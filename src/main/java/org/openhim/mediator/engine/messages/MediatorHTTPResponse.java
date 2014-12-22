/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.openhim.mediator.engine.messages;

import java.util.Map;

public class MediatorHTTPResponse extends MediatorResponseMessage {
    private final String body;
    private final Integer statusCode;
    private final Map<String, String> headers;

    public MediatorHTTPResponse(MediatorHTTPRequest originalRequest, String body, Integer statusCode, Map<String, String> headers) {
        super(originalRequest);
        this.body = body;
        this.statusCode = statusCode;
        this.headers = headers;
    }

    public MediatorHTTPResponse(String body, Integer statusCode, Map<String, String> headers) {
        this(null, body, statusCode, headers);
    }


    public String getBody() {
        return body;
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    /**
     * Convert the message to a FinishRequest
     */
    public FinishRequest toFinishRequest() {
        if (body!=null) {
            String mime = "text/plain";
            if (headers!=null && headers.containsKey("Content-Type")) {
                mime = headers.get("Content-Type");
            }
            return new FinishRequest(body, mime, statusCode);
        }

        return new FinishRequest(null, null, statusCode);
    }
}
