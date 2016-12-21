/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.openhim.mediator.engine.messages;

/**
 * Result of setting up the SSL context.
 * <br>
 * Check {@link #isSuccessful()} for the status, and if false, and error will be available ({@link #getError()})
 *
 * @see SetupSSLContext
 */
public class SetupSSLContextResponse extends MediatorResponseMessage {
    private final Throwable error;

    public SetupSSLContextResponse(MediatorRequestMessage originalRequest, Throwable error) {
        super(originalRequest);
        this.error = error;
    }

    public SetupSSLContextResponse(MediatorRequestMessage originalRequest) {
        super(originalRequest);
        this.error = null;
    }

    public boolean isSuccessful() {
        return error == null;
    }

    public Throwable getError() {
        return error;
    }
}
