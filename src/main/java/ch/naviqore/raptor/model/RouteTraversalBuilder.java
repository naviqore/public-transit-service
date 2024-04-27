package ch.naviqore.raptor.model;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;

import java.util.HashMap;
import java.util.Map;

@NoArgsConstructor(access = AccessLevel.PACKAGE)
@Log4j2
public class RouteTraversalBuilder {

    private final Map<String, Integer> routes = new HashMap<>();
    private final Map<String, Integer> routeStops = new HashMap<>();

    public RouteTraversalBuilder addRoute(String id) {
        if (routes.containsKey(id)) {
            throw new IllegalArgumentException("Route " + id + " already exists");
        }
        log.info("Adding route: id={}", id);
        routes.put(id, routes.size());
        return this;
    }

    public RouteTraversalBuilder addRouteStop(String stopId, String routeId) {
        log.info("Adding route stop: stopId={}, routeId={}", stopId, routeId);
        return this;
    }

    public RouteTraversalBuilder addStopTime(String stopId, String routeId, int arrival, int departure) {
        log.info("Adding stop time: stopId={}, routeId={}, arrival={}, departure={}", stopId, routeId, arrival,
                departure);
        return this;
    }

    public RouteTraversal build() {
        return new RouteTraversal(null, null, null);
    }
}
