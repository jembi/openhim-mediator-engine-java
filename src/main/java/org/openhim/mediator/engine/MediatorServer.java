/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.openhim.mediator.engine;

import akka.actor.*;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import org.openhim.mediator.engine.messages.RegisterMediatorWithCore;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * The mediator engine HTTP server.
 */
public class MediatorServer {
    private final LoggingAdapter log;

    private final ActorSystem system;
    private boolean isDefaultActorSystem = false;
    private final ActorRef rootActor;
    private final MediatorConfig config;


    public MediatorServer(ActorSystem system, MediatorConfig config) {
        this.system = system;
        this.rootActor = system.actorOf(Props.create(MediatorRootActor.class, config), config.getName());
        this.config = config;
        log = Logging.getLogger(system, "http-server");
    }

    public MediatorServer(MediatorConfig config) {
        this(ActorSystem.create("mediator"), config);
        isDefaultActorSystem = true;
    }


    private FiniteDuration getRootTimeout() {
        if (config.getRootTimeout()!=null) {
            return Duration.create(config.getRootTimeout(), TimeUnit.MILLISECONDS);
        }
        return Duration.create(1, TimeUnit.MINUTES);
    }

    public void start() throws IOException {
        start(true);
    }

    public void start(boolean registerMediatorWithCore) throws IOException {
        if (registerMediatorWithCore) {
            Inbox inbox = Inbox.create(system);
            inbox.send(rootActor, new RegisterMediatorWithCore());
        }
    }

    public void stop() {

        if (isDefaultActorSystem) {
            system.shutdown();
        }
    }
}
