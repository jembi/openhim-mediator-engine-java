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
import org.openhim.mediator.engine.MediatorConfig;
import org.openhim.mediator.engine.RegistrationConfig;
import org.openhim.mediator.engine.messages.*;

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
 * <li>RegisterMediatorWithCore: Register the mediator with core - no response</li>
 * <li>MediatorHTTPRequest: Will add the auth headers to the request and forward it to http-connector -
 * responds with MediatorHTTPResponse</li>
 * <li>SendHeartbeatToCore: Send a heartbeat to core and update the dynamic config map
 * with any changes - no response</li>
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


    public CoreAPIConnector(MediatorConfig config) {
        this.config = config;
    }


    private MediatorHTTPRequest buildRegistrationRequest() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", config.getRegistrationConfig().getContentType());

        return new MediatorHTTPRequest(
                getSelf(),
                getSender(),
                "register-mediator",
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

        String path = "/mediators" + config.getRegistrationConfig().getURN() + "/heartbeat";
        String body = "{\"uptime\":" + msg.getUptimeSeconds() + "}";

        return new MediatorHTTPRequest(
                getSelf(),
                getSender(),
                "heartbeat",
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
                "get-auth-details",
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
        MediatorHTTPRequest originalRequest = activeRequests.remove(correlationId);
        MediatorHTTPRequest request = new MediatorHTTPRequest(originalRequest);

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

    @Override
    public void onReceive(Object msg) throws Exception {
        if (msg instanceof RegisterMediatorWithCore) {
            log.info("Registering mediator with core...");
            authenticateMessage(buildRegistrationRequest());

        } else if (msg instanceof MediatorHTTPRequest) {
            authenticateMessage((MediatorHTTPRequest) msg);

        } else if (msg instanceof SendHeartbeatToCore) {
            authenticateMessage(buildHeartbeatRequest((SendHeartbeatToCore) msg));

        } else if (msg instanceof MediatorHTTPResponse) {
            if ("register-mediator".equals(((MediatorHTTPResponse) msg).getOriginalRequest().getOrchestration())) {
                log.info("Sent mediator registration message to core");
                log.info(String.format("Response: %s (%s)", ((MediatorHTTPResponse) msg).getStatusCode(), ((MediatorHTTPResponse) msg).getBody()));

            } else if ("heartbeat".equals(((MediatorHTTPResponse) msg).getOriginalRequest().getOrchestration())) {
                //TODO

            } else if ("get-auth-details".equals(((MediatorHTTPResponse) msg).getOriginalRequest().getOrchestration())) {
                MediatorHTTPRequest originalRequest = copyOriginalRequestWithAuthenticationHeaders((MediatorHTTPResponse) msg);
                if (originalRequest!=null) {
                    sendToHTTPConnector(originalRequest);
                }

            } else {
                //we only expect http responses for the authentication message, but forward to original respondTo just in case
                ((MediatorHTTPResponse) msg).getOriginalRequest().getRespondTo().tell(msg, getSelf());
            }

        } else if (msg instanceof ExceptError) {
            log.error(((ExceptError) msg).getError(), "http-connector: An error occurred while communicating with core");

        } else if (msg instanceof AddOrchestrationToCoreResponse) {
            //do nothing
        }
    }
}
