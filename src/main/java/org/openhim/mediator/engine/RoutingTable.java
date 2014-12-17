package org.openhim.mediator.engine;

import akka.actor.Actor;

import java.util.HashMap;
import java.util.Map;

public class RoutingTable {
    private Map<String, Class<? extends Actor>> routes = new HashMap<>();

    public void addRoute(String route, Class<? extends Actor> actorClass) throws RouteAlreadyMappedException {
        if (routes.containsKey(route)) {
            throw new RouteAlreadyMappedException();
        }
        routes.put(route, actorClass);
    }

    public Class<? extends Actor> getActorClassForRoute(String route) {
        return routes.get(route);
    }

    public Class<? extends Actor> removeRoute(String route) {
        return routes.remove(route);
    }

    public Map<String, Class<? extends  Actor>> getRawMap() {
        return routes;
    }

    public class RouteAlreadyMappedException extends Exception {}
}
