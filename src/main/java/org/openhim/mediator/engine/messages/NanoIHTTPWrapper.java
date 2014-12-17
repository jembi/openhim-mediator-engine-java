package org.openhim.mediator.engine.messages;

import akka.actor.ActorRef;
import fi.iki.elonen.NanoHTTPD;

public class NanoIHTTPWrapper extends MediatorHTTPRequest {
    public NanoIHTTPWrapper(ActorRef requestHandler, ActorRef respondTo, String orchestration, NanoHTTPD.IHTTPSession session) {
        super(
                requestHandler,
                respondTo,
                orchestration,
                session.getMethod().toString(),
                "http",
                null,
                null,
                session.getUri(),
                session.getQueryParameterString(),
                session.getHeaders(),
                session.getParms()
        );
    }
}
