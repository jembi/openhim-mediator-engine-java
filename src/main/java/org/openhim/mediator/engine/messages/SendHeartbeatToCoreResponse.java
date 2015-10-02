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

/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.openhim.mediator.engine.messages;

import java.util.Map;

public class SendHeartbeatToCoreResponse {
    private boolean requestSucceeded;
    private String rawResponse;
    private Map<String, Object> config;

    public SendHeartbeatToCoreResponse(boolean requestSucceeded, String rawResponse, Map<String, Object> config) {
        this.requestSucceeded = requestSucceeded;
        this.rawResponse = rawResponse;
        this.config = config;
    }

    public SendHeartbeatToCoreResponse(String rawResponse, Map<String, Object> config) {
        this(true, rawResponse, config);
    }

    public SendHeartbeatToCoreResponse(String rawResponse) {
        this(rawResponse, null);
    }

    public boolean receivedConfigUpdate() {
        return config!=null;
    }

    public Map<String, Object> getConfig() {
        return config;
    }

    public boolean requestSucceeded() {
        return requestSucceeded;
    }

    public String getRawResponse() {
        return rawResponse;
    }
}
