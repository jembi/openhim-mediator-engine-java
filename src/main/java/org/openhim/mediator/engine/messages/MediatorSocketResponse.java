/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.openhim.mediator.engine.messages;

public class MediatorSocketResponse extends MediatorResponseMessage {
    private final String body;

    public MediatorSocketResponse(MediatorRequestMessage originalRequest, String body) {
        super(originalRequest);
        this.body = body;
    }

    public String getBody() {
        return body;
    }
}
