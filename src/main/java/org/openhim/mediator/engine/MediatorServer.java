/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.openhim.mediator.engine;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import org.glassfish.grizzly.http.server.*;
import org.openhim.mediator.engine.messages.GrizzlyHTTPRequest;
import org.openhim.mediator.engine.messages.RegisterMediatorWithCore;
import org.openhim.mediator.engine.messages.SendHeartbeatToCore;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * The mediator engine HTTP server.
 * <br/><br/>
 * Its roles are to:
 * <ul>
 * <li>provide the http server for the mediator,</li>
 * <li>launch default actor system if none are provided,</li>
 * <li>trigger the registration of the mediator to core, and</li>
 * <li>periodically trigger heartbeat requests to core.</li>
 * </ul>
 */
public class MediatorServer {
    private final LoggingAdapter log;

    private final ActorSystem system;
    private boolean isDefaultActorSystem = false;
    private final ActorRef rootActor;
    private final MediatorConfig config;
    private final HttpServer httpServer;

    private ScheduledExecutorService heartbeatService;
    private static final int initialHeartbeatDelaySeconds = 5;
    private long serverStartTime;


    public MediatorServer(ActorSystem system, MediatorConfig config) {
        this.system = system;
        this.rootActor = system.actorOf(Props.create(MediatorRootActor.class, config), config.getName());
        this.config = config;
        log = Logging.getLogger(system, "http-server");

        httpServer = new HttpServer();
        configureHttpServer();
    }

    public MediatorServer(MediatorConfig config) {
        this(ActorSystem.create("mediator"), config);
        isDefaultActorSystem = true;
    }

    private void configureHttpServer() {
        NetworkListener listener = new NetworkListener(config.getName(), config.getServerHost(), config.getServerPort());
        httpServer.addListener(listener);

        httpServer.getServerConfiguration().addHttpHandler(new HttpHandler() {
            @Override
            public void service(Request request, Response response) throws Exception {
                response.suspend();
                rootActor.tell(new GrizzlyHTTPRequest(request, response), ActorRef.noSender());
            }
        });
    }

    private void startHeartbeatService() {
        if (heartbeatService==null) {
            heartbeatService = Executors.newSingleThreadScheduledExecutor();
        }

        heartbeatService.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                long uptime = (System.currentTimeMillis()-serverStartTime)/1000;
                rootActor.tell(new SendHeartbeatToCore(uptime), ActorRef.noSender());
            }
        }, initialHeartbeatDelaySeconds, config.getHeartbeatPeriodSeconds(), TimeUnit.SECONDS);
    }

    private void stopHeartbeatService() {
        if (heartbeatService!=null) {
            heartbeatService.shutdownNow();
        }
    }

    public void start() throws IOException {
        start(true);
    }

    public void start(boolean registerMediatorWithCore) throws IOException {
        serverStartTime = System.currentTimeMillis();
        httpServer.start();

        if (registerMediatorWithCore) {
            rootActor.tell(new RegisterMediatorWithCore(), ActorRef.noSender());
        }

        if (config.getHeartsbeatEnabled()) {
            startHeartbeatService();
        }
    }

    public void stop() {
        if (config.getHeartsbeatEnabled()) {
            stopHeartbeatService();
        }
        httpServer.shutdownNow();

        if (isDefaultActorSystem) {
            system.shutdown();
        }
    }
}
