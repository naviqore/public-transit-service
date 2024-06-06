package ch.naviqore.raptor;

import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Raptor algorithm implementation
 */
@Log4j2
public class Raptor {

    public final static int INFINITY = Integer.MAX_VALUE;
    public final static int NO_INDEX = -1;
    public final static int SAME_STOP_TRANSFER_TIME = 120;
    private final InputValidator validator = new InputValidator();
    // lookup
    private final Map<String, Integer> stopsToIdx;
    private final Map<String, Integer> routesToIdx;
    // stop context
    private final Transfer[] transfers;
    private final Stop[] stops;
    private final int[] stopRoutes;
    // route traversal
    private final StopTime[] stopTimes;
    private final Route[] routes;
    private final RouteStop[] routeStops;

    Raptor(Lookup lookup, StopContext stopContext, RouteTraversal routeTraversal) {
        this.stopsToIdx = lookup.stops();
        this.routesToIdx = lookup.routes();
        this.transfers = stopContext.transfers();
        this.stops = stopContext.stops();
        this.stopRoutes = stopContext.stopRoutes();
        this.stopTimes = routeTraversal.stopTimes();
        this.routes = routeTraversal.routes();
        this.routeStops = routeTraversal.routeStops();
    }

    public static RaptorBuilder builder() {
        return new RaptorBuilder();
    }

    public List<Connection> routeEarliestArrival(String sourceStopId, String targetStopId, int departureTime) {
        return routeEarliestArrival(createStopMap(sourceStopId, departureTime), createStopMap(targetStopId, 0));
    }

    // TODO: Do we still need this? There are no usages...
    public List<Connection> routeEarliestArrival(Collection<String> sourceStopIds, Collection<String> targetStopIds,
                                                 int departureTime) {
        Map<String, Integer> sourceStops = createStopMap(sourceStopIds, departureTime);
        Map<String, Integer> targetStops = createStopMap(targetStopIds, 0);
        return routeEarliestArrival(sourceStops, targetStops);
    }

    private Map<String, Integer> createStopMap(String stopId, int value) {
        return Map.of(stopId, value);
    }

    private Map<String, Integer> createStopMap(Collection<String> stopIds, int value) {
        Map<String, Integer> stopMap = new HashMap<>();
        for (String stopId : stopIds) {
            stopMap.put(stopId, value);
        }
        return stopMap;
    }

    // TODO: Do we still need this? There are no usages...
    private Map<String, Integer> createStopMap(List<String> stopIds, List<Integer> values) {
        if (stopIds.size() != values.size()) {
            throw new IllegalArgumentException("Stop IDs and values must have the same size.");
        }
        Map<String, Integer> stopMap = new HashMap<>();
        for (int i = 0; i < stopIds.size(); i++) {
            stopMap.put(stopIds.get(i), values.get(i));
        }
        return stopMap;
    }

    public List<Connection> routeEarliestArrival(Map<String, Integer> sourceStops, Map<String, Integer> targetStopIds) {
        InputValidator.validateStopPermutations(sourceStops, targetStopIds);
        Map<Integer, Integer> validatedSourceStopIdx = validator.validateStops(sourceStops);
        Map<Integer, Integer> validatedTargetStopIdx = validator.validateStops(targetStopIds);
        int[] sourceStopIdxs = validatedSourceStopIdx.keySet().stream().mapToInt(Integer::intValue).toArray();
        int[] departureTimes = validatedSourceStopIdx.values().stream().mapToInt(Integer::intValue).toArray();
        int[] targetStopIdxs = validatedTargetStopIdx.keySet().stream().mapToInt(Integer::intValue).toArray();
        int[] walkingDurationsToTarget = validatedTargetStopIdx.values().stream().mapToInt(Integer::intValue).toArray();

        log.info("Routing earliest arrival from {} to {} at {}", sourceStopIdxs, targetStopIdxs, departureTimes);
        List<Leg[]> earliestArrivalsPerRound = spawnFromSourceStop(sourceStopIdxs, targetStopIdxs, departureTimes,
                walkingDurationsToTarget);

        // get pareto-optimal solutions
        return reconstructParetoOptimalSolutions(earliestArrivalsPerRound, targetStopIdxs);
    }

    public Map<String, Connection> getIsoLines(Map<String, Integer> sourceStops) {
        Map<Integer, Integer> validatedSourceStopIdx = validator.validateStops(sourceStops);
        int[] sourceStopIdxs = validatedSourceStopIdx.keySet().stream().mapToInt(Integer::intValue).toArray();
        int[] departureTimes = validatedSourceStopIdx.values().stream().mapToInt(Integer::intValue).toArray();
        List<Leg[]> earliestArrivalsPerRound = spawnFromSourceStop(sourceStopIdxs, departureTimes);

        Map<String, Connection> isoLines = new HashMap<>();
        for (int i = 0; i < stops.length; i++) {
            Stop stop = stops[i];
            Leg earliestArrival = null;
            for (Leg[] legs : earliestArrivalsPerRound) {
                if (legs[i] != null) {
                    if (earliestArrival == null) {
                        earliestArrival = legs[i];
                    } else if (legs[i].arrivalTime < earliestArrival.arrivalTime) {
                        earliestArrival = legs[i];
                    }
                }
            }
            if (earliestArrival != null) {
                Connection connection = reconstructConnectionFromLeg(earliestArrival);
                // A connection can be null, even though earliest arrival is not null --> INITIAL leg
                if (connection != null) {
                    isoLines.put(stop.id(), connection);
                }

            }
        }
        return isoLines;
    }

    // this implementation will spawn from source stop until all stops are reached with all pareto optimal connections
    private List<Leg[]> spawnFromSourceStop(int[] sourceStopIdx, int[] departureTime) {
        return spawnFromSourceStop(sourceStopIdx, new int[]{}, departureTime, new int[]{});
    }

    // if targetStopIdx is not empty, then the search will stop when target stop cannot be pareto optimized
    private List<Leg[]> spawnFromSourceStop(int[] sourceStopIdxs, int[] targetStopIdxs, int[] departureTimes,
                                            int[] walkingDurationsToTarget) {
        // initialization
        final int[] earliestArrivals = new int[stops.length];
        Arrays.fill(earliestArrivals, INFINITY);

        if (sourceStopIdxs.length != departureTimes.length) {
            throw new IllegalArgumentException("Source stops and departure times must have the same size.");
        }

        if (targetStopIdxs.length != walkingDurationsToTarget.length) {
            throw new IllegalArgumentException("Target stops and walking durations to target must have the same size.");
        }

        int[] targetStops = new int[targetStopIdxs.length * 2];
        for (int i = 0; i < targetStops.length; i += 2) {
            int index = (int) Math.ceil(i / 2.0);
            targetStops[i] = targetStopIdxs[index];
            targetStops[i + 1] = walkingDurationsToTarget[index];
        }

        final List<Leg[]> earliestArrivalsPerRound = new ArrayList<>();
        earliestArrivalsPerRound.add(new Leg[stops.length]);
        Set<Integer> markedStops = new HashSet<>();

        for (int i = 0; i < sourceStopIdxs.length; i++) {
            // subtract same stop transfer time, as this will be added by default before scanning routes
            earliestArrivals[sourceStopIdxs[i]] = departureTimes[i] - SAME_STOP_TRANSFER_TIME;
            earliestArrivalsPerRound.getFirst()[sourceStopIdxs[i]] = new Leg(0, departureTimes[i], ArrivalType.INITIAL,
                    NO_INDEX, NO_INDEX, sourceStopIdxs[i], null);
            markedStops.add(sourceStopIdxs[i]);
        }

        for (int i = 0; i < sourceStopIdxs.length; i++) {
            expandFootpathsForSourceStop(earliestArrivals, earliestArrivalsPerRound, markedStops, sourceStopIdxs[i],
                    departureTimes[i]);
        }
        int earliestArrival = getEarliestArrivalTime(targetStops, earliestArrivals);

        // continue with further rounds as long as there are new marked stops
        int round = 1;
        while (!markedStops.isEmpty()) {
            log.debug("Scanning routes for round {}", round);
            Set<Integer> markedStopsNext = new HashSet<>();

            // initialize the earliest arrivals for current round
            Leg[] earliestArrivalsLastRound = earliestArrivalsPerRound.get(round - 1);
            earliestArrivalsPerRound.add(new Leg[stops.length]);
            Leg[] earliestArrivalsThisRound = earliestArrivalsPerRound.get(round);

            // get routes of marked stops
            Set<Integer> routesToScan = new HashSet<>();
            for (int stopIdx : markedStops) {
                Stop currentStop = stops[stopIdx];
                int stopRouteIdx = currentStop.stopRouteIdx();
                int stopRouteEndIdx = stopRouteIdx + currentStop.numberOfRoutes();
                while (stopRouteIdx < stopRouteEndIdx) {
                    routesToScan.add(stopRoutes[stopRouteIdx]);
                    stopRouteIdx++;
                }
            }
            log.debug("Routes to scan: {}", routesToScan);

            // scan routes
            for (int currentRouteIdx : routesToScan) {
                Route currentRoute = routes[currentRouteIdx];
                log.debug("Scanning route {}", currentRoute.id());
                final int firstRouteStopIdx = currentRoute.firstRouteStopIdx();
                final int firstStopTimeIdx = currentRoute.firstStopTimeIdx();
                final int numberOfStops = currentRoute.numberOfStops();
                final int numberOfTrips = currentRoute.numberOfTrips();
                int tripOffset = 0;
                boolean enteredTrip = false;
                int tripEntryTime = 0;
                Leg enteredAtArrival = null;

                // iterate over stops in route
                for (int stopOffset = 0; stopOffset < numberOfStops; stopOffset++) {
                    int stopIdx = routeStops[firstRouteStopIdx + stopOffset].stopIndex();
                    Stop stop = stops[stopIdx];
                    int earliestArrivalTime = earliestArrivals[stopIdx];

                    // find first marked stop in route
                    if (!enteredTrip) {
                        if (earliestArrivalTime == INFINITY) {
                            // when current arrival is infinity (Integer.MAX_VALUE), then the stop cannot be reached
                            log.debug("Stop {} cannot be reached, continue", stop.id());
                            continue;
                        }

                        if (!markedStops.contains(stopIdx)) {
                            // this stop has already been scanned in previous round without improved arrival time
                            log.debug("Stop {} was not improved in previous round, continue", stop.id());
                            continue;
                        }

                        if (stopOffset + 1 == numberOfStops) {
                            // last stop in route, does not make sense to check for trip to enter
                            log.debug("Stop {} is last stop in route, continue", stop.id());
                            continue;
                        }

                        // got first marked stop in the route
                        log.debug("Got first entry point at stop {} at {}", stop.id(), earliestArrivalTime);
                        enteredTrip = true;
                    } else {
                        // in this case we are on a trip and need to check if arrival time has improved
                        // get time of arrival on current trip
                        StopTime stopTime = stopTimes[firstStopTimeIdx + tripOffset * numberOfStops + stopOffset];
                        if (stopTime.arrival() < earliestArrivalTime) {
                            log.debug("Stop {} was improved", stop.id());

                            // check if search should be stopped after finding the best time
                            if (stopTime.arrival() >= earliestArrival) {
                                log.debug("Stop {} is not better than best time, continue", stop.id());
                                continue;
                            }

                            // create a route leg
                            earliestArrivals[stopIdx] = stopTime.arrival();
                            earliestArrivalsThisRound[stopIdx] = new Leg(tripEntryTime, stopTime.arrival(),
                                    ArrivalType.ROUTE, currentRouteIdx, tripOffset, stopIdx, enteredAtArrival);
                            // mark stop improvement for next round
                            markedStopsNext.add(stopIdx);
                            // check if this was a target stop
                            if (Arrays.stream(targetStopIdxs).anyMatch(targetStopIdx -> targetStopIdx == stopIdx)) {
                                earliestArrival = getEarliestArrivalTime(targetStops, earliestArrivals);
                                log.debug("Earliest arrival to a target stop improved to {}", earliestArrival);
                            }

                            // earlier trip is not possible
                            continue;
                        } else {
                            log.debug("Stop {} was not improved", stop.id());
                            Leg previous = earliestArrivalsLastRound[stopIdx];
                            if (previous == null || previous.arrivalTime >= stopTime.arrival()) {
                                log.debug(
                                        "Stop {} has been improved in same round, earlier trip not possible within this round",
                                        stop.id());
                                continue;
                            } else {
                                log.debug("Checking for earlier trips at stop {}", stop.id());
                            }
                        }
                    }

                    // find active trip, increase trip offset
                    tripOffset = 0;
                    enteredAtArrival = earliestArrivalsLastRound[stopIdx];

                    int earliestDepartureTime = enteredAtArrival.arrivalTime;
                    if( enteredAtArrival.type != ArrivalType.INITIAL ) {
                        earliestDepartureTime += SAME_STOP_TRANSFER_TIME;
                    }

                    while (tripOffset < numberOfTrips) {
                        StopTime currentStopTime = stopTimes[firstStopTimeIdx + tripOffset * numberOfStops + stopOffset];
                        if (currentStopTime.departure() >= earliestDepartureTime) {
                            log.debug("Found active trip ({}) on route {}", tripOffset, currentRoute.id());
                            tripEntryTime = currentStopTime.departure();
                            break;
                        }
                        if (tripOffset < numberOfTrips - 1) {
                            tripOffset++;
                        } else {
                            // no active trip found
                            log.debug("No active trip found on route {}", currentRoute.id());
                            enteredTrip = false;
                            break;
                        }
                    }
                }
            }

            // relax footpaths for all markedStops
            // temp variable to add any new stops to markedStopsNext
            Set<Integer> newStops = new HashSet<>();
            for (int stopIdx : markedStopsNext) {
                Stop currentStop = stops[stopIdx];
                if (currentStop.numberOfTransfers() == 0) {
                    continue;
                }
                for (int i = currentStop.transferIdx(); i < currentStop.numberOfTransfers(); i++) {
                    Transfer transfer = transfers[i];
                    // TODO: Handle variable SAME_STOP_TRANSFER_TIMEs
                    int newTargetStopArrivalTime = earliestArrivals[stopIdx] + transfer.duration() - SAME_STOP_TRANSFER_TIME;

                    // update improved arrival time
                    if (earliestArrivals[transfer.targetStopIdx()] > newTargetStopArrivalTime) {
                        log.debug("Stop {} was improved by transfer from stop {}", stops[transfer.targetStopIdx()].id(),
                                stops[stopIdx].id());
                        earliestArrivals[transfer.targetStopIdx()] = newTargetStopArrivalTime;
                        earliestArrivalsThisRound[transfer.targetStopIdx()] = new Leg(earliestArrivals[stopIdx],
                                newTargetStopArrivalTime, ArrivalType.TRANSFER, i, NO_INDEX, transfer.targetStopIdx(),
                                earliestArrivalsThisRound[stopIdx]);
                        newStops.add(transfer.targetStopIdx());
                    }
                }
            }
            markedStopsNext.addAll(newStops);

            // prepare next round
            markedStops = markedStopsNext;
            round++;
        }

        return earliestArrivalsPerRound;
    }

    private int getEarliestArrivalTime(int[] targetStops, int[] earliestArrivals) {
        int earliestArrival = INFINITY;
        for (int i = 0; i < targetStops.length; i += 2) {
            int targetStopIdx = targetStops[i];
            int walkDurationToTarget = targetStops[i + 1];
            int earliestArrivalAtTarget = earliestArrivals[targetStopIdx];

            // To Prevent Adding a number to Max Integer Value (resulting in a very small negative number)
            if( earliestArrivalAtTarget == INFINITY ) {
                continue;
            }

            earliestArrival = Math.min(earliestArrival, earliestArrivalAtTarget + walkDurationToTarget);
        }
        return earliestArrival;
    }

    private List<Connection> reconstructParetoOptimalSolutions(List<Leg[]> earliestArrivalsPerRound,
                                                               int[] targetStopIdxs) {
        final List<Connection> connections = new ArrayList<>();

        // iterate over all rounds
        for (Leg[] legs : earliestArrivalsPerRound) {

            Leg leg = null;

            for (int targetStopIdx : targetStopIdxs) {
                if (legs[targetStopIdx] != null) {
                    if (leg == null || legs[targetStopIdx].arrivalTime < leg.arrivalTime) {
                        leg = legs[targetStopIdx];
                    }
                }
            }

            // target stop not reached in this round
            if (leg == null) {
                continue;
            }

            Connection connection = reconstructConnectionFromLeg(leg);
            if (connection != null) {
                connections.add(connection);
            }
        }

        return connections;
    }

    private @Nullable Connection reconstructConnectionFromLeg(Leg leg) {
        Connection connection = new Connection();

        // start from destination leg and follow legs back until the initial leg is reached
        while (leg.type != ArrivalType.INITIAL) {
            String routeId;
            String tripId = null;
            assert leg.previous != null;
            String fromStopId = stops[leg.previous.stopIdx].id();
            String toStopId = stops[leg.stopIdx].id();
            Connection.LegType type;
            int departureTime = leg.departureTime;
            int arrivalTime = leg.arrivalTime;

            if (leg.type == ArrivalType.ROUTE) {
                Route route = routes[leg.routeOrTransferIdx];
                routeId = route.id();
                tripId = route.tripIds()[leg.tripOffset];
                type = Connection.LegType.ROUTE;

            } else if (leg.type == ArrivalType.TRANSFER) {
                routeId = String.format("transfer_%s_%s", fromStopId, toStopId);
                type = Connection.LegType.WALK_TRANSFER;
                // include same stop transfer time (which is subtracted before scanning routes)
                arrivalTime += SAME_STOP_TRANSFER_TIME;

            } else {
                throw new IllegalStateException("Unknown arrival type");
            }

            connection.addLeg(
                    new Connection.Leg(routeId, tripId, fromStopId, toStopId, departureTime, arrivalTime, type));
            leg = leg.previous;
        }

        // initialize connection: Reverse order of legs and add connection
        if (!connection.getLegs().isEmpty()) {
            connection.initialize();
            return connection;
        } else {
            return null;
        }
    }

    private void expandFootpathsForSourceStop(int[] earliestArrivals, List<Leg[]> earliestArrivalsPerRound,
                                              Set<Integer> markedStops, int sourceStopIdx, int departureTime) {
        // if stop has no transfers, then no footpaths can be expanded
        if (stops[sourceStopIdx].numberOfTransfers() == 0) {
            return;
        }

        // mark all transfer stops, no checks needed for since all transfers will improve arrival time and can be
        // marked
        Stop sourceStop = stops[sourceStopIdx];
        for (int i = sourceStop.transferIdx(); i < sourceStop.transferIdx() + sourceStop.numberOfTransfers(); i++) {
            Transfer transfer = transfers[i];
            int newTargetStopArrivalTime = departureTime + transfer.duration() - SAME_STOP_TRANSFER_TIME;
            if (earliestArrivals[transfer.targetStopIdx()] <= newTargetStopArrivalTime) {
                continue;
            }
            earliestArrivals[transfer.targetStopIdx()] = newTargetStopArrivalTime;
            earliestArrivalsPerRound.getFirst()[transfer.targetStopIdx()] = new Leg(departureTime,
                    newTargetStopArrivalTime, ArrivalType.TRANSFER, i, NO_INDEX, transfer.targetStopIdx(),
                    earliestArrivalsPerRound.getFirst()[sourceStopIdx]);
            markedStops.add(transfer.targetStopIdx());
        }
    }

    /**
     * Arrival type of the leg.
     */
    private enum ArrivalType {

        /**
         * First leg in the connection, so there is no previous leg set.
         */
        INITIAL,
        /**
         * A route leg uses a public transit trip in the network.
         */
        ROUTE,
        /**
         * Uses a transfer between station (no same station transfers).
         */
        TRANSFER

    }

    /**
     * A leg is a part of a connection in the same mode (PT or walk).
     *
     * @param departureTime      the departure time of the leg in seconds after midnight.
     * @param arrivalTime        the arrival time of the leg in seconds after midnight.
     * @param type               the type of the leg, can be INITIAL, ROUTE or TRANSFER.
     * @param routeOrTransferIdx the index of the route or of the transfer, see arrival type (or NO_INDEX).
     * @param tripOffset         the trip offset on the current route (or NO_INDEX).
     * @param stopIdx            the arrival stop of the leg.
     * @param previous           the previous leg, null if it is the previous leg.
     */
    private record Leg(int departureTime, int arrivalTime, ArrivalType type, int routeOrTransferIdx, int tripOffset,
                       int stopIdx, @Nullable Leg previous) {
    }

    /**
     * Validate inputs to raptor.
     */
    private class InputValidator {
        private static final int MIN_DEPARTURE_TIME = 0;
        private static final int MAX_DEPARTURE_TIME = 48 * 60 * 60; // 48 hours
        private static final int MIN_WALKING_TIME_TO_TARGET = 0;

        private static void validateStopPermutations(Map<String, Integer> sourceStops,
                                                     Map<String, Integer> targetStops) {
            if (sourceStops.isEmpty()) {
                throw new IllegalArgumentException("At least one source stop must be provided.");
            }
            if (targetStops.isEmpty()) {
                throw new IllegalArgumentException("At least one target stop must be provided.");
            }

            sourceStops.values().forEach(InputValidator::validateDepartureTime);
            targetStops.values().forEach(InputValidator::validateWalkingTimeToTarget);

            // ensure departure and arrival stops are not the same
            Set<String> intersection = new HashSet<>(sourceStops.keySet());
            intersection.retainAll(targetStops.keySet());
            if (!intersection.isEmpty()) {
                throw new IllegalArgumentException("Source and target stop IDs must not be the same.");
            }

        }

        private static void validateDepartureTime(int departureTime) {
            if (departureTime < MIN_DEPARTURE_TIME || departureTime > MAX_DEPARTURE_TIME) {
                throw new IllegalArgumentException(
                        "Departure time must be between " + MIN_DEPARTURE_TIME + " and " + MAX_DEPARTURE_TIME + " seconds.");
            }
        }

        private static void validateWalkingTimeToTarget(int walkingDurationToTarget) {
            if (walkingDurationToTarget < MIN_WALKING_TIME_TO_TARGET) {
                throw new IllegalArgumentException(
                        "Walking duration to target must be greater or equal to " + MIN_WALKING_TIME_TO_TARGET + "seconds.");
            }
        }

        /**
         * Validate the stops provided in the query. This method will check that the map of stop ids and their
         * corresponding departure / walk to target times are valid. This is done by checking if the map is not empty
         * and then checking each entry if the stop id is present in the lookup. If not it is removed from the query. If
         * no valid stops are found an IllegalArgumentException is thrown.
         *
         * @param stops the stops to validate.
         * @return a map of valid stop IDs and their corresponding departure / walk to target times.
         */
        private Map<Integer, Integer> validateStops(Map<String, Integer> stops) {
            if (stops.isEmpty()) {
                throw new IllegalArgumentException("At least one stop ID must be provided.");
            }

            // loop over all stop pairs and check if stop exists in raptor, then validate departure time
            Map<Integer, Integer> validStopIds = new HashMap<>();
            for (Map.Entry<String, Integer> entry : stops.entrySet()) {
                String stopId = entry.getKey();
                int time = entry.getValue();

                if (stopsToIdx.containsKey(stopId)) {
                    validateDepartureTime(time);
                    validStopIds.put(stopsToIdx.get(stopId), time);
                } else {
                    log.warn("Stop ID {} not found in lookup removing from query.", entry.getKey());
                }
            }

            if (validStopIds.isEmpty()) {
                throw new IllegalArgumentException("No valid stops provided.");
            }

            return validStopIds;
        }
    }

}
