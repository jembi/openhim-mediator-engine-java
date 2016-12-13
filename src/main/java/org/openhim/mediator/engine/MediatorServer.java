/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.openhim.mediator.engine;

import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.NetworkListener;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;
import org.openhim.mediator.engine.messages.GrizzlyHTTPRequest;

import java.io.IOException;

/**
 * The mediator engine HTTP server.
 * <br/><br/>
 * Its roles are to:
 * <ul>
 * <li>provide the http server for the mediator,</li>
 * <li>launch default actor system if none are provided, and</li>
 * <li>start/stop the heartbeat service.</li>
 * </ul>
 */
public class MediatorServer {
    private final LoggingAdapter log;

    private final ActorSystem system;
    private boolean isDefaultActorSystem = false;
    private final ActorRef rootActor;
    private final MediatorConfig config;
    private final HttpServer httpServer;


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


    public void start() throws IOException {
        start(true);
    }

    public void start(boolean registerMediatorWithCore) throws IOException {
        httpServer.start();

        ActorSelection heartbeat = system.actorSelection(config.userPathFor("heartbeat"));
        heartbeat.tell(new HeartbeatActor.Start(registerMediatorWithCore), ActorRef.noSender());
    }

    public void stop() {
        ActorSelection heartbeat = system.actorSelection(config.userPathFor("heartbeat"));
        heartbeat.tell(new HeartbeatActor.Stop(), ActorRef.noSender());

        httpServer.shutdownNow();

        if (isDefaultActorSystem) {
            system.shutdown();
        }
    }
}
