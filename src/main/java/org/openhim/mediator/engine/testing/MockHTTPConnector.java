/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.openhim.mediator.engine.testing;

import akka.actor.UntypedActor;
import org.openhim.mediator.engine.messages.MediatorHTTPRequest;
import org.openhim.mediator.engine.messages.MediatorHTTPResponse;
import java.util.Map;

/**
 * An mock http-connector. To use, create a subclass and use TestingUtils.launchMockHTTPConnector to launch.
 * Use TestingUtils.clearMockContext at the end of your test when finished.
 */
public abstract class MockHTTPConnector extends UntypedActor {
    /**
     * Mock response
     */
    public abstract String getResponse();

    /**
     * Http status to return
     */
    public abstract Integer getStatus();

    /**
     * Http headers to return
     */
    public abstract Map<String, String> getHeaders();

    /**
     * Arbitrary code to be executed when a request is received
     */
    public abstract void executeOnReceive(MediatorHTTPRequest req);


    @Override
    public void onReceive(Object msg) throws Exception {
        if (msg instanceof MediatorHTTPRequest) {
            executeOnReceive((MediatorHTTPRequest) msg);

            MediatorHTTPResponse rMsg = new MediatorHTTPResponse(
                    (MediatorHTTPRequest)msg, getResponse(), getStatus(), getHeaders()
            );
            ((MediatorHTTPRequest) msg).getRespondTo().tell(rMsg, getSelf());
        } else {
            unhandled(msg);
        }
    }
}
