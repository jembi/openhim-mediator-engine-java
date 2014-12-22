/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.openhim.mediator.engine.messages;

import akka.actor.ActorRef;

public class SimpleMediatorRequest<T>  extends MediatorRequestMessage {
    private T requestObject;

    public SimpleMediatorRequest(ActorRef requestHandler, ActorRef respondTo, String orchestration, String correlationId, T requestObject) {
        super(requestHandler, respondTo, orchestration, correlationId);
        this.requestObject = requestObject;
    }

    public SimpleMediatorRequest(ActorRef requestHandler, ActorRef respondTo, T requestObject) {
        this(requestHandler, respondTo, null, null, requestObject);
    }

    public T getRequestObject() {
        return requestObject;
    }

    public static boolean isInstanceOf(Class requestObjectClass, Object o) {
        if (!(o instanceof SimpleMediatorRequest)) {
            return false;
        }

        return requestObjectClass.isInstance(((SimpleMediatorRequest) o).getRequestObject());
    }
}
