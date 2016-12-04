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
import org.openhim.mediator.engine.messages.MediatorSocketRequest;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.Callable;

import static akka.dispatch.Futures.future;

/**
 * An actor that provides functionality for connecting to UDP services.
 * <br/><br/>
 * Note that this is one-way connector and that it won't generate any response orchestrations.
 * It also won't send ExceptError messages to the request handler.
 * <br/><br/>
 * Supports the following messages:
 * <ul>
 * <li>{@link MediatorSocketRequest} - fire and forget, no response</li>
 * </ul>
 */
public class UDPFireForgetConnector extends UntypedActor {

    LoggingAdapter log = Logging.getLogger(getContext().system(), this);


    private void sendRequest(final MediatorSocketRequest req) {
        try {
            final DatagramSocket socket = new DatagramSocket();

            ExecutionContext ec = getContext().dispatcher();
            Future<Boolean> f = future(new Callable<Boolean>() {
                public Boolean call() throws IOException {
                    InetAddress addr = InetAddress.getByName(req.getHost());
                    byte[] payload = req.getBody().getBytes();
                    DatagramPacket packet = new DatagramPacket(payload, payload.length, addr, req.getPort());

                    socket.send(packet);

                    return Boolean.TRUE;
                }
            }, ec);
            f.onComplete(new OnComplete<Boolean>() {
                @Override
                public void onComplete(Throwable throwable, Boolean result) throws Throwable {
                    try {
                        if (throwable != null) {
                            throw throwable;
                        }
                    } catch (Exception ex) {
                        log.error(ex, "Exception");
                    } finally {
                        IOUtils.closeQuietly(socket);
                    }
                }
            }, ec);
        } catch (Exception ex) {
            log.error(ex, "Exception");
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
