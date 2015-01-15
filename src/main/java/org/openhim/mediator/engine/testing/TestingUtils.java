/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.openhim.mediator.engine.testing;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.PoisonPill;
import akka.actor.Props;

import java.util.Collections;
import java.util.List;

public class TestingUtils {
    /**
     * Launch a mock http-connector on a specific root context
     */
    public static void launchMockHTTPConnector(ActorSystem system, String rootContext, Class<? extends MockHTTPConnector> mockConnector) {
        launchActors(system, rootContext, Collections.singletonList(new MockLauncher.ActorToLaunch("http-connector", mockConnector)));
    }

    /**
     * Launch actors on a specific root context
     */
    public static void launchActors(ActorSystem system, String rootContext, List<MockLauncher.ActorToLaunch> actorsToLaunch) {
        system.actorOf(Props.create(MockLauncher.class, actorsToLaunch), rootContext);
    }

    /**
     * Clear the root context
     */
    public static void clearRootContext(ActorSystem system, String rootContext) {
        system.actorSelection("/user/" + rootContext).tell(PoisonPill.getInstance(), ActorRef.noSender());
    }
}
