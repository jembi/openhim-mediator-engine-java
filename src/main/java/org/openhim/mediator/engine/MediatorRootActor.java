/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.openhim.mediator.engine;

import akka.actor.ActorRef;
import akka.actor.Inbox;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.dispatch.OnComplete;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.glassfish.grizzly.ReadHandler;
import org.glassfish.grizzly.http.io.NIOReader;
import org.glassfish.grizzly.http.server.Response;
import org.openhim.mediator.engine.connectors.CoreAPIConnector;
import org.openhim.mediator.engine.connectors.HTTPConnector;
import org.openhim.mediator.engine.connectors.MLLPConnector;
import org.openhim.mediator.engine.connectors.UDPFireForgetConnector;
import org.openhim.mediator.engine.messages.GrizzlyHTTPRequest;
import org.openhim.mediator.engine.messages.MediatorHTTPRequest;
import org.openhim.mediator.engine.messages.MediatorHTTPResponse;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import static akka.dispatch.Futures.future;

/**
 * The root actor for the mediator.
 * <br/><br/>
 * Its roles are to:
 * <ul>
 * <li>launch new request actors,</li>
 * <li>contain the request context, and</li>
 * <li>launch all single instance actors on startup.</li>
 * </ul>
 */
public class MediatorRootActor extends UntypedActor {

    LoggingAdapter log = Logging.getLogger(getContext().system(), this);

    private final MediatorConfig config;


    public MediatorRootActor(MediatorConfig config) {
        if (config.getRoutingTable()==null) {
            throw new NullPointerException("Routing table is required");
        }
        this.config = config;

        if (config.getStartupActors()!=null && config.getStartupActors().getActors().size()>0) {
            for (StartupActorsConfig.ActorToLaunch actor : config.getStartupActors().getActors()) {
                try {
                    //can we pass the mediator config through?
                    if (actor.getActorClass().getConstructor(MediatorConfig.class) != null) {
                        getContext().actorOf(Props.create(actor.getActorClass(), config), actor.getName());
                    }
                } catch (NoSuchMethodException | SecurityException ex) {
                    //no matter. use default
                    getContext().actorOf(Props.create(actor.getActorClass()), actor.getName());
                }
            }
        }

        getContext().actorOf(Props.create(HTTPConnector.class), "http-connector");
        getContext().actorOf(Props.create(CoreAPIConnector.class, config), "core-api-connector");
        getContext().actorOf(Props.create(MLLPConnector.class), "mllp-connector");
        getContext().actorOf(Props.create(UDPFireForgetConnector.class), "udp-fire-forget-connector");

        getContext().actorOf(Props.create(HeartbeatActor.class, config), "heartbeat");
    }

    private void containRequest(final GrizzlyHTTPRequest request, final ActorRef requestHandler) {
        ExecutionContext ec = getContext().dispatcher();

        Future<Object> f = future(new Callable<Object>() {
            public Object call() throws IOException {
                Inbox inbox = Inbox.create(getContext().system());
                processGrizzlyRequest(inbox, requestHandler, request);
                return inbox.receive(getRootTimeout());
            }
        }, ec);

        f.onComplete(new OnComplete<Object>() {
            @Override
            public void onComplete(Throwable throwable, Object result) throws Throwable {
                try {
                    if (throwable != null) {
                        log.error(throwable, "Request containment exception");
                        handleResponse(request.getResponseHandle(), 500, "text/plain", throwable.getMessage());
                    } else if (result == null || !(result instanceof MediatorHTTPResponse)) {
                        String err = "Request handler responded with unexpected result: " + result;
                        log.warning(err);
                        handleResponse(request.getResponseHandle(), 500, "text/plain", err);
                    } else {
                        MediatorHTTPResponse mediatorHTTPResponse = (MediatorHTTPResponse) result;
                        handleResponse(request.getResponseHandle(), mediatorHTTPResponse);
                    }
                } finally {
                    //trigger response to client
                    request.getResponseHandle().resume();
                }
            }
        }, ec);
    }


    private void processGrizzlyRequest(final Inbox handlerInbox, final ActorRef requestHandler, final GrizzlyHTTPRequest request) throws IOException {
        final NIOReader in = request.getRequest().getNIOReader();

        final Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (String hdr : request.getRequest().getHeaderNames()) {
            headers.put(hdr, request.getRequest().getHeader(hdr));
        }

        final List<Pair<String, String>> params = new ArrayList<>();
        for (String param : request.getRequest().getParameterNames()) {
            for (String value : request.getRequest().getParameterValues(param)) {
                params.add(Pair.of(param, value));
            }
        }

        in.notifyAvailable(new ReadHandler() {
            final StringWriter bodyBuffer = new StringWriter();
            char[] readBuffer = new char[1024];

            private void read() throws IOException {
                while (in.isReady()) {
                    int len = in.read(readBuffer);
                    if (len > 0) {
                        bodyBuffer.write(readBuffer, 0, len);
                    }
                }
            }

            @Override
            public void onDataAvailable() throws Exception {
                read();
                in.notifyAvailable(this);
            }

            @Override
            public void onError(Throwable throwable) {
                try {
                    log.error(throwable, "Error during reading of request body");
                    handleResponse(request.getResponseHandle(), 500, "text/plain", throwable.getMessage());
                } catch (IOException ex) {
                    log.error(ex, "Error during reading of request body");
                } finally {
                    request.getResponseHandle().resume();
                }
            }

            @Override
            public void onAllDataRead() throws Exception {
                try {
                    read();
                    MediatorHTTPRequest mediatorHTTPRequest = buildMediatorHTTPRequest(requestHandler, request, bodyBuffer.toString(), headers, params);
                    handlerInbox.send(requestHandler, mediatorHTTPRequest);
                } finally {
                    IOUtils.closeQuietly(in);
                }
            }
        });
    }

    private MediatorHTTPRequest buildMediatorHTTPRequest(ActorRef requestHandler, GrizzlyHTTPRequest request,
                                                         String body, Map<String, String> headers, List<Pair<String, String>> params) {
        return new MediatorHTTPRequest(
                requestHandler,
                requestHandler,
                null,
                request.getRequest().getMethod().toString(),
                request.getRequest().getScheme(),
                request.getRequest().getLocalAddr(),
                request.getRequest().getLocalPort(),
                request.getRequest().getRequestURI(),
                body,
                headers,
                params
        );
    }

    private void handleResponse(Response grizzlyResponseHandle, MediatorHTTPResponse response) throws IOException {
        handleResponse(grizzlyResponseHandle, response.getStatusCode(), response.getHeaders().get("Content-Type"), response.getBody());
    }

    private void handleResponse(Response grizzlyResponseHandle, Integer status, String contentType, String body) throws IOException {
        grizzlyResponseHandle.setStatus(status);
        if (contentType!=null && body!=null) {
            grizzlyResponseHandle.setContentType(contentType);
            grizzlyResponseHandle.setContentLength(body.getBytes().length);
            grizzlyResponseHandle.setCharacterEncoding("UTF-8");
            grizzlyResponseHandle.getWriter().write(body);
        }
    }

    private FiniteDuration getRootTimeout() {
        if (config.getRootTimeout()!=null) {
            return Duration.create(config.getRootTimeout(), TimeUnit.MILLISECONDS);
        }
        return Duration.create(1, TimeUnit.MINUTES);
    }

    @Override
    public void onReceive(Object msg) throws Exception {
        if (msg instanceof GrizzlyHTTPRequest) {
            ActorRef requestHandler = getContext().actorOf(Props.create(MediatorRequestHandler.class, config));
            containRequest((GrizzlyHTTPRequest) msg, requestHandler);

        } else {
            unhandled(msg);
        }
    }
}
