package org.openhim.mediator.engine;

import akka.actor.Actor;

import java.util.LinkedList;
import java.util.List;

/**
 * A list of actors that will get launch by the mediator root on startup.
 * Useful for launching stateless, singleton actors that aren't linked to the request context.
 * <br/><br/>
 * The actors are launched immediately below the root on the hierarchy and so are available on the path /user/{root}/{actorName}
 *
 * @see org.openhim.mediator.engine.MediatorRootActor
 */
public class StartupActorsConfig {
    public static class ActorToLaunch {
        private String name;
        private Class<? extends Actor> actorClass;

        public ActorToLaunch(String name, Class<? extends Actor> actorClass) {
            this.name = name;
            this.actorClass = actorClass;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Class<? extends Actor> getActorClass() {
            return actorClass;
        }

        public void setActorClass(Class<? extends Actor> actorClass) {
            this.actorClass = actorClass;
        }
    }

    private List<ActorToLaunch> actors = new LinkedList<>();

    public void addActor(ActorToLaunch actor) {
        actors.add(actor);
    }

    public void addActor(String name, Class<? extends Actor> clazz) {
        actors.add(new ActorToLaunch(name, clazz));
    }

    public List<ActorToLaunch> getActors() {
        return actors;
    }
}
