/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.openhim.mediator.engine.messages;

import java.util.Map;
import java.util.TreeMap;

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

    private Map<String, String> copyHeaders(Map<String, String> headers) {
        Map<String, String> copy = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (String header : headers.keySet()) {
            if ("Content-Length".equalsIgnoreCase(header) || "Content-Encoding".equalsIgnoreCase(header) ||
                    "Transfer-Encoding".equalsIgnoreCase(header) || "Connection".equalsIgnoreCase(header)) {
                continue;
            }

            copy.put(header, headers.get(header));
        }
        return copy;
    }

    /**
     * Convert the message to a FinishRequest
     */
    public FinishRequest toFinishRequest(boolean includeHeaders) {
        if (includeHeaders) {
            return new FinishRequest(body, copyHeaders(headers), statusCode);
        } else {
            String mime = null;
            if (body!=null) {
                mime = "text/plain";
                if (headers!=null && (headers.containsKey("Content-Type") || headers.containsKey("content-type"))) {
                    mime = headers.get("Content-Type");
                    if (mime==null) {
                        mime = headers.get("content-type");
                    }
                }
            }

            return new FinishRequest(body, mime, statusCode);
        }
    }

    /**
     * Convert the message to a FinishRequest
     */
    public FinishRequest toFinishRequest() {
        return toFinishRequest(false);
    }
}
