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
