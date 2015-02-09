/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.openhim.mediator.engine.messages;

import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;

/**
 */
public class GrizzlyHTTPRequest {
    private final Request request;
    private final Response responseHandle;

    public GrizzlyHTTPRequest(Request request, Response responseHandle) {
        this.request = request;
        this.responseHandle = responseHandle;
    }

    public Request getRequest() {
        return request;
    }

    public Response getResponseHandle() {
        return responseHandle;
    }
}
