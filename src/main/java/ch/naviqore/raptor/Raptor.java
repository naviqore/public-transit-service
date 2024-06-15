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
    private final InputValidator validator = new InputValidator();
    // lookup
    private final Map<String, Integer> stopsToIdx;
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
        this.transfers = stopContext.transfers();
        this.stops = stopContext.stops();
        this.stopRoutes = stopContext.stopRoutes();
        this.stopTimes = routeTraversal.stopTimes();
        this.routes = routeTraversal.routes();
        this.routeStops = routeTraversal.routeStops();
    }

    public static RaptorBuilder builder(int sameStationTransferTime) {
        return new RaptorBuilder(sameStationTransferTime);
    }

    public List<Connection> routeEarliestArrival(String sourceStopId, String targetStopId, int departureTime) {
        return routeEarliestArrival(createStopMap(sourceStopId, departureTime), createStopMap(targetStopId, 0));
    }

    public List<Connection> routeEarliestArrival(String sourceStopId, String targetStopId, int departureTime,
                                                 QueryConfig config) {
        return routeEarliestArrival(createStopMap(sourceStopId, departureTime), createStopMap(targetStopId, 0), config);
    }

    private Map<String, Integer> createStopMap(String stopId, int value) {
        return Map.of(stopId, value);
    }

    public List<Connection> routeEarliestArrival(Map<String, Integer> sourceStops, Map<String, Integer> targetStopIds) {
        return routeEarliestArrival(sourceStops, targetStopIds, new QueryConfig());
    }

    public List<Connection> routeEarliestArrival(Map<String, Integer> sourceStops, Map<String, Integer> targetStopIds,
                                                 QueryConfig config) {
        Map<Integer, Integer> validatedSourceStopIdx = validator.validateStops(sourceStops);
        Map<Integer, Integer> validatedTargetStopIdx = validator.validateStops(targetStopIds);
        InputValidator.validateStopPermutations(sourceStops, targetStopIds);
        int[] sourceStopIdxs = validatedSourceStopIdx.keySet().stream().mapToInt(Integer::intValue).toArray();
        int[] departureTimes = validatedSourceStopIdx.values().stream().mapToInt(Integer::intValue).toArray();
        int[] targetStopIdxs = validatedTargetStopIdx.keySet().stream().mapToInt(Integer::intValue).toArray();
        int[] walkingDurationsToTarget = validatedTargetStopIdx.values().stream().mapToInt(Integer::intValue).toArray();

        log.info("Routing earliest arrival from {} to {} at {}", sourceStopIdxs, targetStopIdxs, departureTimes);
        List<Leg[]> earliestArrivalsPerRound = spawnFromSourceStop(sourceStopIdxs, targetStopIdxs, departureTimes,
                walkingDurationsToTarget, config);

        // get pareto-optimal solutions
        return reconstructParetoOptimalSolutions(earliestArrivalsPerRound, targetStopIdxs);
    }

    public Map<String, Connection> getIsoLines(Map<String, Integer> sourceStops) {
        return getIsoLines(sourceStops, new QueryConfig());
    }

    public Map<String, Connection> getIsoLines(Map<String, Integer> sourceStops, QueryConfig config) {
        Map<Integer, Integer> validatedSourceStopIdx = validator.validateStops(sourceStops);
        int[] sourceStopIdxs = validatedSourceStopIdx.keySet().stream().mapToInt(Integer::intValue).toArray();
        int[] departureTimes = validatedSourceStopIdx.values().stream().mapToInt(Integer::intValue).toArray();
        List<Leg[]> earliestArrivalsPerRound = spawnFromSourceStop(sourceStopIdxs, departureTimes, config);

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
    private List<Leg[]> spawnFromSourceStop(int[] sourceStopIdx, int[] departureTime, QueryConfig config) {
        return spawnFromSourceStop(sourceStopIdx, new int[]{}, departureTime, new int[]{}, config);
    }

    // if targetStopIdx is not empty, then the search will stop when target stop cannot be pareto optimized
    private List<Leg[]> spawnFromSourceStop(int[] sourceStopIdxs, int[] targetStopIdxs, int[] departureTimes,
                                            int[] walkingDurationsToTarget, QueryConfig config) {
        // initialization
        final int[] earliestArrivals = new int[stops.length];
        Arrays.fill(earliestArrivals, INFINITY);

        if (sourceStopIdxs.length != departureTimes.length) {
            throw new IllegalArgumentException("Source stops and departure times must have the same size.");
        }

        if (targetStopIdxs.length != walkingDurationsToTarget.length) {
            throw new IllegalArgumentException("Target stops and walking durations to target must have the same size.");
        }

        // This is used to determine the criteria for maximum travel time
        int earliestDeparture = Arrays.stream(departureTimes).min().orElseThrow();
        int latestAcceptedArrival = config.getMaximumTravelTime() == INFINITY ? INFINITY : earliestDeparture + config.getMaximumTravelTime();

        int maxWalkingDuration = config.getMaximumWalkingDuration();
        int minTransferDuration = config.getMinimumTransferDuration();

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
            earliestArrivals[sourceStopIdxs[i]] = departureTimes[i];
            earliestArrivalsPerRound.getFirst()[sourceStopIdxs[i]] = new Leg(0, departureTimes[i], ArrivalType.INITIAL,
                    NO_INDEX, NO_INDEX, sourceStopIdxs[i], null);
            markedStops.add(sourceStopIdxs[i]);
        }

        for (int sourceStopIdx : sourceStopIdxs) {
            expandFootpathsFromStop(sourceStopIdx, earliestArrivals, earliestArrivalsPerRound, markedStops, 0,
                    maxWalkingDuration, minTransferDuration, 1);
        }
        int earliestArrival = getEarliestArrivalTime(targetStops, earliestArrivals, latestAcceptedArrival);

        // continue with further rounds as long as there are new marked stops
        int round = 1;
        while (!markedStops.isEmpty() && (round - 1) <= config.getMaximumTransferNumber()) {
            log.debug("Scanning routes for round {}", round);
            Set<Integer> markedStopsNext = new HashSet<>();

            // initialize the earliest arrivals for current round
            Leg[] earliestArrivalsLastRound = earliestArrivalsPerRound.get(round - 1);
            earliestArrivalsPerRound.add(new Leg[stops.length]);
            Leg[] earliestArrivalsThisRound = earliestArrivalsPerRound.get(round);

            Set<Integer> routesToScan = getRoutesToScan(markedStops);
            log.debug("Routes to scan: {}", routesToScan);

            // scan routes
            for (int currentRouteIdx : routesToScan) {
                scanRouteForward(currentRouteIdx, earliestArrivals, earliestArrivalsLastRound,
                        earliestArrivalsThisRound, markedStops, markedStopsNext, earliestArrival, minTransferDuration);
                earliestArrival = getEarliestArrivalTime(targetStops, earliestArrivals, latestAcceptedArrival);
            }

            // relax footpaths for all markedStops
            // temp variable to add any new stops to markedStopsNext
            Set<Integer> newStops = new HashSet<>();
            for (int stopIdx : markedStopsNext) {
                expandFootpathsFromStop(stopIdx, earliestArrivals, earliestArrivalsPerRound, newStops, round,
                        maxWalkingDuration, minTransferDuration, 1);
            }
            markedStopsNext.addAll(newStops);

            // prepare next round
            markedStops = markedStopsNext;
            round++;
        }

        return earliestArrivalsPerRound;
    }

    /**
     * Scan a route in forward direction to find the earliest arrival times for each stop on route for given round.
     *
     * @param currentRouteIdx           - The index of the current route.
     * @param earliestArrivals          - The earliest arrival time for each stop.
     * @param earliestArrivalsLastRound - The earliest arrival leg for each stop in the last round.
     * @param earliestArrivalsThisRound - The earliest arrival leg for each stop in the current round.
     * @param markedStops               - The set of marked stops from the previous round.
     * @param markedStopsNext           - The set of marked stops for the next round.
     * @param earliestArrival           - The earliest arrival time at the target stops.
     * @param minTransferDuration       - The minimum transfer duration time.
     */
    private void scanRouteForward(int currentRouteIdx, int[] earliestArrivals, Leg[] earliestArrivalsLastRound,
                                  Leg[] earliestArrivalsThisRound, Set<Integer> markedStops,
                                  Set<Integer> markedStopsNext, int earliestArrival, int minTransferDuration) {
        Route currentRoute = routes[currentRouteIdx];
        log.debug("Scanning route {}", currentRoute.id());
        final int firstRouteStopIdx = currentRoute.firstRouteStopIdx();
        final int firstStopTimeIdx = currentRoute.firstStopTimeIdx();
        final int numberOfStops = currentRoute.numberOfStops();

        ActiveTrip activeTrip = null;

        // iterate over stops in route
        for (int stopOffset = 0; stopOffset < numberOfStops; stopOffset++) {
            int stopIdx = routeStops[firstRouteStopIdx + stopOffset].stopIndex();
            Stop stop = stops[stopIdx];
            int earliestStopArrival = earliestArrivals[stopIdx];

            // find first marked stop in route
            if (activeTrip == null) {
                if (!canEnterAtStop(stop, earliestStopArrival, markedStops, stopIdx, stopOffset, numberOfStops)) {
                    continue;
                }
            } else {
                // in this case we are on a trip and need to check if arrival time has improved
                // get time of arrival on current trip
                StopTime stopTime = stopTimes[firstStopTimeIdx + activeTrip.tripOffset * numberOfStops + stopOffset];
                if (!checkIfEarlierTripIsPossibleAndUpdateMarks(stopTime, activeTrip, stop, earliestStopArrival,
                        earliestArrival, earliestArrivals, stopIdx, earliestArrivalsThisRound,
                        earliestArrivalsLastRound, markedStopsNext, currentRouteIdx)) {
                    continue;
                }
            }
            activeTrip = findFirstPossibleTrip(stopIdx, stop, stopOffset, currentRoute, earliestArrivalsLastRound,
                    minTransferDuration);
        }
    }

    /**
     * This method checks if a trip can be entered at the stop in the current round. A trip can be entered if the stop
     * was reached in a previous round (earliestStopArrival != INFINITY), the stop is the last stop of a trip or (for
     * performance reasons) assuming that this check is only run when not travelling with an active trip, the stop was
     * not marked in a previous round (i.e., the lasts round trip query would be repeated).
     *
     * @param stop                - The stop to check if a trip can be entered.
     * @param earliestStopArrival - The earliest arrival time at the stop.
     * @param markedStops         - The set of marked stops from the previous round.
     * @param stopIdx             - The index of the stop to check if a trip can be entered.
     * @param stopOffset          - The offset of the stop in the route.
     * @param numberOfStops       - The number of stops in the route.
     */
    private boolean canEnterAtStop(Stop stop, int earliestStopArrival, Set<Integer> markedStops, int stopIdx,
                                   int stopOffset, int numberOfStops) {
        if (earliestStopArrival == INFINITY) {
            // when current arrival is infinity (Integer.MAX_VALUE), then the stop cannot be reached
            log.debug("Stop {} cannot be reached, continue", stop.id());
            return false;
        }

        if (!markedStops.contains(stopIdx)) {
            // this stop has already been scanned in previous round without improved arrival time
            log.debug("Stop {} was not improved in previous round, continue", stop.id());
            return false;
        }

        if (stopOffset + 1 == numberOfStops) {
            // last stop in route, does not make sense to check for trip to enter
            log.debug("Stop {} is last stop in route, continue", stop.id());
            return false;
        }

        // got first marked stop in the route
        log.debug("Got first entry point at stop {} at {}", stop.id(), earliestStopArrival);

        return true;
    }

    /**
     * <p>This method checks if the arrival time at a stop can be improved by arriving with the active trip, if so
     * the stop is marked for the next round and the arrival time is updated. If the arrival time is improved it is
     * clear that an earlier trip is not possible and the method returns false.</p>
     * <p>If the arrival time was not improved, an additional check will be needed to figure out if an earlier trip
     * from the stop is possible within the current round, thus the method returns true.</p>
     *
     * @param stopTime                  - The stop time to check for an earlier trip.
     * @param activeTrip                - The active trip to check for an earlier trip.
     * @param stop                      - The stop to check for an earlier trip.
     * @param earliestStopArrival       - The earliest arrival time at the stop.
     * @param earliestArrival           - The earliest arrival time at the target stops.
     * @param earliestArrivals          - The earliest arrival time for each stop.
     * @param stopIdx                   - The index of the stop to check for an earlier trip.
     * @param earliestArrivalsThisRound - The earliest arrival time for each stop in the current round.
     * @param earliestArrivalsLastRound - The earliest arrival time for each stop in the last round.
     * @param markedStopsNext           - The set of marked stops for the next round.
     * @param currentRouteIdx           - The index of the current route.
     * @return true if an earlier trip is possible, false otherwise.
     */
    private boolean checkIfEarlierTripIsPossibleAndUpdateMarks(StopTime stopTime, ActiveTrip activeTrip, Stop stop,
                                                               int earliestStopArrival, int earliestArrival,
                                                               int[] earliestArrivals, int stopIdx,
                                                               Leg[] earliestArrivalsThisRound,
                                                               Leg[] earliestArrivalsLastRound,
                                                               Set<Integer> markedStopsNext, int currentRouteIdx) {

        if (stopTime.arrival() < earliestStopArrival) {
            log.debug("Stop {} was improved", stop.id());

            // check if search should be stopped after finding the best time
            if (stopTime.arrival() >= earliestArrival) {
                log.debug("Stop {} is not better than best time, continue", stop.id());
                return false;
            }

            // create a route leg
            earliestArrivals[stopIdx] = stopTime.arrival();
            earliestArrivalsThisRound[stopIdx] = new Leg(activeTrip.entryTime(), stopTime.arrival(), ArrivalType.ROUTE,
                    currentRouteIdx, activeTrip.tripOffset, stopIdx, activeTrip.previousLeg);
            // mark stop improvement for next round
            markedStopsNext.add(stopIdx);

            // earlier trip is not possible
            return false;
        } else {
            log.debug("Stop {} was not improved", stop.id());
            Leg previous = earliestArrivalsLastRound[stopIdx];
            if (previous == null || previous.arrivalTime >= stopTime.arrival()) {
                log.debug("Stop {} has been improved in same round, earlier trip not possible within this round",
                        stop.id());
                return false;
            } else {
                log.debug("Checking for earlier trips at stop {}", stop.id());
                return true;
            }
        }
    }

    /**
     * Find the first possible trip on the route. This loops through all trips departing from a given stop for a given
     * route and returns details about the first trip that can be taken (departing after the arrival time of the
     * previous round at this stop and accounting for transfer constraints).
     *
     * @param stopIdx                   - The index of the stop to find the first possible trip from.
     * @param stop                      - The stop to find the first possible trip from.
     * @param stopOffset                - The offset of the stop in the route.
     * @param route                     - The route to find the first possible trip on.
     * @param earliestArrivalsLastRound - The earliest arrival time for each stop in the last round.
     * @param minTransferDuration       - The minimum transfer duration time, since this is intended as rest period it
     *                                  is added to the walk time.
     */
    private @Nullable ActiveTrip findFirstPossibleTrip(int stopIdx, Stop stop, int stopOffset, Route route,
                                                       Leg[] earliestArrivalsLastRound, int minTransferDuration) {

        int firstStopTimeIdx = route.firstStopTimeIdx();
        int numberOfStops = route.numberOfStops();
        int numberOfTrips = route.numberOfTrips();

        int tripOffset = 0;
        int entryTime = 0;
        Leg previousLeg = earliestArrivalsLastRound[stopIdx];

        int earliestDepartureTime = previousLeg.arrivalTime;
        if (previousLeg.type == ArrivalType.ROUTE) {
            earliestDepartureTime += Math.max(stop.sameStationTransferTime(), minTransferDuration);
        }

        while (tripOffset < numberOfTrips) {
            StopTime currentStopTime = stopTimes[firstStopTimeIdx + tripOffset * numberOfStops + stopOffset];
            if (currentStopTime.departure() >= earliestDepartureTime) {
                log.debug("Found active trip ({}) on route {}", tripOffset, route.id());
                entryTime = currentStopTime.departure();
                break;
            }
            if (tripOffset < numberOfTrips - 1) {
                tripOffset++;
            } else {
                // no active trip found
                log.debug("No active trip found on route {}", route.id());
                return null;
            }
        }

        return new ActiveTrip(tripOffset, entryTime, previousLeg);

    }

    /**
     * Get all routes to scan from the marked stops.
     *
     * @param markedStops - The set of marked stops from the previous round.
     */
    private Set<Integer> getRoutesToScan(Set<Integer> markedStops) {
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
        return routesToScan;
    }

    /**
     * Get the earliest arrival time at the target stops.
     *
     * @param targetStops           - The target stops to reach.
     * @param earliestArrivals      - The earliest arrival time for each stop.
     * @param latestAcceptedArrival - The latest accepted arrival time.
     */
    private int getEarliestArrivalTime(int[] targetStops, int[] earliestArrivals, int latestAcceptedArrival) {
        int earliestArrival = latestAcceptedArrival;
        for (int i = 0; i < targetStops.length; i += 2) {
            int targetStopIdx = targetStops[i];
            int walkDurationToTarget = targetStops[i + 1];
            int earliestArrivalAtTarget = earliestArrivals[targetStopIdx];

            // To Prevent Adding a number to Max Integer Value (resulting in a very small negative number)
            if (earliestArrivalAtTarget == INFINITY) {
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

    /**
     * Expands all transfers between stops from a given stop. If a transfer improves the arrival time at the target
     * stop, then the target stop is marked for the next round. And the improved arrival time is stored in the
     * earliestArrivals array and the earliestArrivalsPerRound list (including the new Transfer Leg).
     *
     * @param stopIdx                  - The index of the stop to expand transfers from.
     * @param earliestArrivals         - A array with the overall best arrival time for each stop, indexed by stop
     *                                 index. Note: The arrival time is reduced by the same station transfer time for
     *                                 transfers, to make them comparable with route arrivals.
     * @param earliestArrivalsPerRound - A list of arrays with the best arrival time for each stop per round, indexed by
     *                                 round.
     * @param markedStops              - A set of stop indices that have been marked for scanning in the next round.
     * @param round                    - The current round to relax footpaths for.
     * @param maxWalkingDuration       - The maximum walking duration to reach the target stop. If the walking duration
     *                                 exceeds this value, the target stop is not reached.
     * @param minTransferDuration      - The minimum transfer duration time, since this is intended as rest period it is
     *                                 added to the walk time.
     * @param timeFactor               - The time factor to determine if the search is for earliest arrival (1) or
     *                                 latest departure (-1).
     */
    private void expandFootpathsFromStop(int stopIdx, int[] earliestArrivals, List<Leg[]> earliestArrivalsPerRound,
                                         Set<Integer> markedStops, int round, int maxWalkingDuration,
                                         int minTransferDuration, int timeFactor) {
        // if stop has no transfers, then no footpaths can be expanded
        if (stops[stopIdx].numberOfTransfers() == 0) {
            return;
        }
        Stop sourceStop = stops[stopIdx];
        Leg previousLeg = earliestArrivalsPerRound.get(round)[stopIdx];

        // do not relax footpath from stop that was only reached by footpath in the same round
        if (previousLeg == null || previousLeg.type == ArrivalType.TRANSFER) {
            return;
        }

        int arrivalTime = timeFactor * previousLeg.arrivalTime();

        for (int i = sourceStop.transferIdx(); i < sourceStop.transferIdx() + sourceStop.numberOfTransfers(); i++) {
            Transfer transfer = transfers[i];
            Stop targetStop = stops[transfer.targetStopIdx()];
            int duration = transfer.duration();
            if (maxWalkingDuration < duration) {
                continue;
            }
            int newTargetStopArrivalTime = arrivalTime + transfer.duration() + minTransferDuration;

            // For Comparison with Route Arrivals the Arrival Time by Transfer must be reduced by the same stop transfer time
            int comparableNewTargetStopArrivalTime = newTargetStopArrivalTime - targetStop.sameStationTransferTime();
            if (earliestArrivals[transfer.targetStopIdx()] <= comparableNewTargetStopArrivalTime) {
                continue;
            }

            log.debug("Stop {} was improved by transfer from stop {}", targetStop.id(), sourceStop.id());

            earliestArrivals[transfer.targetStopIdx()] = comparableNewTargetStopArrivalTime;
            earliestArrivalsPerRound.get(round)[transfer.targetStopIdx()] = new Leg(timeFactor * arrivalTime,
                    timeFactor * newTargetStopArrivalTime, ArrivalType.TRANSFER, i, NO_INDEX, transfer.targetStopIdx(),
                    earliestArrivalsPerRound.get(round)[stopIdx]);
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

    private record ActiveTrip(int tripOffset, int entryTime, Leg previousLeg) {
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
            if (stops == null) {
                throw new IllegalArgumentException("Stops must not be null.");
            }
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
