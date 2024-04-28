package ch.naviqore.raptor.model;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the Raptor and its internal data structures
 * <p>
 * Note: The builder expects that stops, routes, route stops and stop times are added in the correct order.
 *
 * @author munterfi
 */
@NoArgsConstructor(access = AccessLevel.PACKAGE)
@Log4j2
public class RaptorBuilder {

    private final Map<String, Integer> stops = new HashMap<>();
    private final Map<String, Integer> routes = new HashMap<>();
    private final Map<String, List<String>> routeStops = new HashMap<>();
    private final Map<String, List<StopTime>> stopTimes = new HashMap<>();

    private int routeSize = 0;
    private int stopSize = 0;
    private int routeStopSize = 0;
    private int stopTimeSize = 0;

    public RaptorBuilder addStop(String id) {
        if (stops.containsKey(id)) {
            throw new IllegalArgumentException("Stop " + id + " already exists");
        }
        log.debug("Adding stop: id={}", id);
        stops.put(id, stops.size());
        stopSize++;
        return this;
    }

    public RaptorBuilder addRoute(String id) {
        if (routes.containsKey(id)) {
            throw new IllegalArgumentException("Route " + id + " already exists");
        }
        log.debug("Adding route: id={}", id);
        routes.put(id, routes.size());
        routeSize++;
        return this;
    }

    public RaptorBuilder addRouteStop(String stopId, String routeId) {
        log.debug("Adding route stop: stopId={}, routeId={}", stopId, routeId);
        if (!stops.containsKey(stopId)) {
            throw new IllegalArgumentException("Stop " + stopId + " does not exist");
        }
        if (!routes.containsKey(routeId)) {
            throw new IllegalArgumentException("Route " + routeId + " does not exist");
        }
        routeStops.computeIfAbsent(routeId, k -> new ArrayList<>()).add(stopId);
        routeStopSize++;
        return this;
    }

    public RaptorBuilder addStopTime(String stopId, String routeId, int arrival, int departure) {
        log.info("Adding stop time: stopId={}, routeId={}, arrival={}, departure={}", stopId, routeId, arrival,
                departure);
        stopTimes.computeIfAbsent(routeId, k -> new ArrayList<>()).add(new StopTime(arrival, departure));
        stopTimeSize++;
        return this;
    }

    public Raptor build() {
        // Stop[] stopArr = new Stop[stopSize];

        log.info("Building route traversal with {} routes, {} route stops, {} stop times", routeSize, routeStopSize,
                stopTimeSize);
        Route[] routeArr = new Route[routeSize];
        RouteStop[] routeStopArr = new RouteStop[routeStopSize];
        StopTime[] stopTimeArr = new StopTime[stopTimeSize];

        int routeStopCnt = 0;
        int stopTimeCnt = 0;
        for (Map.Entry<String, Integer> entry : routes.entrySet()) {
            String routeId = entry.getKey();
            int routeIdx = entry.getValue();

            // fill route stops
            List<String> currentRouteStops = routeStops.get(routeId);
            for (String routeStop : currentRouteStops) {
                routeStopArr[routeStopCnt++] = new RouteStop(stops.get(routeStop), routeIdx);
            }

            // fill stop times
            List<StopTime> currentStopTimes = stopTimes.get(routeId);
            for (StopTime stopTime : currentStopTimes) {
                stopTimeArr[stopTimeCnt++] = stopTime;
            }

            // fill route
            routeArr[routeIdx] = new Route(routeStopCnt - currentRouteStops.size(), currentRouteStops.size(),
                    stopTimeCnt - currentStopTimes.size(), currentStopTimes.size());
        }

        return new Raptor(new RouteTraversal(stopTimeArr, routeArr, routeStopArr));
    }
}
