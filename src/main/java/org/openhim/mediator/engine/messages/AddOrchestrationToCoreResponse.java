/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.openhim.mediator.engine.messages;

import org.openhim.mediator.engine.CoreResponse;

public class AddOrchestrationToCoreResponse {
    private final CoreResponse.Orchestration orchestration;

    public AddOrchestrationToCoreResponse(CoreResponse.Orchestration orchestration) {
        this.orchestration = orchestration;
    }

    public CoreResponse.Orchestration getOrchestration() {
        return orchestration;
    }
}
