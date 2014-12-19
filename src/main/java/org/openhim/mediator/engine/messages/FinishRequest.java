/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.openhim.mediator.engine.messages;

/**
 * A message indicating that the request should end and the final response sent to the client
 */
public class FinishRequest {
    private final String response;
    private final String responseMimeType;
    private final Integer responseStatus;

    public FinishRequest(String response, String responseMimeType, Integer responseStatus) {
        this.response = response;
        this.responseMimeType = responseMimeType;
        this.responseStatus = responseStatus;
    }

    public String getResponse() {
        return response;
    }

    public String getResponseMimeType() {
        return responseMimeType;
    }

    public Integer getResponseStatus() {
        return responseStatus;
    }
}
