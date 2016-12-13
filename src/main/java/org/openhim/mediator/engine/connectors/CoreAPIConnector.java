/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.openhim.mediator.engine.connectors;

import akka.actor.ActorSelection;
import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import org.openhim.mediator.engine.MediatorConfig;
import org.openhim.mediator.engine.RegistrationConfig;
import org.openhim.mediator.engine.messages.AddOrchestrationToCoreResponse;
import org.openhim.mediator.engine.messages.ExceptError;
import org.openhim.mediator.engine.messages.MediatorHTTPRequest;
import org.openhim.mediator.engine.messages.MediatorHTTPResponse;
import org.openhim.mediator.engine.messages.RegisterMediatorWithCore;
import org.openhim.mediator.engine.messages.RegisterMediatorWithCoreResponse;
import org.openhim.mediator.engine.messages.SendHeartbeatToCore;
import org.openhim.mediator.engine.messages.SendHeartbeatToCoreResponse;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * An actor that provides functionality for connecting to the OpenHIM Core API.
 * <br/><br/>
 * Supports the following messages:
 * <ul>
 * <li>{@link RegisterMediatorWithCore}: Register the mediator with core -
 * responds with {@link RegisterMediatorWithCoreResponse}</li>
 * <li>{@link MediatorHTTPRequest}: Will add the auth headers to the request and forward it to http-connector -
 * responds with {@link MediatorHTTPResponse}</li>
 * <li>{@link SendHeartbeatToCore}: Send a heartbeat to core and update the dynamic config map with any changes -
 * responds with {@link SendHeartbeatToCoreResponse}</li>
 * </ul>
 */
public class CoreAPIConnector extends UntypedActor {

    private static class AuthResponse {
        String salt;
        String ts;

        public void setSalt(String salt) {
            this.salt = salt;
        }
        public void setTs(String ts) {
            this.ts = ts;
        }
    }

    public static class CoreGetAuthenticationDetailsError extends Exception {
        public CoreGetAuthenticationDetailsError(String msg) {
            super(msg);
        }
    }

    LoggingAdapter log = Logging.getLogger(getContext().system(), this);

    private final MediatorConfig config;
    private final Map<String, MediatorHTTPRequest> activeRequests = new HashMap<>();

    private static final String REGISTER_MEDIATOR = "register-mediator";
    private static final String HEARTBEAT = "heartbeat";
    private static final String GET_AUTH_DETAILS = "get-auth-details";


    public CoreAPIConnector(MediatorConfig config) {
        this.config = config;
    }


    private MediatorHTTPRequest buildRegistrationRequest() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", config.getRegistrationConfig().getContentType());

        return new MediatorHTTPRequest(
                getSelf(),
                getSender(),
                REGISTER_MEDIATOR,
                config.getRegistrationConfig().getMethod(),
                config.getCoreAPIScheme(),
                config.getCoreHost(),
                config.getCoreAPIPort(),
                config.getRegistrationConfig().getPath(),
                config.getRegistrationConfig().getContent(),
                headers,
                null,
                null
        );
    }

    private MediatorHTTPRequest buildHeartbeatRequest(SendHeartbeatToCore msg) throws RegistrationConfig.InvalidRegistrationContentException {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");

        String path = "/mediators/" + config.getRegistrationConfig().getURN() + "/heartbeat";
        String body = "{";
        body += "\"uptime\":" + msg.getUptimeSeconds();
        if (msg.getForceConfig()) {
            body += ",\"config\":true";
        }
        body += "}";

        return new MediatorHTTPRequest(
                getSelf(),
                getSender(),
                HEARTBEAT,
                "POST",
                config.getCoreAPIScheme(),
                config.getCoreHost(),
                config.getCoreAPIPort(),
                path,
                body,
                headers,
                null
        );
    }

    private MediatorHTTPRequest buildAuthMessage(String correlationId) {
        return new MediatorHTTPRequest(
                getSelf(),
                getSelf(),
                GET_AUTH_DETAILS,
                "GET",
                config.getCoreAPIScheme(),
                config.getCoreHost(),
                config.getCoreAPIPort(),
                "/authenticate/" + config.getCoreAPIUsername(),
                null,
                null,
                null,
                correlationId
        );
    }

    protected static String hash(String s) throws NoSuchAlgorithmException {
        //thanks to http://www.mkyong.com/java/java-sha-hashing-example/

        MessageDigest md = MessageDigest.getInstance("SHA-512");
        md.update(s.getBytes());

        byte[] byteData = md.digest();
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < byteData.length; i++) {
            sb.append(Integer.toString((byteData[i] & 0xff) + 0x100, 16).substring(1));
        }

        return sb.toString();
    }

    private MediatorHTTPRequest copyOriginalRequestWithAuthenticationHeaders(MediatorHTTPResponse response) {
        String correlationId = response.getOriginalRequest().getCorrelationId();
        MediatorHTTPRequest originalRequest = activeRequests.get(correlationId);
        MediatorHTTPRequest request;

        //if register-mediator or heartbeat, core-api-connector is the respondTo. Else forward to original caller
        if (REGISTER_MEDIATOR.equals(originalRequest.getOrchestration()) || HEARTBEAT.equals(originalRequest.getOrchestration())) {
            request = new MediatorHTTPRequest(getSelf(), correlationId, originalRequest);
        } else {
            request = new MediatorHTTPRequest(originalRequest);
            activeRequests.remove(correlationId);
        }

        try {
            if (response.getStatusCode()!=200) {
                String msg = String.format("Core responded with %s (%s)", response.getStatusCode(), response.getBody());
                throw new CoreGetAuthenticationDetailsError(msg);
            }

            Gson gson = new GsonBuilder().create();
            AuthResponse authResponse = gson.fromJson(response.getBody(), AuthResponse.class);

            String passHash = hash(authResponse.salt + config.getCoreAPIPassword());
            String token = hash(passHash + authResponse.salt + authResponse.ts);

            request.getHeaders().put("auth-username", config.getCoreAPIUsername());
            request.getHeaders().put("auth-ts", authResponse.ts);
            request.getHeaders().put("auth-salt", authResponse.salt);
            request.getHeaders().put("auth-token", token);

            return request;
        } catch (NoSuchAlgorithmException | CoreGetAuthenticationDetailsError e) {
            originalRequest.getRequestHandler().tell(new ExceptError(e), getSelf());
        }
        return null;
    }

    private void authenticateMessage(MediatorHTTPRequest request) {
        String correlationId = UUID.randomUUID().toString();
        activeRequests.put(correlationId, request);
        sendToHTTPConnector(buildAuthMessage(correlationId));
    }

    private void sendToHTTPConnector(MediatorHTTPRequest request) {
        ActorSelection httpConnector = getContext().actorSelection(config.userPathFor("http-connector"));
        httpConnector.tell(request, getSelf());
    }

    private void handleRegisterMediatorResponse(MediatorHTTPResponse msg) {
        boolean success = true;

        if (msg.getStatusCode() != 201) {
            success = false;
        }

        MediatorHTTPRequest originalHttp = activeRequests.remove(msg.getOriginalRequest().getCorrelationId());
        originalHttp.getRespondTo().tell(new RegisterMediatorWithCoreResponse(success, msg.getStatusCode(), msg.getBody()), getSelf());
    }

    private void handleAuthenticationResponse(MediatorHTTPResponse msg) {
        MediatorHTTPRequest originalRequest = copyOriginalRequestWithAuthenticationHeaders(msg);
        if (originalRequest != null) {
            sendToHTTPConnector(originalRequest);
        }
    }

    private void handleHeartbeatResponse(MediatorHTTPResponse msg) {
        SendHeartbeatToCoreResponse resp;

        if (msg.getStatusCode()==200) {
            if (msg.getBody().trim().isEmpty()) {
                resp = new SendHeartbeatToCoreResponse(msg.getBody());
            } else {
                try {
                    Map<String, Object> config = new GsonBuilder().create().fromJson(msg.getBody(), Map.class);
                    resp = new SendHeartbeatToCoreResponse(msg.getBody(), config);
                } catch (JsonParseException ex) {
                    log.error("Invalid JSON config received from the OpenHIM core");
                    resp = new SendHeartbeatToCoreResponse(false, msg.getBody(), null);
                }
            }
        } else {
            resp = new SendHeartbeatToCoreResponse(false, msg.getBody(), null);
        }

        MediatorHTTPRequest originalHttp = activeRequests.remove(msg.getOriginalRequest().getCorrelationId());
        originalHttp.getRespondTo().tell(resp, getSelf());
    }

    private void handleHTTPConnectorResponse(MediatorHTTPResponse msg) {
        if (REGISTER_MEDIATOR.equals(msg.getOriginalRequest().getOrchestration())) {
            handleRegisterMediatorResponse(msg);

        } else if (GET_AUTH_DETAILS.equals(msg.getOriginalRequest().getOrchestration())) {
            handleAuthenticationResponse(msg);

        } else if (HEARTBEAT.equals(msg.getOriginalRequest().getOrchestration())) {
            handleHeartbeatResponse(msg);

        } else {
            msg.getOriginalRequest().getRespondTo().tell(msg, getSelf());
        }
    }

    private void handleExceptError(ExceptError error) {
        if (error.getOriginalRequest() instanceof MediatorHTTPRequest) {
            MediatorHTTPRequest original = ((MediatorHTTPRequest) error.getOriginalRequest());
            if (GET_AUTH_DETAILS.equals(original.getOrchestration())) {
                original = activeRequests.remove(original.getCorrelationId());
            }

            if (REGISTER_MEDIATOR.equals(original.getOrchestration())) {
                original.getRespondTo().tell(new RegisterMediatorWithCoreResponse(false, null, error.getError().getMessage()), getSelf());

            } else if (HEARTBEAT.equals(original.getOrchestration())) {
                original.getRespondTo().tell(new SendHeartbeatToCoreResponse(false, error.getError().getMessage(), null), getSelf());

            } else {
                log.error(error.getError(), "http-connector: An error occurred while communicating with core");
            }
        } else {
            log.error(error.getError(), "http-connector: An error occurred while communicating with core");
        }
    }

    @Override
    public void onReceive(Object msg) throws Exception {
        if (msg instanceof RegisterMediatorWithCore) {
            authenticateMessage(buildRegistrationRequest());

        } else if (msg instanceof MediatorHTTPRequest) {
            authenticateMessage((MediatorHTTPRequest) msg);

        } else if (msg instanceof SendHeartbeatToCore) {
            authenticateMessage(buildHeartbeatRequest((SendHeartbeatToCore) msg));

        } else if (msg instanceof MediatorHTTPResponse) {
            handleHTTPConnectorResponse((MediatorHTTPResponse) msg);

        } else if (msg instanceof ExceptError) {
            handleExceptError((ExceptError) msg);

        } else if (msg instanceof AddOrchestrationToCoreResponse) {
            //do nothing
        }
    }
}
