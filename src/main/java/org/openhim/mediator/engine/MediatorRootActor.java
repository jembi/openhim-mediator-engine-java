package org.openhim.mediator.engine;

import static akka.dispatch.Futures.future;

import akka.actor.*;
import akka.dispatch.OnComplete;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import fi.iki.elonen.NanoHTTPD;
import org.openhim.mediator.engine.connectors.CoreAPIConnector;
import org.openhim.mediator.engine.connectors.HTTPConnector;
import org.openhim.mediator.engine.messages.*;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * The root actor for the mediator.
 *
 * Its roles are:
 * launch/kill new request actors,
 * launch all single instance actors on startup, and
 * trigger the registration of the mediator to core.
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
                getContext().actorOf(Props.create(actor.getActorClass()), actor.getName());
            }
        }

        getContext().actorOf(Props.create(HTTPConnector.class), "http-connector");
        getContext().actorOf(Props.create(CoreAPIConnector.class, config), "core-api-connector");
    }

    private void containRequest(final NanoHTTPD.ActorContainedRunnable msg, final ActorRef requestActor) {
        ExecutionContext ec = getContext().dispatcher();
        Future<Boolean> f = future(new Callable<Boolean>() {
            public Boolean call() {
                msg.run(requestActor);
                return Boolean.TRUE;
            }
        }, ec);
        f.onComplete(new OnComplete<Boolean>() {
            @Override
            public void onComplete(Throwable throwable, Boolean result) throws Throwable {
                requestActor.tell(PoisonPill.getInstance(), getSelf());
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
        if (msg instanceof NanoHTTPD.ActorContainedRunnable) {
            ActorRef requestHandler = getContext().actorOf(Props.create(MediatorRequestActor.class, config.getRoutingTable()));
            containRequest((NanoHTTPD.ActorContainedRunnable) msg, requestHandler);
        } else if (config.getRegistrationConfig()!=null && msg instanceof RegisterMediatorWithCore) {
            log.info("Registering mediator with core...");
            ActorSelection coreConnector = getContext().actorSelection("/user/" + getSelf().path().name() + "/core-api-connector");
            coreConnector.tell(msg, getSelf());
        } else if (msg instanceof MediatorHTTPResponse) {
            log.info("Sent mediator registration message to core");
            log.info(String.format("Response: %s (%s)", ((MediatorHTTPResponse) msg).getStatusCode(), ((MediatorHTTPResponse) msg).getContent()));
        } else {
            unhandled(msg);
        }
    }
}
