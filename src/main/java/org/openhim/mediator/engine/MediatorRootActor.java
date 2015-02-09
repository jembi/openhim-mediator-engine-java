/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.openhim.mediator.engine;

import static akka.dispatch.Futures.future;

import akka.actor.*;
import akka.dispatch.OnComplete;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import org.apache.commons.io.IOUtils;
import org.glassfish.grizzly.http.server.Response;
import org.openhim.mediator.engine.connectors.CoreAPIConnector;
import org.openhim.mediator.engine.connectors.HTTPConnector;
import org.openhim.mediator.engine.connectors.MLLPConnector;
import org.openhim.mediator.engine.connectors.UDPFireForgetConnector;
import org.openhim.mediator.engine.messages.*;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * The root actor for the mediator.
 * <br/><br/>
 * Its roles are to:
 * <ul>
 * <li>launch new request actors,</li>
 * <li>contain the request context,</li>
 * <li>launch all single instance actors on startup, and</li>
 * <li>trigger the registration of the mediator to core.</li>
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
    }

    private void containRequest(final GrizzlyHTTPRequest request, final ActorRef requestHandler) {
        ExecutionContext ec = getContext().dispatcher();

        Future<Object> f = future(new Callable<Object>() {
            public Object call() throws IOException {
                MediatorHTTPRequest mediatorHTTPRequest = buildMediatorHTTPRequestFromGrizzlyRequest(requestHandler, request);
                Inbox inbox = Inbox.create(getContext().system());
                inbox.send(requestHandler, mediatorHTTPRequest);
                return inbox.receive(getRootTimeout());
            }
        }, ec);

        f.onComplete(new OnComplete<Object>() {
            @Override
            public void onComplete(Throwable throwable, Object result) throws Throwable {
                try {
                    if (throwable != null) {
                        log.error(throwable, "Request containment exception");
                    }
                    if (result == null || !(result instanceof MediatorHTTPResponse)) {
                        log.warning("Request handler responded with unexpected result: " + result);
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


    private MediatorHTTPRequest buildMediatorHTTPRequestFromGrizzlyRequest(ActorRef requestHandler, GrizzlyHTTPRequest request) throws IOException {
        String body = IOUtils.toString(request.getRequest().getNIOReader());

        Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (String hdr : request.getRequest().getHeaderNames()) {
            headers.put(hdr, request.getRequest().getHeader(hdr));
        }

        Map<String, String> params = new HashMap<>();
        for (String param : request.getRequest().getParameterNames()) {
            params.put(param, request.getRequest().getParameter(param));
        }

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

    private void handleResponse(Response grizzlyResponseHandle, MediatorHTTPResponse mediatorHTTPResponse) throws IOException {
        grizzlyResponseHandle.setContentType(mediatorHTTPResponse.getHeaders().get("Content-Type"));
        grizzlyResponseHandle.setStatus(mediatorHTTPResponse.getStatusCode());
        if (mediatorHTTPResponse.getBody()!=null) {
            grizzlyResponseHandle.setContentLength(mediatorHTTPResponse.getBody().length());
            grizzlyResponseHandle.setCharacterEncoding("UTF-8");
            grizzlyResponseHandle.getWriter().write(mediatorHTTPResponse.getBody());
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
        } else if (config.getRegistrationConfig()!=null && msg instanceof RegisterMediatorWithCore) {
            log.info("Registering mediator with core...");
            ActorSelection coreConnector = getContext().actorSelection(config.userPathFor("core-api-connector"));
            coreConnector.tell(msg, getSelf());
        } else if (msg instanceof MediatorHTTPResponse) {
            log.info("Sent mediator registration message to core");
            log.info(String.format("Response: %s (%s)", ((MediatorHTTPResponse) msg).getStatusCode(), ((MediatorHTTPResponse) msg).getBody()));
        } else {
            unhandled(msg);
        }
    }
}
