package org.openhim.mediator.engine.messages;

import akka.actor.ActorRef;

/**
 * A base message type that can be used by actors to build request messages
 */
public abstract class MediatorRequestMessage {
    private final String correlationId;
    private final ActorRef requestHandler;
    private final ActorRef respondTo;

    public MediatorRequestMessage(String correlationId, ActorRef requestHandler, ActorRef respondTo) {
        this.correlationId = correlationId;
        this.requestHandler = requestHandler;
        this.respondTo = respondTo;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public ActorRef getRequestHandler() {
        return requestHandler;
    }

    public ActorRef getRespondTo() {
        return respondTo;
    }
}
