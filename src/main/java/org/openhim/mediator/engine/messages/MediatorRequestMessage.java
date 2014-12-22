/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.openhim.mediator.engine.messages;

import akka.actor.ActorRef;

/**
 * A base message type that can be used by actors to build request messages
 */
public abstract class MediatorRequestMessage {
    private final ActorRef requestHandler;
    private final ActorRef respondTo;
    private final String correlationId;
    private final String orchestration;

    public MediatorRequestMessage(ActorRef requestHandler, ActorRef respondTo, String orchestration, String correlationId) {
        this.requestHandler = requestHandler;
        this.respondTo = respondTo;
        this.orchestration = orchestration;
        this.correlationId = correlationId;
    }

    public MediatorRequestMessage(ActorRef requestHandler, ActorRef respondTo, String orchestration) {
        this(requestHandler, respondTo, orchestration, null);
    }

    public MediatorRequestMessage(ActorRef requestHandler, ActorRef respondTo) {
        this(requestHandler, respondTo, null, null);
    }

    public ActorRef getRequestHandler() {
        return requestHandler;
    }

    public ActorRef getRespondTo() {
        return respondTo;
    }

    public String getOrchestration() {
        return orchestration;
    }

    public String getCorrelationId() {
        return correlationId;
    }
}
