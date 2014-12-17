package org.openhim.mediator.engine;

import akka.actor.Actor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import fi.iki.elonen.NanoHTTPD;
import org.apache.http.HttpStatus;
import org.openhim.mediator.engine.messages.AddOrchestrationToCoreResponse;
import org.openhim.mediator.engine.messages.ExceptError;
import org.openhim.mediator.engine.messages.FinishRequest;
import org.openhim.mediator.engine.messages.NanoIHTTPWrapper;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * <p>The request handler actor launched whenever a request is received.
 * Handles the routing based on the configured RoutingTable and handles the response finalization.</p>
 * <p>
 * Messages supported:
 * <ul>
 * <li>FinishRequestMessage</li>
 * <li>ExceptErrorMessage</li>
 * <li>AddOrchestrationToCoreResponseMessage</li>
 * </ul>
 * </p>
 *
 * @see RoutingTable
 */
public class MediatorRequestActor extends UntypedActor {

    public static final String OPENHIM_MIME_TYPE = "application/json+openhim";

    LoggingAdapter log = Logging.getLogger(getContext().system(), this);

    private ActorRef requestCaller;
    private CoreResponse response = new CoreResponse();
    private final RoutingTable routingTable;


    public MediatorRequestActor(RoutingTable routingTable) {
        this.routingTable = routingTable;
    }


    private void loadRequestBody(NanoHTTPD.IHTTPSession session) {
        if ("POST".equals(session.getMethod().toString()) || "PUT".equals(session.getMethod().toString())) {
            try {
                Map<String, String> files = new HashMap<String, String>();
                session.parseBody(files);
            } catch (NanoHTTPD.ResponseException | IOException e) {
                exceptError(e);
            }
        }
    }

    private void routeToActor(String route, Class<? extends Actor> clazz, NanoHTTPD.IHTTPSession session) {
        loadRequestBody(session);
        ActorRef actor = getContext().actorOf(Props.create(clazz));
        actor.tell(new NanoIHTTPWrapper(getSelf(), getSelf(), route, session), getSelf());
    }

    private void routeRequest(NanoHTTPD.IHTTPSession session) {
        log.info("Received request: " + session.getMethod() + " " + session.getUri());

        Class<? extends Actor> routeTo = routingTable.getActorClassForRoute(session.getUri());
        if (routeTo!=null) {
            routeToActor(session.getUri(), routeTo, session);
        } else {
            CoreResponse.Response resp = new CoreResponse.Response();
            resp.setStatus(HttpStatus.SC_NOT_FOUND);
            resp.setBody(session.getUri() + " not found");
            resp.putHeader("Content-Type", "text/plain");
            response.setResponse(resp);
            respondToCaller(HttpStatus.SC_NOT_FOUND);
        }
    }

    private void processFinishRequestMessage(FinishRequest msg) {
        if (response.getResponse()==null) {
            CoreResponse.Response resp = new CoreResponse.Response();
            resp.setBody(msg.getResponse());
            resp.putHeader("Content-Type", msg.getResponseMimeType());
            resp.setStatus(msg.getResponseStatus());
            response.setResponse(resp);
        }
        respondToCaller(msg.getResponseStatus());
    }

    private void exceptError(Throwable t) {
        log.error(t, "Exception while processing request");
        if (response.getResponse()==null) {
            CoreResponse.Response resp = new CoreResponse.Response();
            resp.setBody(t.getMessage());
            resp.putHeader("Content-Type", "text/plain");
            resp.setStatus(HttpStatus.SC_INTERNAL_SERVER_ERROR);
            response.setResponse(resp);
        }
        respondToCaller(HttpStatus.SC_INTERNAL_SERVER_ERROR);
    }

    private void respondToCaller(Integer status) {
        if (requestCaller != null) {
            if (response.getStatus()==null) {
                response.setStatus(response.getDescriptiveStatus());
            }

            NanoHTTPD.Response.IStatus nanoStatus = getIntAsNanoHTTPDStatus(status);
            NanoHTTPD.Response serverResponse = new NanoHTTPD.Response(nanoStatus, OPENHIM_MIME_TYPE, response.toJSON());
            requestCaller.tell(serverResponse, getSelf());
            requestCaller = null;
        } else {
            log.warning("FinishRequestMessage received but request caller is gone");
        }
    }

    private NanoHTTPD.Response.IStatus getIntAsNanoHTTPDStatus(final Integer httpStatus) {
        if (httpStatus==null) {
            //200 by default
            return NanoHTTPD.Response.Status.OK;
        }
        return new NanoHTTPD.Response.IStatus() {
            @Override
            public int getRequestStatus() {
                return httpStatus;
            }

            @Override
            public String getDescription() {
                return Integer.toString(getRequestStatus());
            }
        };
    }

    @Override
    public void onReceive(Object msg) throws Exception {
        if (msg instanceof NanoHTTPD.IHTTPSession) {
            requestCaller = getSender();
            routeRequest((NanoHTTPD.IHTTPSession) msg);
        } else if (msg instanceof FinishRequest) {
            processFinishRequestMessage((FinishRequest) msg);
        } else if (msg instanceof ExceptError) {
            exceptError(((ExceptError) msg).getError());
        } else if (msg instanceof AddOrchestrationToCoreResponse) {
            response.addOrchestration(((AddOrchestrationToCoreResponse)msg).getOrchestration());
        } else {
            unhandled(msg);
        }
    }
}
