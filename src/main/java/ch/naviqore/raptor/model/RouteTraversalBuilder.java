package ch.naviqore.raptor.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PACKAGE)
public class RouteTraversalBuilder {

    private final Map<String, Integer> routes = new HashMap<>();
    private final Map<String, Integer> stops = new HashMap<>();

    public static RouteTraversalBuilder builder() {
        return new RouteTraversalBuilder();
    }

    public RouteTraversalBuilder addRoute(String id) {
        if (routes.containsKey(id)) {
            throw new IllegalArgumentException("Route " + id + " already exists");
        }
        routes.put(id, routes.size());
        return this;
    }

    public RouteTraversalBuilder addStopTime() {
        return this;
    }

    public RouteTraversalBuilder addRouteStop(String id, String routeid) {
        if (routes.containsKey(id)) {
            throw new IllegalArgumentException("Route " + id + " already exists");
        }
        return this;
    }

}
