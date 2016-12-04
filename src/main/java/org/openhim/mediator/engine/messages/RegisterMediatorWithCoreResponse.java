/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.openhim.mediator.engine.messages;

public class RegisterMediatorWithCoreResponse {
    private final boolean successful;
    private final Integer responseStatus;
    private final String responseBody;

    public RegisterMediatorWithCoreResponse(boolean successful, Integer responseStatus, String responseBody) {
        this.successful = successful;
        this.responseStatus = responseStatus;
        this.responseBody = responseBody;
    }

    public boolean isSuccessful() {
        return successful;
    }

    public Integer getResponseStatus() {
        return responseStatus;
    }

    public String getResponseBody() {
        return responseBody;
    }
}
