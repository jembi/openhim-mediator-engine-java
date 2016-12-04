/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.openhim.mediator.engine;

import akka.actor.ActorSelection;
import akka.actor.Cancellable;
import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import org.openhim.mediator.engine.messages.RegisterMediatorWithCore;
import org.openhim.mediator.engine.messages.RegisterMediatorWithCoreResponse;
import org.openhim.mediator.engine.messages.SendHeartbeatToCore;
import org.openhim.mediator.engine.messages.SendHeartbeatToCoreResponse;
import scala.concurrent.duration.Duration;

import java.util.concurrent.TimeUnit;

/**
 * An actor for sending heartbeats to core.
 * <br/><br/>
 * Its roles are to:
 * <ul>
 * <li>Send the mediator registration request,</li>
 * <li>send heartbeat requests, and</li>
 * <li>update the dynamic config on heartbeat responses from core.</li>
 * </ul>
 */
public class HeartbeatActor extends UntypedActor {

    public static class Start {
        boolean sendRegistration;

        public Start(boolean sendRegistration) {
            this.sendRegistration = sendRegistration;
        }
    }

    public static class Stop {}

    private static class Trigger {}

    LoggingAdapter log = Logging.getLogger(getContext().system(), this);

    private final MediatorConfig config;

    private boolean firstFailure = true;
    private boolean heartbeatError = false;
    private Cancellable task;
    private long serverStartTime;
    private boolean forceConfig;


    public HeartbeatActor(MediatorConfig config) {
        this.config = config;
    }


    private void start(Start startMessage) {
        serverStartTime = System.currentTimeMillis();
        firstFailure = true;

        if (startMessage.sendRegistration) {
            if (config.getRegistrationConfig() == null) {
                log.warning("No mediator registration config found. Cannot proceed with registration.");
            } else {
                log.info("Registering mediator with core...");
                ActorSelection coreConnector = getContext().actorSelection(config.userPathFor("core-api-connector"));
                coreConnector.tell(new RegisterMediatorWithCore(getSelf()), getSelf());
            }
        } else {
            if (config.getHeartsbeatEnabled()) {
                startHeartbeatTask();
            }
        }
    }

    private void handleRegisterResponse(RegisterMediatorWithCoreResponse response) {
        if (response.isSuccessful()) {
            log.info("Successfully registered with core");
            if (config.getHeartsbeatEnabled()) {
                startHeartbeatTask();
            }

        } else {
            if (firstFailure) {
                log.error("Failed to connect to core. Check that it is running and accessible and that your config is correct.");
                if (response.getResponseStatus() != null) {
                    log.error("Response: [" + response.getResponseStatus() + "] " + response.getResponseBody());
                } else {
                    log.error("Response: " + response.getResponseBody());
                }
                firstFailure = false;
            }
            task = getContext().system().scheduler().scheduleOnce(
                    Duration.create(config.getHeartbeatPeriodSeconds(), TimeUnit.SECONDS),
                    new Runnable() {
                        @Override
                        public void run() {
                            ActorSelection coreConnector = getContext().actorSelection(config.userPathFor("core-api-connector"));
                            coreConnector.tell(new RegisterMediatorWithCore(getSelf()), getSelf());
                        }
                    },
                    getContext().system().dispatcher()
            );
            }
    }

    private void startHeartbeatTask() {
        firstFailure = true;
        forceConfig = true;

        task = getContext().system().scheduler().schedule(
                Duration.Zero(),
                Duration.create(config.getHeartbeatPeriodSeconds(), TimeUnit.SECONDS),
                getSelf(),
                new Trigger(),
                getContext().system().dispatcher(),
                null
        );
    }

    private void trigger() {
        long uptime = (System.currentTimeMillis()-serverStartTime)/1000;
        ActorSelection coreConnector = getContext().actorSelection(config.userPathFor("core-api-connector"));
        coreConnector.tell(new SendHeartbeatToCore(uptime, forceConfig), getSelf());
    }

    private void handleHeartbeatResponse(SendHeartbeatToCoreResponse response) {
        if (response.requestSucceeded()) {
            firstFailure = true;
            forceConfig = false;

            if (heartbeatError) {
                log.info("Recovered");
                heartbeatError = false;
            }

            if (response.receivedConfigUpdate()) {
                log.info("Received config updates from core");

                for (String param : response.getConfig().keySet()) {
                    config.getDynamicConfig().put(param, response.getConfig().get(param));
                }
            }

        } else {
            heartbeatError = true;
            if (firstFailure) {
                log.error("Request to core failed: " + response.getRawResponse());
                firstFailure = false;
            }
        }
    }

    private void stop() {
        if (task!=null) {
            task.cancel();
        }
    }


    @Override
    public void onReceive(Object msg) throws Exception {

        if (msg instanceof Start) {
            start((Start) msg);

        } else if (msg instanceof Stop) {
            stop();

        } else if (msg instanceof RegisterMediatorWithCoreResponse) {
            handleRegisterResponse((RegisterMediatorWithCoreResponse) msg);

        } else if (msg instanceof SendHeartbeatToCoreResponse) {
            handleHeartbeatResponse((SendHeartbeatToCoreResponse) msg);

        } else if (msg instanceof Trigger) {
            trigger();

        } else {
            unhandled(msg);
        }
    }
}