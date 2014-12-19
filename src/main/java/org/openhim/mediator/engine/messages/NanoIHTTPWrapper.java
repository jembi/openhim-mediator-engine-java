/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.openhim.mediator.engine.messages;

import akka.actor.ActorRef;
import fi.iki.elonen.NanoHTTPD;

public class NanoIHTTPWrapper extends MediatorHTTPRequest {
    public NanoIHTTPWrapper(ActorRef requestHandler, ActorRef respondTo, String orchestration, NanoHTTPD.IHTTPSession session) {
        super(
                requestHandler,
                respondTo,
                orchestration,
                session.getMethod().toString(),
                "http",
                null,
                null,
                session.getUri(),
                session.getQueryParameterString(),
                session.getHeaders(),
                session.getParms()
        );
    }
}
