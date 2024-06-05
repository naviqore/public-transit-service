package ch.naviqore.raptor;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Builds the Raptor and its internal data structures. Ensures that all stops, routes, trips, stop times, and transfers
 * are correctly added and validated before constructing the Raptor model:
 * <ul>
 *     <li>All stops must have at least one route serving them.</li>
 *     <li>All stops of a route must be known before adding the route.</li>
 *     <li>All trips of a route must have the same stop sequence.</li>
 *     <li>Each stop time of a trip must have a departure time after the previous stop time's arrival time.</li>
 *     <li>All trips in the final route container must be sorted by departure time.</li>
 * </ul>
 *
 * @author munterfi
 */
@NoArgsConstructor(access = AccessLevel.PACKAGE)
@Log4j2
public class RaptorBuilder {

    private final Map<String, Integer> stops = new HashMap<>();
    private final Map<String, RouteBuilder> routeBuilders = new HashMap<>();
    private final Map<String, List<Transfer>> transfers = new HashMap<>();
    private final Map<String, Set<String>> stopRoutes = new HashMap<>();

    int stopTimeSize = 0;
    int routeStopSize = 0;
    int transferSize = 0;

    public RaptorBuilder addStop(String id) {
        if (stops.containsKey(id)) {
            throw new IllegalArgumentException("Stop " + id + " already exists");
        }

        log.debug("Adding stop: id={}", id);
        stops.put(id, stops.size());
        stopRoutes.put(id, new HashSet<>());

        return this;
    }

    public RaptorBuilder addRoute(String id, List<String> stopIds) {
        if (routeBuilders.containsKey(id)) {
            throw new IllegalArgumentException("Route " + id + " already exists");
        }

        for (String stopId : stopIds) {
            if (!stops.containsKey(stopId)) {
                throw new IllegalArgumentException("Stop " + stopId + " does not exist");
            }
            stopRoutes.get(stopId).add(id);
        }

        log.debug("Adding route: id={}, stopSequence={}", id, stopIds);
        routeBuilders.put(id, new RouteBuilder(id, stopIds));
        routeStopSize += stopIds.size();

        return this;
    }

    public RaptorBuilder addTrip(String tripId, String routeId) {
        getRouteBuilder(routeId).addTrip(tripId);
        return this;
    }

    public RaptorBuilder addStopTime(String routeId, String tripId, int position, String stopId, int arrival,
                                     int departure) {
        StopTime stopTime = new StopTime(arrival, departure);
        getRouteBuilder(routeId).addStopTime(tripId, position, stopId, stopTime);
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
        log.info("Initialize Raptor with {} stops, {} routes, {} route stops, {} stop times, {} transfers",
                stops.size(), routeBuilders.size(), routeStopSize, stopTimeSize, transferSize);

        // build route containers and the raptor array-based data structures
        List<RouteBuilder.RouteContainer> routeContainers = buildAndSortRouteContainers();
        Lookup lookup = buildLookup(routeContainers);
        StopContext stopContext = buildStopContext(lookup);
        RouteTraversal routeTraversal = buildRouteTraversal(routeContainers);

        return new Raptor(lookup, stopContext, routeTraversal);
    }

    private @NotNull List<RouteBuilder.RouteContainer> buildAndSortRouteContainers() {
        return routeBuilders.values().parallelStream().map(RouteBuilder::build).sorted().toList();
    }

    private Lookup buildLookup(List<RouteBuilder.RouteContainer> routeContainers) {
        log.debug("Building lookup with {} stops and {} routes", stops.size(), routeContainers.size());
        Map<String, Integer> routes = new HashMap<>(routeContainers.size());

        // assign idx to routes based on sorted order
        for (int i = 0; i < routeContainers.size(); i++) {
            RouteBuilder.RouteContainer routeContainer = routeContainers.get(i);
            routes.put(routeContainer.id(), i);
        }

        return new Lookup(Map.copyOf(stops), Map.copyOf(routes));
    }

    private StopContext buildStopContext(Lookup lookup) {
        log.debug("Building stop context with {} stops and {} transfers", stops.size(), transferSize);

        // allocate arrays in needed size
        Stop[] stopArr = new Stop[stops.size()];
        int[] stopRouteArr = new int[stopRoutes.values().stream().mapToInt(Set::size).sum()];
        Transfer[] transferArr = new Transfer[transferSize];

        // iterate over stops and populate arrays
        int transferIdx = 0;
        int stopRouteIdx = 0;
        for (Map.Entry<String, Integer> entry : stops.entrySet()) {
            String stopId = entry.getKey();
            int stopIdx = entry.getValue();

            // check if stop has no routes: Unserved stops are useless in the raptor data structure
            Set<String> currentStopRoutes = stopRoutes.get(stopId);
            if (currentStopRoutes == null) {
                throw new IllegalStateException("Stop " + stopId + " has no routes");
            }

            // get the number of (optional) transfers
            List<Transfer> currentTransfers = transfers.get(stopId);
            int numberOfTransfers = currentTransfers == null ? 0 : currentTransfers.size();

            // add stop entry to stop array
            stopArr[stopIdx] = new Stop(stopId, stopRouteIdx, currentStopRoutes.size(),
                    numberOfTransfers == 0 ? Raptor.NO_INDEX : transferIdx, numberOfTransfers);

            // add transfer entry to transfer array if there are any
            if (currentTransfers != null) {
                for (Transfer transfer : currentTransfers) {
                    transferArr[transferIdx++] = transfer;
                }
            }

            // add route index entries to stop route array
            for (String routeId : currentStopRoutes) {
                stopRouteArr[stopRouteIdx++] = lookup.routes().get(routeId);
            }
        }

        return new StopContext(transferArr, stopArr, stopRouteArr);
    }

    private RouteTraversal buildRouteTraversal(List<RouteBuilder.RouteContainer> routeContainers) {
        log.debug("Building route traversal with {} routes, {} route stops, {} stop times", routeContainers.size(),
                routeStopSize, stopTimeSize);

        // allocate arrays in needed size
        Route[] routeArr = new Route[routeContainers.size()];
        RouteStop[] routeStopArr = new RouteStop[routeStopSize];
        StopTime[] stopTimeArr = new StopTime[stopTimeSize];

        // iterate over routes and populate arrays
        int routeStopCnt = 0;
        int stopTimeCnt = 0;
        for (int routeIdx = 0; routeIdx < routeContainers.size(); routeIdx++) {
            RouteBuilder.RouteContainer routeContainer = routeContainers.get(routeIdx);

            // add route entry to route array
            final int numberOfStops = routeContainer.stopSequence().size();
            final int numberOfTrips = routeContainer.trips().size();
            routeArr[routeIdx] = new Route(routeContainer.id(), routeStopCnt, numberOfStops, stopTimeCnt, numberOfTrips,
                    routeContainer.trips().keySet().toArray(new String[0]));

            // add stops to route stop array
            Map<Integer, String> stopSequence = routeContainer.stopSequence();
            for (int position = 0; position < numberOfStops; position++) {
                int stopIdx = stops.get(stopSequence.get(position));
                routeStopArr[routeStopCnt++] = new RouteStop(stopIdx, routeIdx);
            }

            // add times to stop time array
            for (StopTime[] stopTimes : routeContainer.trips().values()) {
                for (StopTime stopTime : stopTimes) {
                    stopTimeArr[stopTimeCnt++] = stopTime;
                }
            }
        }

        return new RouteTraversal(stopTimeArr, routeArr, routeStopArr);
    }

    private @NotNull RouteBuilder getRouteBuilder(String routeId) {
        RouteBuilder routeBuilder = routeBuilders.get(routeId);
        if (routeBuilder == null) {
            throw new IllegalArgumentException("Route " + routeId + " does not exist");
        }

        return routeBuilder;
    }

}
