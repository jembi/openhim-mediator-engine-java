package org.openhim.mediator.engine;

import akka.actor.*;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import fi.iki.elonen.NanoHTTPD;
import org.openhim.mediator.engine.messages.RegisterMediatorWithCore;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * The mediator engine HTTP server.
 */
public class MediatorServer extends NanoHTTPD {
    private static class MediatorAsyncRunner implements AsyncRunner {
        final ActorSystem system;
        final ActorRef rootActor;

        public MediatorAsyncRunner(ActorSystem system, ActorRef rootActor) {
            this.system = system;
            this.rootActor = rootActor;
        }

        @Override
        public void exec(ActorContainedRunnable code) {
            Inbox inbox = Inbox.create(system);
            inbox.send(rootActor, code);
        }
    }

    private final LoggingAdapter log;
    private final ActorSystem system;
    private final ActorRef rootActor;
    private final MediatorConfig config;

    public MediatorServer(ActorSystem system, MediatorConfig config) {
        super(config.getServerHost(), config.getServerPort());
        this.system = system;
        this.rootActor = system.actorOf(Props.create(MediatorRootActor.class, config), config.getName());
        this.config = config;
        setAsyncRunner(new MediatorAsyncRunner(system, rootActor));
        log = Logging.getLogger(system, "http-server");
    }

    public MediatorServer(MediatorConfig config) {
        this(ActorSystem.create("mediator"), config);
    }


    private FiniteDuration getRootTimeout() {
        if (config.getRootTimeout()!=null) {
            return Duration.create(config.getRootTimeout(), TimeUnit.MILLISECONDS);
        }
        return Duration.create(1, TimeUnit.MINUTES);
    }

    @Override
    public Response serve(IHTTPSession session) {
        try {
            Inbox inbox = Inbox.create(system);
            inbox.send(session.getRequestActor(), session);
            Object result = inbox.receive(getRootTimeout());
            return (Response) result;
        } catch (Exception ex) {
            String msg = "An internal server error occurred";

            if (ex instanceof TimeoutException) {
                msg = "Request timed out";
                log.warning(msg);
            } else {
                log.error(ex, "Exception");
            }
            return new Response(Response.Status.INTERNAL_ERROR, "text/plain", msg);
        }
    }

    @Override
    public void start() throws IOException {
        super.start();
        Inbox inbox = Inbox.create(system);
        inbox.send(rootActor, new RegisterMediatorWithCore());
    }
}
