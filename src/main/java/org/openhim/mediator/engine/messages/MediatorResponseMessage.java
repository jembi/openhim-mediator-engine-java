/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.openhim.mediator.engine.messages;

/**
 * A base message type that can be used by actors to build response messages
 */
public abstract class MediatorResponseMessage {
    private MediatorRequestMessage originalRequest;

    public MediatorResponseMessage(MediatorRequestMessage originalRequest) {
        this.originalRequest = originalRequest;
    }

    public MediatorRequestMessage getOriginalRequest() {
        return originalRequest;
    }
}
