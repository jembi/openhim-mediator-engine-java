/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.openhim.mediator.engine.connectors;

import akka.actor.UntypedActor;
import akka.dispatch.OnComplete;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import org.apache.commons.io.IOUtils;
import org.openhim.mediator.engine.CoreResponse;
import org.openhim.mediator.engine.messages.AddOrchestrationToCoreResponse;
import org.openhim.mediator.engine.messages.ExceptError;
import org.openhim.mediator.engine.messages.MediatorSocketRequest;
import org.openhim.mediator.engine.messages.MediatorSocketResponse;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.util.concurrent.Callable;

import static akka.dispatch.Futures.future;

/**
 * An actor that provides functionality for connecting to TCP services using the MLLP protocol.
 * <br/><br/>
 * Supports the following messages:
 * <ul>
 * <li>MediatorSocketRequest - responds with MediatorSocketResponse</li>
 * </ul>
 */
public class MLLPConnector extends UntypedActor {
    public static final char MLLP_HEADER_VT = '\013';
    public static final char MLLP_FOOTER_FS = '\034';
    public static final char MLLP_FOOTER_CR = '\r';

    LoggingAdapter log = Logging.getLogger(getContext().system(), this);


    public static String wrapMLLP(String s) {
        return MLLPConnector.MLLP_HEADER_VT + s + MLLPConnector.MLLP_FOOTER_FS + MLLPConnector.MLLP_FOOTER_CR;
    }

    public static boolean isMLLPWrapped(String s) {
        return s!=null && s.length()>=3 &&
                s.charAt(0)==MLLP_HEADER_VT &&
                s.charAt(s.length()-2)==MLLP_FOOTER_FS &&
                s.charAt(s.length()-1)==MLLP_FOOTER_CR;
    }


    private CoreResponse.Orchestration buildOrchestration(MediatorSocketRequest req, MediatorSocketResponse resp) {
        CoreResponse.Orchestration orch = new CoreResponse.Orchestration();
        orch.setName(req.getOrchestration());

        CoreResponse.Request orchReq = new CoreResponse.Request();
        orchReq.setBody(wrapMLLP(req.getBody()));
        orch.setRequest(orchReq);

        CoreResponse.Response orchResp = new CoreResponse.Response();
        orchResp.setBody(wrapMLLP(resp.getBody()));
        orch.setResponse(orchResp);

        return orch;
    }

    private static String readMLLPStream(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(1024*1024);
        int lastByte = -1;
        int lastLastByte;
        do {
            lastLastByte = lastByte;
            lastByte = in.read();
            if (lastByte!=-1) {
                buffer.write(lastByte);
            }
        } while (lastByte!=-1 && lastLastByte!=MLLP_FOOTER_FS && lastByte!=MLLP_FOOTER_CR);

        return buffer.toString();
    }

    private void sendRequest(final MediatorSocketRequest req) {
        try {
            final Socket socket = new Socket(req.getHost(), req.getPort());

            ExecutionContext ec = getContext().dispatcher();
            Future<String> f = future(new Callable<String>() {
                public String call() throws IOException {
                    DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                    out.writeBytes(wrapMLLP(req.getBody()));

                    String result = readMLLPStream(socket.getInputStream());
                    if (isMLLPWrapped(result)) {
                        result = result.substring(1).substring(0, result.length()-3);
                    } else {
                        log.warning("Response from server is not valid MLLP");
                    }

                    return result;
                }
            }, ec);
            f.onComplete(new OnComplete<String>() {
                @Override
                public void onComplete(Throwable throwable, String result) throws Throwable {
                    try {
                        if (throwable != null) {
                            throw throwable;
                        }

                        MediatorSocketResponse response = new MediatorSocketResponse(req, result);
                        req.getRespondTo().tell(response, getSelf());

                        //enrich engine response
                        CoreResponse.Orchestration orch = buildOrchestration(req, response);
                        req.getRequestHandler().tell(new AddOrchestrationToCoreResponse(orch), getSelf());
                    } catch (Exception ex) {
                        req.getRequestHandler().tell(new ExceptError(ex), getSelf());
                    } finally {
                        IOUtils.closeQuietly(socket);
                    }
                }
            }, ec);
        } catch (IOException | UnsupportedOperationException ex) {
            req.getRequestHandler().tell(new ExceptError(ex), getSelf());
        }
    }

    @Override
    public void onReceive(Object msg) throws Exception {
        if (msg instanceof MediatorSocketRequest) {
            sendRequest((MediatorSocketRequest) msg);
        } else {
            unhandled(msg);
        }
    }
}
