/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.openhim.mediator.engine.testing;

import akka.actor.*;

import java.util.List;

/**
 * Utility for launching actors
*/
public class MockLauncher extends UntypedActor {
    public static class ActorToLaunch {
        private String name;
        private Class<? extends Actor> actorClass;

        public ActorToLaunch(String name, Class<? extends Actor> actorClass) {
            this.name = name;
            this.actorClass = actorClass;
        }
    }

    public MockLauncher(List<ActorToLaunch> actorsToLaunch) {
        for (ActorToLaunch actorToLaunch : actorsToLaunch) {
            getContext().actorOf(Props.create(actorToLaunch.actorClass), actorToLaunch.name);
        }
    }

    @Override
    public void onReceive(Object msg) throws Exception {
        unhandled(msg);
    }
}
