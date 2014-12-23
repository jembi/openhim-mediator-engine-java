/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.openhim.mediator.engine.messages;

import akka.actor.ActorRef;

public class MediatorSocketRequest extends MediatorRequestMessage {
    private final String host;
    private final Integer port;
    private final String body;

    public MediatorSocketRequest(ActorRef requestHandler, ActorRef respondTo, String orchestration, String correlationId, String host, Integer port, String body) {
        super(requestHandler, respondTo, orchestration, correlationId);
        this.host = host;
        this.port = port;
        this.body = body;
    }

    public MediatorSocketRequest(ActorRef requestHandler, ActorRef respondTo, String orchestration, String host, Integer port, String body) {
        this(requestHandler, respondTo, orchestration, null, host, port, body);
    }

    public MediatorSocketRequest(ActorRef requestHandler, ActorRef respondTo, String host, Integer port, String body) {
        this(requestHandler, respondTo, null, null, host, port, body);
    }

    public String getHost() {
        return host;
    }

    public Integer getPort() {
        return port;
    }

    public String getBody() {
        return body;
    }
}
