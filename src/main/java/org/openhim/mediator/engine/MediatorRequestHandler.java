/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.openhim.mediator.engine;

import akka.actor.*;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import org.apache.http.HttpStatus;
import org.openhim.mediator.engine.messages.*;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * <p>The request handler actor launched whenever a request is received.
 * Handles the routing based on the configured RoutingTable and handles the response finalization.</p>
 * <p>
 * Messages supported:
 * <ul>
 * <li>FinishRequestMessage</li>
 * <li>ExceptErrorMessage</li>
 * <li>AcceptedAsyncRequest</li>
 * <li>AddOrchestrationToCoreResponseMessage</li>
 * <li>PutPropertyInCoreResponse</li>
 * </ul>
 * </p>
 *
 * @see RoutingTable
 */
public class MediatorRequestHandler extends UntypedActor {

    public static final String OPENHIM_MIME_TYPE = "application/json+openhim";

    LoggingAdapter log = Logging.getLogger(getContext().system(), this);

    protected ActorRef requestCaller;
    protected CoreResponse response = new CoreResponse();
    protected String coreTransactionID;
    protected boolean async = false;
    //finalizingRequest becomes true as soon as we "respondAndEnd()"
    protected boolean finalizingRequest = false;

    protected final MediatorConfig config;


    public MediatorRequestHandler(MediatorConfig config) {
        this.config = config;

        try {
            if (config.getRegistrationConfig()!=null) {
                response.setUrn(config.getRegistrationConfig().getURN());
            }
        } catch (RegistrationConfig.InvalidRegistrationContentException ex) {
            log.error(ex, "Could not read URN");
            log.warning("'x-mediator-urn' will not be included in the mediator response");
        }
    }



    private void routeToActor(String route, Class<? extends Actor> clazz, MediatorHTTPRequest request) {
        ActorRef actor = null;
        try {
            //can we pass the mediator config through?
            if (clazz.getConstructor(MediatorConfig.class) != null) {
                actor = getContext().actorOf(Props.create(clazz, config));
            }
        } catch (NoSuchMethodException | SecurityException ex) {
            //no matter. use default
            actor = getContext().actorOf(Props.create(clazz));
        }

        actor.tell(request, getSelf());
    }

    private void routeRequest(MediatorHTTPRequest request) {
        log.info("Received request: " + request.getMethod() + " " + request.getPath());

        Class<? extends Actor> routeTo = config.getRoutingTable().getActorClassForPath(request.getPath());
        if (routeTo!=null) {
            routeToActor(request.getPath(), routeTo, request);
        } else {
            CoreResponse.Response resp = new CoreResponse.Response();
            resp.setStatus(HttpStatus.SC_NOT_FOUND);
            resp.setBody(request.getPath() + " not found");
            resp.putHeader("Content-Type", "text/plain");
            response.setResponse(resp);
            respondAndEnd(HttpStatus.SC_NOT_FOUND);
        }
    }

    private void enableAsyncProcessing() {
        if (coreTransactionID==null || coreTransactionID.isEmpty()) {
            exceptError(new RuntimeException("Cannot enable asyncronous processing if X-OpenHIM-TransactionID is unknown"));
            return;
        }

        log.info("Accepted async request. Responding to client.");
        async = true;

        //store existing response
        CoreResponse.Response _resp = response.getResponse();

        //respond with 202
        CoreResponse.Response accepted = new CoreResponse.Response();
        accepted.setStatus(HttpStatus.SC_ACCEPTED);
        accepted.setBody("Accepted request");
        response.setResponse(accepted);

        respondToCaller(HttpStatus.SC_ACCEPTED);

        //restore response
        response.setResponse(_resp);
    }

    private void processFinishRequestMessage(FinishRequest msg) {
        if (response.getResponse()==null) {
            CoreResponse.Response resp = new CoreResponse.Response();
            resp.setBody(msg.getResponse());
            if (msg.getResponseMimeType()!=null) {
                resp.putHeader("Content-Type", msg.getResponseMimeType());
            }
            resp.setStatus(msg.getResponseStatus());
            response.setResponse(resp);
        }
        respondAndEnd(msg.getResponseStatus());
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
        respondAndEnd(HttpStatus.SC_INTERNAL_SERVER_ERROR);
    }

    private void updateTransactionToCoreAPI() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");

        MediatorHTTPRequest request = new MediatorHTTPRequest(
                getSelf(),
                getSelf(),
                "core-api-update-transaction",
                "PUT",
                "https",
                config.getCoreHost(),
                config.getCoreAPIPort(),
                "/transactions/" + coreTransactionID,
                response.toJSON(),
                headers,
                null
        );

        log.info("Sending updated transaction (" + coreTransactionID + ") to core");
        ActorSelection coreConnector = getContext().actorSelection(config.userPathFor("core-api-connector"));
        coreConnector.tell(request, getSelf());
    }

    private void processResponseFromCoreAPI(MediatorHTTPResponse response) {
        try {
            log.info("Received response from core - status " + response.getStatusCode());
            log.info(response.getBody());
        } finally {
            endRequest();
        }
    }

    private void respondAndEnd(Integer status) {
        if (finalizingRequest) {
            return;
        }

        finalizingRequest = true;

        if (async) {
            updateTransactionToCoreAPI();
        } else {
            try {
                respondToCaller(status);
            } finally {
                endRequest();
            }
        }
    }

    private void respondToCaller(Integer status) {
        if (requestCaller != null) {
            if (response.getStatus()==null) {
                response.setStatus(response.getDescriptiveStatus());
            }

            Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            headers.put("Content-Type", OPENHIM_MIME_TYPE);

            MediatorHTTPResponse serverResponse = new MediatorHTTPResponse(null, response.toJSON(), status, headers);
            requestCaller.tell(serverResponse, getSelf());
            requestCaller = null;
        } else {
            log.warning("FinishRequestMessage received but request caller is gone");
        }
    }


    /**
     * To be called when the request handler is all done
     */
    private void endRequest() {
        getContext().stop(getSelf());
    }

    @Override
    public void onReceive(Object msg) throws Exception {
        if (msg instanceof MediatorHTTPRequest) {
            requestCaller = getSender();
            coreTransactionID = ((MediatorHTTPRequest) msg).getHeaders().get("X-OpenHIM-TransactionID");
            routeRequest((MediatorHTTPRequest) msg);
        } else if (msg instanceof AcceptedAsyncRequest) {
            enableAsyncProcessing();
        } else if (msg instanceof FinishRequest) {
            processFinishRequestMessage((FinishRequest) msg);
        } else if (msg instanceof ExceptError) {
            exceptError(((ExceptError) msg).getError());
        } else if (msg instanceof AddOrchestrationToCoreResponse) {
            if (!finalizingRequest) {
                response.addOrchestration(((AddOrchestrationToCoreResponse) msg).getOrchestration());
            }
        } else if (msg instanceof PutPropertyInCoreResponse) {
            if (!finalizingRequest) {
                response.putProperty(((PutPropertyInCoreResponse) msg).getName(), ((PutPropertyInCoreResponse) msg).getValue());
            }
        } else if (msg instanceof MediatorHTTPResponse) {
            processResponseFromCoreAPI((MediatorHTTPResponse) msg);
        } else {
            unhandled(msg);
        }
    }
}
