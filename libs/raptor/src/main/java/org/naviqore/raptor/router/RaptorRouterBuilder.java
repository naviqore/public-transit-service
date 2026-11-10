package org.naviqore.raptor.router;

import lombok.extern.slf4j.Slf4j;

import java.time.ZoneId;
import java.util.*;

import static org.naviqore.raptor.router.QueryState.NO_INDEX;

/**
 * Builds the RAPTOR router and its internal data structures. Ensures that all stops, routes, trips, stop times, and
 * transfers are correctly added and validated before constructing the RAPTOR data model:
 * <ul>
 *     <li>All stops must have at least one route serving them.</li>
 *     <li>All stops of a route must be known before adding the route.</li>
 *     <li>All trips of a route must have the same stop sequence.</li>
 *     <li>Each stop time of a trip must have a departure time after the previous stop time's arrival time.</li>
 *     <li>All trips in the final route containers must be sorted by departure time.</li>
 *     <li>No trip of a route container overtakes another: the trips of a route that do are held in several route
 *     containers, each a route of RAPTOR, which keep the id of the route.</li>
 * </ul>
 * <p>
 * The router does not depend on the order in which stops, routes, trips and transfers are added: stops are numbered by
 * id, route containers are ordered by their first departure and then by id, and trips with the same times by id. Of
 * routes or trips that serve a stop equally well, RAPTOR keeps the one it scans first, so the same timetable always
 * gives the same answers. A transfer added again replaces the one added before.
 *
 * @author munterfi
 */
@Slf4j
public class RaptorRouterBuilder {

    private final RaptorConfig config;
    private final Set<String> stops = new HashSet<>();
    private final Map<String, RouteBuilder> routeBuilders = new HashMap<>();
    private final Map<String, Map<String, Integer>> transfers = new HashMap<>();
    private final Map<String, Integer> sameStopTransfers = new HashMap<>();
    private final Map<String, Set<String>> stopRoutes = new HashMap<>();

    int stopTimeSize = 0;
    int routeStopSize = 0;
    int transferSize = 0;

    public RaptorRouterBuilder(RaptorConfig config) {
        this.config = config;
    }

    public RaptorRouterBuilder addStop(String id) {
        if (stops.contains(id)) {
            throw new IllegalArgumentException("Stop " + id + " already exists");
        }

        log.debug("Adding stop: id={}", id);
        stops.add(id);
        stopRoutes.put(id, new HashSet<>());

        return this;
    }

    public RaptorRouterBuilder addRoute(String id, ZoneId zoneId, List<String> stopIds) {
        if (routeBuilders.containsKey(id)) {
            throw new IllegalArgumentException("Route " + id + " already exists");
        }

        for (String stopId : stopIds) {
            if (!stops.contains(stopId)) {
                throw new IllegalArgumentException("Stop " + stopId + " does not exist");
            }
            stopRoutes.get(stopId).add(id);
        }

        log.debug("Adding route: id={}, stopSequence={}", id, stopIds);
        routeBuilders.put(id, new RouteBuilder(id, zoneId, stopIds));
        routeStopSize += stopIds.size();

        return this;
    }

    public RaptorRouterBuilder addTrip(String tripId, String routeId) {
        getRouteBuilder(routeId).addTrip(tripId);
        return this;
    }

    public RaptorRouterBuilder addStopTime(String routeId, String tripId, int position, String stopId, int arrival,
                                           int departure) {
        StopTime stopTime = new StopTime(arrival, departure);
        getRouteBuilder(routeId).addStopTime(tripId, position, stopId, stopTime);
        stopTimeSize++;

        return this;
    }

    public RaptorRouterBuilder addTransfer(String sourceStopId, String targetStopId, int duration) {
        log.debug("Adding transfer: sourceStopId={}, targetStopId={}, duration={}", sourceStopId, targetStopId,
                duration);

        if (!stops.contains(sourceStopId)) {
            throw new IllegalArgumentException("Source stop " + sourceStopId + " does not exist");
        }

        if (!stops.contains(targetStopId)) {
            throw new IllegalArgumentException("Target stop " + targetStopId + " does not exist");
        }

        if (sourceStopId.equals(targetStopId)) {
            sameStopTransfers.put(sourceStopId, duration);
            return this;
        }

        if (transfers.computeIfAbsent(sourceStopId, _ -> new HashMap<>()).put(targetStopId, duration) == null) {
            transferSize++;
        }

        return this;
    }

    public RaptorRouter build() {
        log.info("Initializing RAPTOR router with {} stops, {} routes, {} route stops, {} stop times, {} transfers",
                stops.size(), routeBuilders.size(), routeStopSize, stopTimeSize, transferSize);

        // number the stops by id, build route containers and the raptor array-based data structures
        String[] stopIds = stops.stream().sorted().toArray(String[]::new);
        List<RouteBuilder.RouteContainer> routeContainers = buildAndSortRouteContainers();
        Lookup lookup = buildLookup(stopIds, routeContainers);
        StopContext stopContext = buildStopContext(stopIds, lookup);
        RouteTraversal routeTraversal = buildRouteTraversal(lookup, routeContainers);

        return new RaptorRouter(lookup, stopContext, routeTraversal, config);
    }

    private List<RouteBuilder.RouteContainer> buildAndSortRouteContainers() {
        List<RouteBuilder.RouteContainer> routeContainers = routeBuilders.values()
                .parallelStream()
                .flatMap(routeBuilder -> routeBuilder.build().stream())
                .sorted()
                .toList();

        if (routeContainers.size() > routeBuilders.size()) {
            log.info("Split routes with trips overtaking one another into {} additional routes",
                    routeContainers.size() - routeBuilders.size());
        }

        return routeContainers;
    }

    private Lookup buildLookup(String[] stopIds, List<RouteBuilder.RouteContainer> routeContainers) {
        log.debug("Building lookup with {} stops and {} routes", stopIds.length, routeContainers.size());

        Map<String, Integer> stopIndices = HashMap.newHashMap(stopIds.length);
        for (int stopIdx = 0; stopIdx < stopIds.length; stopIdx++) {
            stopIndices.put(stopIds[stopIdx], stopIdx);
        }

        // assign idx to routes based on sorted order, the route containers of a route in the same order
        Map<String, List<Integer>> routeIndices = new HashMap<>(routeBuilders.size());
        for (int i = 0; i < routeContainers.size(); i++) {
            routeIndices.computeIfAbsent(routeContainers.get(i).id(), _ -> new ArrayList<>(1)).add(i);
        }

        Map<String, int[]> routes = new HashMap<>(routeIndices.size());
        Map<String, String[]> routeTripIds = new HashMap<>(routeIndices.size());
        for (Map.Entry<String, List<Integer>> entry : routeIndices.entrySet()) {
            routes.put(entry.getKey(), entry.getValue().stream().mapToInt(Integer::intValue).toArray());
            routeTripIds.put(entry.getKey(), entry.getValue()
                    .stream()
                    .flatMap(i -> routeContainers.get(i).trips().keySet().stream())
                    .toArray(String[]::new));
        }

        return new Lookup(Map.copyOf(stopIndices), Map.copyOf(routes), Map.copyOf(routeTripIds));
    }

    private StopContext buildStopContext(String[] stopIds, Lookup lookup) {
        log.debug("Building stop context with {} stops and {} transfers", stopIds.length, transferSize);

        // allocate arrays in needed size
        Stop[] stopArr = new Stop[stopIds.length];
        int[] stopRouteArr = new int[stopRoutes.values()
                .stream()
                .flatMap(Set::stream)
                .mapToInt(routeId -> lookup.routes().get(routeId).length)
                .sum()];
        Transfer[] transferArr = new Transfer[transferSize];

        // iterate over stops and populate arrays
        int transferIdx = 0;
        int stopRouteIdx = 0;
        for (int stopIdx = 0; stopIdx < stopIds.length; stopIdx++) {
            String stopId = stopIds[stopIdx];

            // check if stop has no routes: Unserved stops are useless in the raptor data structure
            Set<String> currentStopRoutes = stopRoutes.get(stopId);
            if (currentStopRoutes == null) {
                throw new IllegalStateException("Stop " + stopId + " has no routes");
            }

            // get the number of (optional) transfers
            Map<String, Integer> currentTransfers = transfers.getOrDefault(stopId, Map.of());
            int numberOfTransfers = currentTransfers.size();

            int sameStopTransferTime = sameStopTransfers.getOrDefault(stopId,
                    config.getSameStopTransferDurationDefault());

            // add stop entry to stop array, a route held in several route containers is served by each of them
            int[] currentRouteIndices = currentStopRoutes.stream()
                    .flatMapToInt(routeId -> Arrays.stream(lookup.routes().get(routeId)))
                    .sorted()
                    .toArray();
            stopArr[stopIdx] = new Stop(stopId, stopRouteIdx, currentRouteIndices.length, sameStopTransferTime,
                    numberOfTransfers == 0 ? NO_INDEX : transferIdx, numberOfTransfers);

            // add transfer entries to transfer array if there are any, by the index of their target stop
            int firstTransferIdx = transferIdx;
            for (Map.Entry<String, Integer> transfer : currentTransfers.entrySet()) {
                transferArr[transferIdx++] = new Transfer(lookup.stops().get(transfer.getKey()), transfer.getValue());
            }
            Arrays.sort(transferArr, firstTransferIdx, transferIdx, Comparator.comparingInt(Transfer::targetStopIdx));

            // add route index entries to stop route array
            for (int routeIdx : currentRouteIndices) {
                stopRouteArr[stopRouteIdx++] = routeIdx;
            }
        }

        return new StopContext(transferArr, stopArr, stopRouteArr);
    }

    private RouteTraversal buildRouteTraversal(Lookup lookup, List<RouteBuilder.RouteContainer> routeContainers) {
        // the stops of a route held in several route containers are those of each of them
        int routeContainerStopSize = routeContainers.stream()
                .mapToInt(routeContainer -> routeContainer.stopSequence().size())
                .sum();
        log.debug("Building route traversal with {} routes, {} route stops, {} stop times", routeContainers.size(),
                routeContainerStopSize, stopTimeSize);

        // allocate arrays in needed size
        Route[] routeArr = new Route[routeContainers.size()];
        RouteStop[] routeStopArr = new RouteStop[routeContainerStopSize];
        int[] stopTimeArr = new int[2 + (stopTimeSize * 2) + (routeContainers.size() * 2)];

        // iterate over routes and populate arrays
        int routeStopCnt = 0;

        // placeholders for min/max value of day
        stopTimeArr[0] = RaptorTripMaskProvider.RouteTripMask.NO_TRIP;
        stopTimeArr[1] = RaptorTripMaskProvider.RouteTripMask.NO_TRIP;

        int stopTimeCnt = 2;
        for (int routeIdx = 0; routeIdx < routeContainers.size(); routeIdx++) {
            RouteBuilder.RouteContainer routeContainer = routeContainers.get(routeIdx);

            // add route entry to route array
            final int numberOfStops = routeContainer.stopSequence().size();
            final int numberOfTrips = routeContainer.trips().size();
            routeArr[routeIdx] = new Route(routeContainer.id(), routeContainer.zoneId(), routeStopCnt, numberOfStops,
                    stopTimeCnt, numberOfTrips, routeContainer.trips().keySet().toArray(new String[0]));

            // will be route day min/max values
            stopTimeArr[stopTimeCnt++] = RaptorTripMaskProvider.RouteTripMask.NO_TRIP;
            stopTimeArr[stopTimeCnt++] = RaptorTripMaskProvider.RouteTripMask.NO_TRIP;

            // add stops to route stop array
            Map<Integer, String> stopSequence = routeContainer.stopSequence();
            for (int position = 0; position < numberOfStops; position++) {
                int stopIdx = lookup.stops().get(stopSequence.get(position));
                routeStopArr[routeStopCnt++] = new RouteStop(stopIdx, routeIdx);
            }

            // add times to stop time array
            for (StopTime[] stopTimes : routeContainer.trips().values()) {
                for (StopTime stopTime : stopTimes) {
                    stopTimeArr[stopTimeCnt++] = stopTime.arrival();
                    stopTimeArr[stopTimeCnt++] = stopTime.departure();
                }
            }
        }

        return new RouteTraversal(stopTimeArr, routeArr, routeStopArr);
    }

    private RouteBuilder getRouteBuilder(String routeId) {
        RouteBuilder routeBuilder = routeBuilders.get(routeId);
        if (routeBuilder == null) {
            throw new IllegalArgumentException("Route " + routeId + " does not exist");
        }

        return routeBuilder;
    }

}
