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
import org.openhim.mediator.engine.connectors.CoreAPIConnector;
import org.openhim.mediator.engine.connectors.HTTPConnector;
import org.openhim.mediator.engine.connectors.MLLPConnector;
import org.openhim.mediator.engine.connectors.UDPFireForgetConnector;
import org.openhim.mediator.engine.messages.*;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;
import java.util.concurrent.Callable;

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

    private void containRequest(final MediatorHTTPRequest request, final ActorRef requestActor) {
        ExecutionContext ec = getContext().dispatcher();
        Future<Boolean> f = future(new Callable<Boolean>() {
            public Boolean call() {
                //TODO
                return Boolean.TRUE;
            }
        }, ec);
        f.onComplete(new OnComplete<Boolean>() {
            @Override
            public void onComplete(Throwable throwable, Boolean result) throws Throwable {
                if (throwable!=null) {
                    log.error(throwable, "Request containment exception");
                }
                if (result==null || !result) {
                    log.warning("Request containment returned non-true result");
                }
            }
        }, ec);
    }

    @Override
    public void onReceive(Object msg) throws Exception {
        if (msg instanceof MediatorHTTPRequest) {
            ActorRef requestHandler = getContext().actorOf(Props.create(MediatorRequestHandler.class, config));
            containRequest((MediatorHTTPRequest) msg, requestHandler);
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
