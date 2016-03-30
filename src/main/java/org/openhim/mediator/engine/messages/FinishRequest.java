/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.openhim.mediator.engine.messages;

import java.util.Map;
import java.util.TreeMap;

/**
 * A message indicating that the request should end and the final response sent to the client
 */
public class FinishRequest {
    private final String response;
    private final Map<String, String> responseHeaders;
    private final Integer responseStatus;

    public FinishRequest(String response, String responseMimeType, Integer responseStatus) {
        this.response = response;
        Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.put("Content-Type", responseMimeType);
        this.responseHeaders = headers;
        this.responseStatus = responseStatus;
    }

    public FinishRequest(String response, Map<String, String> responseHeaders, Integer responseStatus) {
        this.response = response;
        this.responseHeaders = responseHeaders;
        this.responseStatus = responseStatus;
    }

    public String getResponse() {
        return response;
    }

    public String getResponseMimeType() {
        return responseHeaders.get("Content-Type");
    }

    public Map<String, String> getResponseHeaders() {
        return responseHeaders;
    }

    public Integer getResponseStatus() {
        return responseStatus;
    }
}
