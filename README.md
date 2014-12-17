OpenHIM Mediator Engine
=======================

**:warning: Note that this project is still in version 0.x.x and the API and functionality is likely to change drastically**

An engine for building OpenHIM mediators based on the [Akka](http://akka.io/) framework.

Getting Started
---------------
Include the jar in your mediator project. If you're using Maven, simply add the following dependency:
```
<dependency>
  <groupId>org.openhim</groupId>
  <artifactId>mediator-engine</artifactId>
  <version>0.0.3</version>
</dependency>
```

The engine provides an HTTP server and Akka handlers for dealing with incoming requests. To fire up the engine, the `org.openhim.mediator.engine.MediatorServer` class is used. You simply have to pass it the necessary config and run the `start()` method:
```
MediatorConfig config = new MediatorConfig("my-mediator", "0.0.0.0", 8500);
//add config values...

MediatorServer server = new MediatorServer(config);
server.start()
```
Note that `start()` is non-blocking.

The server constructor also accepts an `ActorSystem` parameter (e.g. `new MediatorServer(myActorSytem, config)`), in the event that you want to manage this yourself. If _not_ passed, the server will create a new system. Note though that ActorSystem is a heavy object and there should ideally only be a single instance of this in your application.

On startup server will register your mediator with the HIM core and you must pass the registration json info to the config (see [this page](https://github.com/jembi/openhim-core-js/wiki/Creating-an-OpenHIM-mediator) for details about the json format).

You will need at least one actor in your project to receive requests from the engine. If you're starting a new mediator, you can simply create an actor as follows:
```
import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import org.apache.http.HttpStatus;
import org.openhim.mediator.engine.messages.FinishRequest;
import org.openhim.mediator.engine.messages.MediatorHTTPRequest;

public class MyActor extends UntypedActor {
    LoggingAdapter log = Logging.getLogger(getContext().system(), this);

    @Override
    public void onReceive(Object msg) throws Exception {
        if (msg instanceof MediatorHTTPRequest) {
            FinishRequest response = new FinishRequest("A message from my new mediator!", "text/plain", HttpStatus.SC_OK);
            ((MediatorHTTPRequest) msg).getRequestHandler().tell(response, getSelf());
        } else {
            unhandled(msg);
        }
    }
}
```

An actor is simply a Java Object that can receive messages. The engine will send your actor a `MediatorHTTPRequest` message whenever a request is received and you can respond by sending back a `FinishRequest` message. The `MediatorHTTPRequest` provides a reference to the request handler for receiving this response. The above actor template demonstrates a simple actor that responds immediately with an OK message. Note that the response can be sent asyncronously, not necessarily at the same moment you receive the http request like above.

When running, the server will receive requests as well as handle the response, including the formatting of the standard _application/json+openhim_ response, so you do not need to worry about this. Whatever you return in the `FinishRequest` will be embedded in the response object.

To link your actor to the engine you have to create a routing table and pass that to the server config. The routing table consists of path/actor pairs, e.g.:
```
RoutingTable routingTable = new RoutingTable();
routingTable.addRoute("/mymediator", MyActor.class);
```

When receiving a request on the specified path, the engine will launch a new instance of your actor to handle the request (actor-per-request model). This means that you can safely add request-specific state to your actor.

In summary, the following illustrates an example main method that fires up the engine that'll route to the above `MyActor`:
```
import akka.actor.ActorSystem;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import org.openhim.mediator.engine.*;
import java.io.InputStream;

public class MyMediatorMain {

    public static void main(String... args) throws Exception {
        //setup actor system
        final ActorSystem system = ActorSystem.create("mediator");
        //setup logger for main
        final LoggingAdapter log = Logging.getLogger(system, "main");

        //setup actors
        log.info("Initializing mediator actors...");

        MediatorConfig config = new MediatorConfig("my-mediator", "0.0.0.0", 8500);

        config.setCoreHost("localhost");
        config.setCoreAPIUsername("root@openhim.org");
        config.setCoreAPIPassword("openhim-password");

        RoutingTable routingTable = new RoutingTable();
        routingTable.addRoute("/mymediator", MyActor.class);
        config.setRoutingTable(routingTable);

        InputStream regInfo = MyMediatorMain.class.getClassLoader().getResourceAsStream("mediator-registration-info.json");
        RegistrationConfig regConfig = new RegistrationConfig(regInfo);
        config.setRegistrationConfig(regConfig);

        final MediatorServer server = new MediatorServer(system, config);

        //setup shutdown hook (will handle ctrl-c)
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                log.info("Shutting down mediator");
                server.stop();
                system.shutdown();
            }
        });

        log.info("Starting mediator server...");
        server.start();

        log.info("Mediator listening on 0.0.0.0:8500");
        while (true) {
            System.in.read();
        }
    }
}
```

Request Handler Reference
-------------------------
When receiving a request, the engine will send your actor a `MediatorHTTPRequest` message. This message contains a reference to the request handler actor, and it is this actor that you send messages to in order to manage the final mediator response. The messages it supports are as follows:
* **FinishRequestMessage** - Finalize the request and send the response. Note that the request actor instances will be stopped at this point (the engine will send a `PoisonPill` message).
* **ExceptErrorMessage** - An exception has occured and the request should end with a `500 Internal Server Error` response.
* **AddOrchestrationToCoreResponseMessage** - Add orchestration details to the request response. This message can be sent as many times as required.

License
-------
This software is licensed under the Mozilla Public License Version 2.0.

This software makes use of [NanoHTTPD](http://nanohttpd.com/). For license details see [this link](https://github.com/NanoHttpd/nanohttpd/blob/ab6feae737b3038532d057e87fd83c58bad3b3cc/LICENSE.md).
