package org.openhim.mediator.engine;

import akka.actor.Actor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RoutingTable {
    private static class Route {
        String path;
        boolean isRegex;

        public Route(String path, boolean isRegex) {
            this.path = path;
            this.isRegex = isRegex;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Route route1 = (Route) o;

            if (!path.equals(route1.path)) return false;

            return true;
        }

        @Override
        public int hashCode() {
            return path.hashCode();
        }
    }

    private Map<Route, Class<? extends Actor>> routes = new LinkedHashMap<>();


    private void addRoute(Route route, Class<? extends Actor> actorClass) throws RouteAlreadyMappedException {
        if (routes.containsKey(route)) {
            throw new RouteAlreadyMappedException();
        }
        routes.put(route, actorClass);
    }

    public void addRoute(String route, Class<? extends Actor> actorClass) throws RouteAlreadyMappedException {
        addRoute(new Route(route, false), actorClass);
    }

    public void addRegexRoute(String urlPattern, Class<? extends Actor> actorClass) throws RouteAlreadyMappedException {
        addRoute(new Route(urlPattern, true), actorClass);
    }

    public Class<? extends Actor> getActorClassForPath(String path) {
        for (Route route : routes.keySet()) {
            if (route.isRegex) {
                Pattern p = Pattern.compile(route.path);
                Matcher m = p.matcher(path);
                if (m.matches()) {
                    return routes.get(route);
                }
            } else if (route.path.equals(path)) {
                return routes.get(route);
            }
        }
        return null;
    }

    public Class<? extends Actor> removeRoute(String route) {
        return routes.remove(route);
    }


    public class RouteAlreadyMappedException extends Exception {}
}
