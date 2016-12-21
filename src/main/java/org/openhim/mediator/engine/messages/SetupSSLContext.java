/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.openhim.mediator.engine.messages;

import akka.actor.ActorRef;
import org.openhim.mediator.engine.MediatorConfig;

/**
 * Setup the SSL context for the {@link org.openhim.mediator.engine.connectors.HTTPConnector}.
 * <br>
 * It's best not to use this message directly, but rather specify the context settings in {@link MediatorConfig}
 * and let the {@link org.openhim.mediator.engine.MediatorServer} coordinate and trigger this message.
 *
 * @see org.openhim.mediator.engine.MediatorServer
 * @see org.openhim.mediator.engine.MediatorConfig#setSSLContext(MediatorConfig.SSLContext)
 * @see SetupSSLContextResponse
 */
public class SetupSSLContext extends SimpleMediatorRequest<MediatorConfig.SSLContext> {
    public SetupSSLContext(ActorRef requestHandler, ActorRef respondTo, MediatorConfig.SSLContext sslContext) {
        super(requestHandler, respondTo, sslContext);
    }
}
