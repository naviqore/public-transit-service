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
 *     <li>The trips of a route container let passengers board and alight at the same stops: trips that do not are held
 *     in several route containers too.</li>
 *     <li>The routes and trips a trip transfer names exist, and a trip belongs to the route named with it.</li>
 * </ul>
 * <p>
 * The router does not depend on the order in which stops, routes, trips and transfers are added: stops are numbered by
 * id, route containers are ordered by their first departure and then by id, and trips with the same times by id. Of
 * routes or trips that serve a stop equally well, RAPTOR keeps the one it scans first, so the same timetable always
 * gives the same answers. A transfer added again replaces the one added before.
 * <p>
 * A transfer is a walk in one direction, from its source stop to its target stop, and may take another time than the
 * walk back: a search departing follows the walks from a stop, a search arriving, routing backward, the walks to it.
 * Transfers can be refused, at a stop or between two stops, and apply to particular routes or trips only (see
 * {@link TripTransfer}).
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
    private final Set<String> refusingStops = new HashSet<>();
    private final Map<String, Set<String>> refusedWalks = new HashMap<>();
    private final List<TripTransfer> tripTransfers = new ArrayList<>();

    int stopTimeSize = 0;
    int routeStopSize = 0;
    int transferSize = 0;

    public RaptorRouterBuilder(RaptorConfig config) {
        this.config = config;
    }

    /**
     * Adds the walks of a stop to the transfers from an index on, by the index of their target stop.
     *
     * @return the index after them.
     */
    private static int addTransfers(Transfer[] transferArr, int firstTransferIdx, Map<String, Integer> walks,
                                    Lookup lookup) {
        int transferIdx = firstTransferIdx;
        for (Map.Entry<String, Integer> walk : walks.entrySet()) {
            transferArr[transferIdx++] = new Transfer(Objects.requireNonNull(lookup.stops().get(walk.getKey())),
                    walk.getValue());
        }
        Arrays.sort(transferArr, firstTransferIdx, transferIdx, Comparator.comparingInt(Transfer::targetStopIdx));

        return transferIdx;
    }

    /**
     * Adds the slots of the trip transfers to the stops, each a copy of its stop.
     */
    private static StopContext addSlots(StopContext stopContext, TripTransfers tripTransfers) {
        if (tripTransfers.numberOfSlots() == 0) {
            return stopContext;
        }

        Stop[] stops = stopContext.stops();
        Stop[] stopsAndSlots = Arrays.copyOf(stops, stops.length + tripTransfers.numberOfSlots());
        for (int slot = stops.length; slot < stopsAndSlots.length; slot++) {
            stopsAndSlots[slot] = stops[tripTransfers.stopOf(slot)];
        }

        return new StopContext(stopContext.transfers(), stopContext.reverseTransfers(), stopsAndSlots,
                stopContext.stopRoutes());
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
            Objects.requireNonNull(stopRoutes.get(stopId)).add(id);
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
        return addStopTime(routeId, tripId, position, stopId, arrival, departure, true, true);
    }

    /**
     * Adds a stop time of a trip, at which passengers can board or alight only if allowed: a trip that only sets down
     * passengers at a stop cannot be boarded there.
     */
    public RaptorRouterBuilder addStopTime(String routeId, String tripId, int position, String stopId, int arrival,
                                           int departure, boolean boarding, boolean alighting) {
        StopTime stopTime = new StopTime(arrival, departure);
        getRouteBuilder(routeId).addStopTime(tripId, position, stopId, stopTime, boarding, alighting);
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

    /**
     * Refuses the transfers at a stop, or between two stops: no trip can be changed for another at the stop, and no
     * walk leads from the one stop to the other, also one added before or after. Transfers between particular routes or
     * trips may still allow them (see {@link TripTransfer}).
     */
    public RaptorRouterBuilder refuseTransfers(String sourceStopId, String targetStopId) {
        log.debug("Refusing transfers: sourceStopId={}, targetStopId={}", sourceStopId, targetStopId);
        checkStopExists(sourceStopId);
        checkStopExists(targetStopId);

        if (sourceStopId.equals(targetStopId)) {
            refusingStops.add(sourceStopId);
        } else {
            refusedWalks.computeIfAbsent(sourceStopId, _ -> new HashSet<>()).add(targetStopId);
        }

        return this;
    }

    /**
     * Adds a transfer between particular routes or trips, see {@link TripTransfer}. The router is built only if it has
     * the routes and trips the transfer names, which may be added after it.
     */
    public RaptorRouterBuilder addTripTransfer(TripTransfer tripTransfer) {
        log.debug("Adding trip transfer: {}", tripTransfer);
        checkStopExists(tripTransfer.fromStopId());
        checkStopExists(tripTransfer.toStopId());
        boolean namesRouteOrTrip = tripTransfer.fromRouteId() != null || tripTransfer.fromTripId() != null || tripTransfer.toRouteId() != null || tripTransfer.toTripId() != null;
        if (!namesRouteOrTrip) {
            throw new IllegalArgumentException(
                    "Trip transfer from stop " + tripTransfer.fromStopId() + " to stop " + tripTransfer.toStopId() + " names no route or trip");
        }
        tripTransfers.add(tripTransfer);

        return this;
    }

    private void checkStopExists(String stopId) {
        if (!stops.contains(stopId)) {
            throw new IllegalArgumentException("Stop " + stopId + " does not exist");
        }
    }

    public RaptorRouter build() {
        log.info("Initializing RAPTOR router with {} stops, {} routes, {} route stops, {} stop times, {} transfers",
                stops.size(), routeBuilders.size(), routeStopSize, stopTimeSize, transferSize);

        // number the stops by id, build route containers and the raptor array-based data structures
        String[] stopIds = stops.stream().sorted().toArray(String[]::new);
        List<RouteBuilder.RouteContainer> routeContainers = buildAndSortRouteContainers();
        Lookup lookup = buildLookup(stopIds, routeContainers);
        Map<Long, Integer> refusedWalkTimes = removeRefusedWalks(lookup);
        StopContext stopContext = buildStopContext(stopIds, lookup);
        RouteTraversal routeTraversal = buildRouteTraversal(lookup, routeContainers);

        // compile the trip transfers, whose slots follow the stops
        Set<Integer> refusingStopIndices = new HashSet<>();
        refusingStops.forEach(stopId -> refusingStopIndices.add(lookup.stops().get(stopId)));
        TripTransfers.Builder tripTransfersBuilder = new TripTransfers.Builder(lookup, stopContext, routeTraversal,
                refusingStopIndices, refusedWalkTimes);
        tripTransfers.forEach(tripTransfersBuilder::add);
        TripTransfers compiledTripTransfers = tripTransfersBuilder.build();
        stopContext = addSlots(stopContext, compiledTripTransfers);

        return new RaptorRouter(lookup, stopContext, routeTraversal, compiledTripTransfers, config);
    }

    /**
     * Removes the walks between stops that refuse transfers.
     *
     * @return the time each removed walk took, by its source and target stop.
     */
    private Map<Long, Integer> removeRefusedWalks(Lookup lookup) {
        Map<Long, Integer> refusedWalkTimes = new HashMap<>();
        refusedWalks.forEach((sourceStopId, targetStopIds) -> {
            Map<String, Integer> walks = transfers.get(sourceStopId);
            for (String targetStopId : targetStopIds) {
                Integer duration = walks == null ? null : walks.remove(targetStopId);
                if (duration != null) {
                    transferSize--;
                    refusedWalkTimes.put(
                            TripTransfers.Builder.walk(Objects.requireNonNull(lookup.stops().get(sourceStopId)),
                                    Objects.requireNonNull(lookup.stops().get(targetStopId))), duration);
                }
            }
        });
        if (!refusedWalkTimes.isEmpty()) {
            log.info("Removed {} walks between stops that refuse transfers", refusedWalkTimes.size());
        }

        return refusedWalkTimes;
    }

    private List<RouteBuilder.RouteContainer> buildAndSortRouteContainers() {
        List<RouteBuilder.RouteContainer> routeContainers = routeBuilders.values()
                .parallelStream()
                .flatMap(routeBuilder -> routeBuilder.build().stream())
                .sorted()
                .toList();

        if (routeContainers.size() > routeBuilders.size()) {
            log.info(
                    "Split routes with trips overtaking one another, or picking up and setting down at other " + "stops, into {} additional routes",
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
                .mapToInt(routeId -> Objects.requireNonNull(lookup.routes().get(routeId)).length)
                .sum()];
        Transfer[] transferArr = new Transfer[transferSize];
        Transfer[] reverseTransferArr = new Transfer[transferSize];

        // the walks to each stop, from the stops they start at, which a search arriving follows backward
        Map<String, Map<String, Integer>> reverseTransfers = new HashMap<>();
        transfers.forEach((sourceStopId, walks) -> walks.forEach(
                (targetStopId, duration) -> reverseTransfers.computeIfAbsent(targetStopId, _ -> new HashMap<>())
                        .put(sourceStopId, duration)));

        // iterate over stops and populate arrays
        int transferIdx = 0;
        int reverseTransferIdx = 0;
        int stopRouteIdx = 0;
        for (int stopIdx = 0; stopIdx < stopIds.length; stopIdx++) {
            String stopId = stopIds[stopIdx];

            // check if stop has no routes: Unserved stops are useless in the raptor data structure
            Set<String> currentStopRoutes = stopRoutes.get(stopId);
            if (currentStopRoutes == null) {
                throw new IllegalStateException("Stop " + stopId + " has no routes");
            }

            // get the (optional) walks from and to the stop
            Map<String, Integer> currentTransfers = transfers.getOrDefault(stopId, Map.of());
            Map<String, Integer> currentReverseTransfers = reverseTransfers.getOrDefault(stopId, Map.of());
            int numberOfTransfers = currentTransfers.size();
            int numberOfReverseTransfers = currentReverseTransfers.size();

            int sameStopTransferTime = sameStopTransfers.getOrDefault(stopId,
                    config.getSameStopTransferDurationDefault());

            // add stop entry to stop array, a route held in several route containers is served by each of them
            int[] currentRouteIndices = currentStopRoutes.stream()
                    .flatMapToInt(routeId -> Arrays.stream(lookup.routes().get(routeId)))
                    .sorted()
                    .toArray();
            stopArr[stopIdx] = new Stop(stopId, stopRouteIdx, currentRouteIndices.length, sameStopTransferTime,
                    refusingStops.contains(stopId), numberOfTransfers == 0 ? NO_INDEX : transferIdx, numberOfTransfers,
                    numberOfReverseTransfers == 0 ? NO_INDEX : reverseTransferIdx, numberOfReverseTransfers);

            transferIdx = addTransfers(transferArr, transferIdx, currentTransfers, lookup);
            reverseTransferIdx = addTransfers(reverseTransferArr, reverseTransferIdx, currentReverseTransfers, lookup);

            // add route index entries to stop route array
            for (int routeIdx : currentRouteIndices) {
                stopRouteArr[stopRouteIdx++] = routeIdx;
            }
        }

        return new StopContext(transferArr, reverseTransferArr, stopArr, stopRouteArr);
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
                int stopIdx = Objects.requireNonNull(lookup.stops().get(stopSequence.get(position)));
                routeStopArr[routeStopCnt++] = new RouteStop(stopIdx, routeIdx, routeContainer.boarding(position),
                        routeContainer.alighting(position));
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
