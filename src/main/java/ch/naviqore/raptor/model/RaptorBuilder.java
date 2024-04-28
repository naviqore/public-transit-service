package ch.naviqore.raptor.model;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;

import java.util.*;

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
    private final Map<String, Set<String>> stopRoutes = new HashMap<>();
    private final Map<String, List<Transfer>> transfers = new HashMap<>();

    private int stopSize = 0;
    private int routeSize = 0;
    private int routeStopSize = 0;
    private int stopTimeSize = 0;
    private int transferSize = 0;

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
        stopRoutes.computeIfAbsent(stopId, k -> new HashSet<>()).add(routeId);
        routeStopSize++;
        return this;
    }

    public RaptorBuilder addStopTime(String stopId, String routeId, int arrival, int departure) {
        log.debug("Adding stop time: stopId={}, routeId={}, arrival={}, departure={}", stopId, routeId, arrival,
                departure);
        if (!stops.containsKey(stopId)) {
            log.error("Stop {} does not exist", stopId);
            // TODO: Reactivate after test for consistency of route stops.
            // throw new IllegalArgumentException("Stop " + stopId + " does not exist");
        }
        if (!routes.containsKey(routeId)) {
            throw new IllegalArgumentException("Route " + routeId + " does not exist");
        }
        stopTimes.computeIfAbsent(routeId, k -> new ArrayList<>()).add(new StopTime(arrival, departure));
        stopTimeSize++;
        return this;
    }

    public RaptorBuilder addTransfer(String sourceStopId, String targetStopId, int duration) {
        log.debug("Adding transfer: sourceStopId={}, targetStopId={}, duration={}", sourceStopId, targetStopId,
                duration);
        if (!stops.containsKey(sourceStopId)) {
            throw new IllegalArgumentException("Source stop " + sourceStopId + " does not exist");
        }
        if (!stops.containsKey(targetStopId)) {
            throw new IllegalArgumentException("Target stop " + targetStopId + " does not exist");
        }
        transfers.computeIfAbsent(sourceStopId, k -> new ArrayList<>())
                .add(new Transfer(stops.get(targetStopId), duration));
        transferSize++;
        return this;
    }

    public Raptor build() {
        Lookup lookup = buildLookup();
        StopContext stopContext = buildStopContext();
        RouteTraversal routeTraversal = buildRouteTraversal();
        log.info("Initialize Raptor with {} stops, {} routes, {} route stops, {} stop times, {} transfers", stopSize,
                routeSize, routeStopSize, stopTimeSize, transferSize);
        return new Raptor(lookup, routeTraversal, stopContext);
    }

    private Lookup buildLookup() {
        log.info("Building lookup with {} stops and {} routes", stopSize, routeSize);
        return new Lookup(new HashMap<>(stops), new HashMap<>(routes));
    }

    private StopContext buildStopContext() {
        log.info("Building stop context with {} stops and {} transfers", stopSize, transferSize);
        Stop[] stopArr = new Stop[stopSize];
        int[] stopRouteArr = new int[stopRoutes.values().stream().mapToInt(Set::size).sum()];
        Transfer[] transferArr = new Transfer[transferSize];

        int transferCnt = 0;
        int stopRouteCnt = 0;
        for (Map.Entry<String, Integer> entry : stops.entrySet()) {
            String stopId = entry.getKey();
            int stopIdx = entry.getValue();

            List<Transfer> currentTransfers = transfers.get(stopId);
            int currentTransferCnt = 0;
            if (currentTransfers != null) {
                for (Transfer transfer : currentTransfers) {
                    transferArr[transferCnt++] = transfer;
                    currentTransferCnt++;
                }
            }

            Set<String> currentStopRoutes = stopRoutes.get(stopId);
            if (currentStopRoutes == null) {
                throw new IllegalStateException("Stop " + stopId + " has no routes");
            }
            for (String routeId : currentStopRoutes) {
                stopRouteArr[stopRouteCnt++] = routes.get(routeId);
            }

            stopArr[stopIdx] = new Stop(stopId, stopRouteCnt - currentStopRoutes.size(), currentStopRoutes.size(),
                    currentTransferCnt == 0 ? Raptor.NO_INDEX : transferCnt - currentTransferCnt, currentTransferCnt);
        }
        return new StopContext(transferArr, stopArr, stopRouteArr);
    }

    private RouteTraversal buildRouteTraversal() {
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

            List<String> currentRouteStops = routeStops.get(routeId);
            if (currentRouteStops == null) {
                throw new IllegalStateException("Route " + routeId + " has no route stops");
            }
            for (String routeStop : currentRouteStops) {
                routeStopArr[routeStopCnt++] = new RouteStop(stops.get(routeStop), routeIdx);
            }

            List<StopTime> currentStopTimes = stopTimes.get(routeId);
            if (currentStopTimes == null) {
                throw new IllegalStateException("Route " + routeId + " has no stop times");
            }
            for (StopTime stopTime : currentStopTimes) {
                stopTimeArr[stopTimeCnt++] = stopTime;
            }

            routeArr[routeIdx] = new Route(routeId, routeStopCnt - currentRouteStops.size(), currentRouteStops.size(),
                    stopTimeCnt - currentStopTimes.size(), currentStopTimes.size());
        }
        return new RouteTraversal(stopTimeArr, routeArr, routeStopArr);
    }
}
