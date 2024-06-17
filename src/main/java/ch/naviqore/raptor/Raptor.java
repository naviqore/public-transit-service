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

    public static RaptorBuilder builder(int sameStopTransferTime) {
        return new RaptorBuilder(sameStopTransferTime);
    }

    private static int getBestTimeForStop(int stopIdx, List<Leg[]> bestLegForStopsPerRound, TimeType timeType) {
        int timeFactor = timeType == TimeType.DEPARTURE ? 1 : -1;
        int bestTime = timeFactor * INFINITY;
        for (Leg[] legs : bestLegForStopsPerRound) {
            if (legs[stopIdx] == null) {
                continue;
            }
            Leg currentLeg = legs[stopIdx];
            if (timeType == TimeType.DEPARTURE) {
                if (currentLeg.arrivalTime < bestTime) {
                    bestTime = currentLeg.arrivalTime;
                }
            } else {
                if (currentLeg.arrivalTime > bestTime) {
                    bestTime = currentLeg.arrivalTime;
                }
            }
        }

        return bestTime;
    }

    public List<Connection> route(Map<String, Integer> sourceStops, Map<String, Integer> targetStops, TimeType timeType,
                                  QueryConfig config) {
        Map<Integer, Integer> validatedSourceStopIdx = validator.validateStops(sourceStops);
        Map<Integer, Integer> validatedTargetStopIdx = validator.validateStops(targetStops);

        int[] sourceStopIdxs;
        int[] departureTimes;
        int[] targetStopIdxs;
        int[] walkingDurationsToTarget;

        if (timeType == TimeType.DEPARTURE) {
            InputValidator.validateStopPermutations(sourceStops, targetStops);
            sourceStopIdxs = validatedSourceStopIdx.keySet().stream().mapToInt(Integer::intValue).toArray();
            departureTimes = validatedSourceStopIdx.values().stream().mapToInt(Integer::intValue).toArray();
            targetStopIdxs = validatedTargetStopIdx.keySet().stream().mapToInt(Integer::intValue).toArray();
            walkingDurationsToTarget = validatedTargetStopIdx.values().stream().mapToInt(Integer::intValue).toArray();

            log.info("Routing earliest arrival from {} to {} departing at {}", sourceStopIdxs, targetStopIdxs,
                    departureTimes);
        } else {
            // has to be flipped because source stops only contain walking info and target stops contain arrival times
            InputValidator.validateStopPermutations(targetStops, sourceStops);
            sourceStopIdxs = validatedTargetStopIdx.keySet().stream().mapToInt(Integer::intValue).toArray();
            departureTimes = validatedTargetStopIdx.values().stream().mapToInt(Integer::intValue).toArray();
            targetStopIdxs = validatedSourceStopIdx.keySet().stream().mapToInt(Integer::intValue).toArray();
            walkingDurationsToTarget = validatedSourceStopIdx.values().stream().mapToInt(Integer::intValue).toArray();

            log.info("Routing latest departure from {} to {} arriving at {}", targetStopIdxs, sourceStopIdxs,
                    departureTimes);
        }

        List<Leg[]> earliestArrivalsPerRound = spawnFromStop(sourceStopIdxs, targetStopIdxs, departureTimes,
                walkingDurationsToTarget, config, timeType);

        // get pareto-optimal solutions
        if (timeType == TimeType.DEPARTURE) {
            return reconstructParetoOptimalSolutions(earliestArrivalsPerRound, validatedTargetStopIdx, timeType);
        } else {
            return reconstructParetoOptimalSolutions(earliestArrivalsPerRound, validatedSourceStopIdx, timeType);
        }
    }

    public Map<String, Connection> getIsoLines(Map<String, Integer> sourceStops, TimeType timeType,
                                               QueryConfig config) {
        Map<Integer, Integer> validatedSourceStopIdx = validator.validateStops(sourceStops);
        int[] sourceStopIdxs = validatedSourceStopIdx.keySet().stream().mapToInt(Integer::intValue).toArray();
        int[] refStopTimes = validatedSourceStopIdx.values().stream().mapToInt(Integer::intValue).toArray();

        List<Leg[]> earliestArrivalsPerRound = spawnFromStop(sourceStopIdxs, new int[]{}, refStopTimes, new int[]{},
                config, timeType);

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

    // if targetStopIdx is not empty, then the search will stop when target stop cannot be pareto optimized
    private List<Leg[]> spawnFromStop(int[] sourceStopIdxs, int[] targetStopIdxs, int[] sourceTimes,
                                      int[] walkingDurationsToTarget, QueryConfig config, TimeType timeType) {
        // initialization
        final int[] bestTimeForStops = new int[stops.length];
        Arrays.fill(bestTimeForStops, timeType == TimeType.DEPARTURE ? INFINITY : -INFINITY);

        if (sourceStopIdxs.length != sourceTimes.length) {
            throw new IllegalArgumentException("Source stops and departure/arrival times must have the same size.");
        }

        if (targetStopIdxs.length != walkingDurationsToTarget.length) {
            throw new IllegalArgumentException("Target stops and walking durations to target must have the same size.");
        }

        // This is used to determine the criteria for maximum travel time
        int cutOffTime;
        if (config.getMaximumTravelTime() == INFINITY) {
            cutOffTime = timeType == TimeType.DEPARTURE ? INFINITY : -INFINITY;
        } else if (timeType == TimeType.DEPARTURE) {
            int earliestDeparture = Arrays.stream(sourceTimes).min().orElseThrow();
            cutOffTime = earliestDeparture + config.getMaximumTravelTime();
        } else {
            int latestArrival = Arrays.stream(sourceTimes).max().orElseThrow();
            cutOffTime = latestArrival - config.getMaximumTravelTime();
        }

        int maxWalkingDuration = config.getMaximumWalkingDuration();
        int minTransferDuration = config.getMinimumTransferDuration();

        int[] targetStops = new int[targetStopIdxs.length * 2];
        for (int i = 0; i < targetStops.length; i += 2) {
            int index = (int) Math.ceil(i / 2.0);
            targetStops[i] = targetStopIdxs[index];
            targetStops[i + 1] = walkingDurationsToTarget[index];
        }

        final List<Leg[]> bestStopLegsPerRound = new ArrayList<>();
        bestStopLegsPerRound.add(new Leg[stops.length]);
        Set<Integer> markedStops = new HashSet<>();

        for (int i = 0; i < sourceStopIdxs.length; i++) {
            bestTimeForStops[sourceStopIdxs[i]] = sourceTimes[i];
            bestStopLegsPerRound.getFirst()[sourceStopIdxs[i]] = new Leg(0, sourceTimes[i], ArrivalType.INITIAL,
                    NO_INDEX, NO_INDEX, sourceStopIdxs[i], null);
            markedStops.add(sourceStopIdxs[i]);
        }

        for (int sourceStopIdx : sourceStopIdxs) {
            expandFootpathsFromStop(sourceStopIdx, bestTimeForStops, bestStopLegsPerRound, markedStops, 0,
                    maxWalkingDuration, minTransferDuration, timeType);
        }

        int bestTime = getBestTime(targetStops, bestStopLegsPerRound, cutOffTime, timeType);
        markedStops = removeSubOptimalLegsForRound(bestTime, 0, timeType, bestStopLegsPerRound, markedStops);

        // continue with further rounds as long as there are new marked stops
        int round = 1;
        while (!markedStops.isEmpty() && (round - 1) <= config.getMaximumTransferNumber()) {
            log.debug("Scanning routes for round {}", round);
            Set<Integer> markedStopsNext = new HashSet<>();

            Leg[] bestTimeLegsLastRound = bestStopLegsPerRound.get(round - 1);
            bestStopLegsPerRound.add(new Leg[stops.length]);
            Leg[] bestTimeLegsThisRound = bestStopLegsPerRound.get(round);

            Set<Integer> routesToScan = getRoutesToScan(markedStops);
            log.debug("Routes to scan: {}", routesToScan);

            // scan routes
            for (int currentRouteIdx : routesToScan) {
                if (timeType == TimeType.DEPARTURE) {
                    scanRouteForward(currentRouteIdx, bestTimeForStops, bestTimeLegsLastRound, bestTimeLegsThisRound,
                            markedStops, markedStopsNext, minTransferDuration);
                } else {
                    scanRouteBackward(currentRouteIdx, bestTimeForStops, bestTimeLegsLastRound, bestTimeLegsThisRound,
                            markedStops, markedStopsNext, minTransferDuration);
                }
            }

            // relax footpaths for all markedStops
            // temp variable to add any new stops to markedStopsNext
            Set<Integer> newStops = new HashSet<>();
            for (int stopIdx : markedStopsNext) {
                expandFootpathsFromStop(stopIdx, bestTimeForStops, bestStopLegsPerRound, newStops, round,
                        maxWalkingDuration, minTransferDuration, timeType);
            }
            markedStopsNext.addAll(newStops);

            // prepare next round
            bestTime = getBestTime(targetStops, bestStopLegsPerRound, cutOffTime, timeType);
            markedStops = removeSubOptimalLegsForRound(bestTime, round, timeType, bestStopLegsPerRound,
                    markedStopsNext);
            round++;
        }

        return bestStopLegsPerRound;
    }

    /**
     * Nullify legs that are suboptimal for the current round. This method checks if the leg arrival time is worse than
     * the optimal time mark and removes the mark for the next round and nullifies the leg in this case.
     *
     * @param bestTime             - The best time for the current round.
     * @param round                - The round to remove suboptimal legs for.
     * @param timeType             - The time type to check for.
     * @param bestTimeLegsPerRound - The best time legs per round.
     * @param markedStops          - The marked stops to check for suboptimal legs.
     */
    private Set<Integer> removeSubOptimalLegsForRound(int bestTime, int round, TimeType timeType,
                                                      List<Leg[]> bestTimeLegsPerRound, Set<Integer> markedStops) {
        if (bestTime == INFINITY || bestTime == -INFINITY) {
            return markedStops;
        }
        Leg[] bestTimeLegsThisRound = bestTimeLegsPerRound.get(round);
        Set<Integer> markedStopsClean = new HashSet<>();
        for (int stopIdx : markedStops) {
            if (bestTimeLegsThisRound[stopIdx] != null) {
                if (timeType == TimeType.DEPARTURE && bestTimeLegsThisRound[stopIdx].arrivalTime > bestTime) {
                    bestTimeLegsThisRound[stopIdx] = null;
                } else if (timeType == TimeType.ARRIVAL && bestTimeLegsThisRound[stopIdx].arrivalTime < bestTime) {
                    bestTimeLegsThisRound[stopIdx] = null;
                } else {
                    markedStopsClean.add(stopIdx);
                }
            }
        }
        return markedStopsClean;
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
     * @param minTransferDuration       - The minimum transfer duration time.
     */
    private void scanRouteForward(int currentRouteIdx, int[] earliestArrivals, Leg[] earliestArrivalsLastRound,
                                  Leg[] earliestArrivalsThisRound, Set<Integer> markedStops,
                                  Set<Integer> markedStopsNext, int minTransferDuration) {
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
                if (!canEnterAtStop(stop, earliestStopArrival, markedStops, stopIdx, stopOffset, numberOfStops,
                        TimeType.DEPARTURE)) {
                    continue;
                }
            } else {
                // in this case we are on a trip and need to check if arrival time has improved
                // get time of arrival on current trip
                StopTime stopTime = stopTimes[firstStopTimeIdx + activeTrip.tripOffset * numberOfStops + stopOffset];
                if (!checkIfEarlierTripIsPossibleAndUpdateMarks(stopTime, activeTrip, stop, earliestStopArrival,
                        earliestArrivals, stopIdx, earliestArrivalsThisRound, earliestArrivalsLastRound,
                        markedStopsNext, currentRouteIdx)) {
                    continue;
                }
            }
            activeTrip = findFirstPossibleTrip(stopIdx, stop, stopOffset, currentRoute, earliestArrivalsLastRound,
                    minTransferDuration);
        }
    }

    /**
     * Scan a route in backward direction to find the latest departure times for each stop on route for given round.
     *
     * @param currentRouteIdx           - The index of the current route.
     * @param latestDepartures          - The latest departure time for each stop.
     * @param latestDeparturesLastRound - The latest departure leg for each stop in the last round.
     * @param latestDeparturesThisRound - The latest departure leg for each stop in the current round.
     * @param markedStops               - The set of marked stops from the previous round.
     * @param markedStopsNext           - The set of marked stops for the next round.
     * @param minTransferDuration       - The minimum transfer duration time.
     */
    private void scanRouteBackward(int currentRouteIdx, int[] latestDepartures, Leg[] latestDeparturesLastRound,
                                   Leg[] latestDeparturesThisRound, Set<Integer> markedStops,
                                   Set<Integer> markedStopsNext, int minTransferDuration) {
        Route currentRoute = routes[currentRouteIdx];
        log.debug("Scanning route {} backwards", currentRoute.id());
        final int firstRouteStopIdx = currentRoute.firstRouteStopIdx();
        final int firstStopTimeIdx = currentRoute.firstStopTimeIdx();
        final int numberOfStops = currentRoute.numberOfStops();

        ActiveTrip activeTrip = null;

        // iterate over stops in route
        for (int stopOffset = numberOfStops - 1; stopOffset >= 0; stopOffset--) {
            int stopIdx = routeStops[firstRouteStopIdx + stopOffset].stopIndex();
            Stop stop = stops[stopIdx];
            int latestStopDeparture = latestDepartures[stopIdx];

            // find first marked stop in route
            if (activeTrip == null) {
                if (!canEnterAtStop(stop, latestStopDeparture, markedStops, stopIdx, stopOffset, numberOfStops,
                        TimeType.ARRIVAL)) {
                    continue;
                }
            } else {
                // in this case we are on a trip and need to check if arrival time has improved
                // get time of arrival on current trip
                StopTime stopTime = stopTimes[firstStopTimeIdx + activeTrip.tripOffset * numberOfStops + stopOffset];
                if (!checkIfLaterTripIsPossibleAndUpdateMarks(stopTime, activeTrip, stop, latestStopDeparture,
                        latestDepartures, stopIdx, latestDeparturesThisRound, latestDeparturesLastRound,
                        markedStopsNext, currentRouteIdx)) {
                    continue;
                }
            }
            activeTrip = findLatestPossibleTrip(stopIdx, stop, stopOffset, currentRoute, latestDeparturesLastRound,
                    minTransferDuration);
        }

    }

    /**
     * This method checks if a trip can be entered at the stop in the current round. A trip can be entered if the stop
     * was reached in a previous round, and is not the first (arrivalTime) / last (departureTime) stop of a trip or (for
     * performance reasons) assuming that this check is only run when not travelling with an active trip, the stop was
     * not marked in a previous round (i.e., the lasts round trip query would be repeated).
     *
     * @param stop          - The stop to check if a trip can be entered.
     * @param stopTime      - The time at the stop.
     * @param markedStops   - The set of marked stops from the previous round.
     * @param stopIdx       - The index of the stop to check if a trip can be entered.
     * @param stopOffset    - The offset of the stop in the route.
     * @param numberOfStops - The number of stops in the route.
     * @param timeType      - The type of time to check for (arrival or departure).
     */
    private boolean canEnterAtStop(Stop stop, int stopTime, Set<Integer> markedStops, int stopIdx, int stopOffset,
                                   int numberOfStops, TimeType timeType) {

        int unreachableValue = timeType == TimeType.DEPARTURE ? INFINITY : -INFINITY;
        if (stopTime == unreachableValue) {
            log.debug("Stop {} cannot be reached, continue", stop.id());
            return false;
        }

        if (!markedStops.contains(stopIdx)) {
            // this stop has already been scanned in previous round without improved arrival time
            log.debug("Stop {} was not improved in previous round, continue", stop.id());
            return false;
        }

        if (timeType == TimeType.DEPARTURE && (stopOffset + 1 == numberOfStops)) {
            // last stop in route, does not make sense to check for trip to enter
            log.debug("Stop {} is last stop in route, continue", stop.id());
            return false;
        } else if (timeType == TimeType.ARRIVAL && (stopOffset == 0)) {
            // first stop in route, does not make sense to check for trip to enter
            log.debug("Stop {} is first stop in route, continue", stop.id());
            return false;
        }

        // got first marked stop in the route
        log.debug("Got first entry point at stop {} at {}", stop.id(), stopTime);

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
     * @param earliestArrivals          - The earliest arrival time for each stop.
     * @param stopIdx                   - The index of the stop to check for an earlier trip.
     * @param earliestArrivalsThisRound - The earliest arrival time for each stop in the current round.
     * @param earliestArrivalsLastRound - The earliest arrival time for each stop in the last round.
     * @param markedStopsNext           - The set of marked stops for the next round.
     * @param currentRouteIdx           - The index of the current route.
     * @return true if an earlier trip is possible, false otherwise.
     */
    private boolean checkIfEarlierTripIsPossibleAndUpdateMarks(StopTime stopTime, ActiveTrip activeTrip, Stop stop,
                                                               int earliestStopArrival, int[] earliestArrivals,
                                                               int stopIdx, Leg[] earliestArrivalsThisRound,
                                                               Leg[] earliestArrivalsLastRound,
                                                               Set<Integer> markedStopsNext, int currentRouteIdx) {

        if (stopTime.arrival() < earliestStopArrival) {
            log.debug("Stop {} was improved", stop.id());
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

    private boolean checkIfLaterTripIsPossibleAndUpdateMarks(StopTime stopTime, ActiveTrip activeTrip, Stop stop,
                                                             int latestStopDeparture, int[] latestDepartures,
                                                             int stopIdx, Leg[] latestDeparturesThisRound,
                                                             Leg[] latestDeparturesLastRound,
                                                             Set<Integer> markedStopsNext, int currentRouteIdx) {

        if (stopTime.departure() > latestStopDeparture) {
            log.debug("Stop {} was improved", stop.id());
            latestDepartures[stopIdx] = stopTime.departure();
            latestDeparturesThisRound[stopIdx] = new Leg(activeTrip.entryTime(), stopTime.departure(),
                    ArrivalType.ROUTE, currentRouteIdx, activeTrip.tripOffset, stopIdx, activeTrip.previousLeg);
            markedStopsNext.add(stopIdx);
            return false;
        } else {
            log.debug("Stop {} was not improved", stop.id());
            Leg previous = latestDeparturesLastRound[stopIdx];
            if (previous == null || previous.arrivalTime <= stopTime.departure()) {
                log.debug("Stop {} has been improved in same round, later trip not possible within this round",
                        stop.id());
                return false;
            } else {
                log.debug("Checking for later trips at stop {}", stop.id());
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
            earliestDepartureTime += Math.max(stop.sameStopTransferTime(), minTransferDuration);
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
     * Find the latest possible trip on the route. This loops through all trips arriving at a given stop for a given
     * route and returns details about the latest trip that can be taken (arriving before the departure time of the
     * previous round at this stop and accounting for transfer constraints).
     *
     * @param stopIdx                   - The index of the stop to find the first possible trip from.
     * @param stop                      - The stop to find the first possible trip from.
     * @param stopOffset                - The offset of the stop in the route.
     * @param route                     - The route to find the first possible trip on.
     * @param latestDeparturesLastRound - The earliest arrival time for each stop in the last round.
     * @param minTransferDuration       - The minimum transfer duration time, since this is intended as rest period it
     *                                  is added to the walk time.
     */
    private @Nullable ActiveTrip findLatestPossibleTrip(int stopIdx, Stop stop, int stopOffset, Route route,
                                                        Leg[] latestDeparturesLastRound, int minTransferDuration) {
        int firstStopTimeIdx = route.firstStopTimeIdx();
        int numberOfStops = route.numberOfStops();
        int numberOfTrips = route.numberOfTrips();

        int tripOffset = numberOfTrips - 1;
        int entryTime = 0;
        Leg previousLeg = latestDeparturesLastRound[stopIdx];

        int latestArrivalTime = previousLeg.arrivalTime;
        if (previousLeg.type == ArrivalType.ROUTE) {
            latestArrivalTime -= Math.max(stop.sameStopTransferTime(), minTransferDuration);
        }

        while (tripOffset >= 0) {
            StopTime currentStopTime = stopTimes[firstStopTimeIdx + tripOffset * numberOfStops + stopOffset];
            if (currentStopTime.arrival() <= latestArrivalTime) {
                log.debug("Found active trip ({}) on route {}", tripOffset, route.id());
                entryTime = currentStopTime.arrival();
                break;
            }
            if (tripOffset > 0) {
                tripOffset--;
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
     * Get the best time for the target stops. The best time is the earliest arrival time for each stop if the time type
     * is departure, and the latest arrival time for each stop if the time type is arrival.
     *
     * @param targetStops             - The target stops to reach.
     * @param bestLegForStopsPerRound - The collection of all best legs for each stop in each round.
     * @param cutOffValue             - The latest accepted arrival time.
     */
    private int getBestTime(int[] targetStops, List<Leg[]> bestLegForStopsPerRound, int cutOffValue,
                            TimeType timeType) {
        int bestTime = cutOffValue;
        for (int i = 0; i < targetStops.length; i += 2) {
            int targetStopIdx = targetStops[i];
            int walkDurationToTarget = targetStops[i + 1];
            int bestTimeForStop = getBestTimeForStop(targetStopIdx, bestLegForStopsPerRound, timeType);

            if (timeType == TimeType.DEPARTURE && bestTimeForStop != INFINITY) {
                bestTimeForStop += walkDurationToTarget;
                bestTime = Math.min(bestTime, bestTimeForStop);
            } else if (timeType == TimeType.ARRIVAL && bestTimeForStop != -INFINITY) {
                bestTimeForStop -= walkDurationToTarget;
                bestTime = Math.max(bestTime, bestTimeForStop);
            }
        }
        return bestTime;
    }

    private List<Connection> reconstructParetoOptimalSolutions(List<Leg[]> bestTimeLegsPerRound,
                                                               Map<Integer, Integer> targetStops, TimeType timeType) {
        final List<Connection> connections = new ArrayList<>();

        // iterate over all rounds
        for (Leg[] roundLegs : bestTimeLegsPerRound) {

            Leg leg = null;
            int bestTime = timeType == TimeType.DEPARTURE ? INFINITY : -INFINITY;

            for (Map.Entry<Integer, Integer> entry : targetStops.entrySet()) {
                int targetStopIdx = entry.getKey();
                int targetStopWalkingTime = entry.getValue();
                if (roundLegs[targetStopIdx] == null) {
                    continue;
                }
                Leg currentLeg = roundLegs[targetStopIdx];

                if (timeType == TimeType.DEPARTURE) {
                    int actualArrivalTime = currentLeg.arrivalTime + targetStopWalkingTime;
                    if (actualArrivalTime < bestTime) {
                        leg = currentLeg;
                        bestTime = actualArrivalTime;
                    }
                } else {
                    int actualDepartureTime = currentLeg.arrivalTime - targetStopWalkingTime;
                    if (actualDepartureTime > bestTime) {
                        leg = currentLeg;
                        bestTime = actualDepartureTime;
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

            String fromStopId;
            String toStopId;
            int departureTime;
            int arrivalTime;
            Connection.LegType type;
            if (leg.arrivalTime > leg.departureTime) {
                fromStopId = stops[leg.previous.stopIdx].id();
                toStopId = stops[leg.stopIdx].id();
                departureTime = leg.departureTime;
                arrivalTime = leg.arrivalTime;
            } else {
                fromStopId = stops[leg.stopIdx].id();
                toStopId = stops[leg.previous.stopIdx].id();
                departureTime = leg.arrivalTime;
                arrivalTime = leg.departureTime;
            }

            if (leg.type == ArrivalType.ROUTE) {
                Route route = routes[leg.routeOrTransferIdx];
                routeId = route.id();
                tripId = route.tripIds()[leg.tripOffset];
                type = Connection.LegType.ROUTE;

            } else if (leg.type == ArrivalType.TRANSFER) {
                routeId = String.format("transfer_%s_%s", fromStopId, toStopId);
                type = Connection.LegType.WALK_TRANSFER;
            } else {
                throw new IllegalStateException("Unknown leg type");
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
     * @param stopIdx               - The index of the stop to expand transfers from.
     * @param referenceTimes        - A array with the overall best arrival time for each stop, indexed by stop index.
     *                              Note: The arrival time is reduced by the same stop transfer time for transfers, to
     *                              make them comparable with route arrivals.
     * @param referenceLegsPerRound - A list of arrays with the best arrival time for each stop per round, indexed by
     *                              round.
     * @param markedStops           - A set of stop indices that have been marked for scanning in the next round.
     * @param round                 - The current round to relax footpaths for.
     * @param maxWalkingDuration    - The maximum walking duration to reach the target stop. If the walking duration
     *                              exceeds this value, the target stop is not reached.
     * @param minTransferDuration   - The minimum transfer duration time, since this is intended as rest period it is
     *                              added to the walk time.
     * @param timeType              - The type of time to check for (arrival or departure), defines if stop is
     *                              considered as arrival or departure stop.
     */
    private void expandFootpathsFromStop(int stopIdx, int[] referenceTimes, List<Leg[]> referenceLegsPerRound,
                                         Set<Integer> markedStops, int round, int maxWalkingDuration,
                                         int minTransferDuration, TimeType timeType) {
        // if stop has no transfers, then no footpaths can be expanded
        if (stops[stopIdx].numberOfTransfers() == 0) {
            return;
        }
        Stop sourceStop = stops[stopIdx];
        Leg previousLeg = referenceLegsPerRound.get(round)[stopIdx];

        // do not relax footpath from stop that was only reached by footpath in the same round
        if (previousLeg == null || previousLeg.type == ArrivalType.TRANSFER) {
            return;
        }

        int startTime = previousLeg.arrivalTime;
        int timeDirection = timeType == TimeType.DEPARTURE ? 1 : -1;

        for (int i = sourceStop.transferIdx(); i < sourceStop.transferIdx() + sourceStop.numberOfTransfers(); i++) {
            Transfer transfer = transfers[i];
            Stop targetStop = stops[transfer.targetStopIdx()];
            int duration = transfer.duration();
            if (maxWalkingDuration < duration) {
                continue;
            }
            int newTargetStopArrivalTime = startTime + timeDirection * (transfer.duration() + minTransferDuration);

            // For Comparison with Route Arrivals the Arrival Time by Transfer must be reduced (or increased in case of
            // departure optimization) by the same stop transfer time
            int comparableNewTargetStopArrivalTime = newTargetStopArrivalTime - targetStop.sameStopTransferTime();
            if (timeType == TimeType.DEPARTURE && referenceTimes[transfer.targetStopIdx()] <= comparableNewTargetStopArrivalTime) {
                continue;
            } else if (timeType == TimeType.ARRIVAL && referenceTimes[transfer.targetStopIdx()] >= comparableNewTargetStopArrivalTime) {
                continue;
            }

            log.debug("Stop {} was improved by transfer from stop {}", targetStop.id(), sourceStop.id());

            referenceTimes[transfer.targetStopIdx()] = comparableNewTargetStopArrivalTime;

            referenceLegsPerRound.get(round)[transfer.targetStopIdx()] = new Leg(startTime, newTargetStopArrivalTime,
                    ArrivalType.TRANSFER, i, NO_INDEX, transfer.targetStopIdx(),
                    referenceLegsPerRound.get(round)[stopIdx]);
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
         * Uses a transfer between stops (not a same stop transfer).
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
