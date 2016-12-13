/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.openhim.mediator.engine.messages;

/**
 * A message indicating that an error has occurred and the request should terminate
 */
public class ExceptError {
    private final Object originalRequest;
    private final Throwable error;

    public ExceptError(Object originalRequest, Throwable error) {
        this.originalRequest = originalRequest;
        this.error = error;
    }

    public ExceptError(Throwable error) {
        this(null, error);
    }

    public Object getOriginalRequest() {
        return originalRequest;
    }

    public Throwable getError() {
        return error;
    }
}
