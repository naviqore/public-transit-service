package ch.naviqore.raptor;

import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static ch.naviqore.raptor.Raptor.INFINITY;

/**
 * Scans routes, which are passing marked stops, for each round.
 */
@Log4j2
class RouteScanner {

    private final Stop[] stops;
    private final int[] stopRoutes;
    private final StopTime[] stopTimes;
    private final Route[] routes;
    private final RouteStop[] routeStops;

    private final List<Raptor.Label[]> bestLabelsPerRound;
    private final int[] bestTimeForStops;

    /**
     * the minimum transfer duration time, since this is intended as rest period it is added to the walk time.
     */
    private final int minTransferDuration;
    private final TimeType timeType;

    /**
     * @param stopContext        the stop context data structure.
     * @param routeTraversal     the route traversal data structure.
     * @param bestLabelsPerRound the prepared layer from raptor wo keep track of the best labels per round.
     * @param bestTimeForStops   the global best time per stop.
     * @param timeType           the type of time to check for (arrival or departure).
     * @param config             the query configuration.
     */
    RouteScanner(StopContext stopContext, RouteTraversal routeTraversal, List<Raptor.Label[]> bestLabelsPerRound,
                 int[] bestTimeForStops, TimeType timeType, QueryConfig config) {
        // constant data structures
        this.stops = stopContext.stops();
        this.stopRoutes = stopContext.stopRoutes();
        this.stopTimes = routeTraversal.stopTimes();
        this.routes = routeTraversal.routes();
        this.routeStops = routeTraversal.routeStops();
        // variable labels and best times (note: will vary also outside of scanner, due to footpath relaxation)
        this.bestLabelsPerRound = bestLabelsPerRound;
        this.bestTimeForStops = bestTimeForStops;
        // constant configuration of scanner
        this.minTransferDuration = config.getMinimumTransferDuration();
        this.timeType = timeType;
    }

    /**
     * Scans all routes passing marked stops for the given round.
     *
     * @param round       the current round.
     * @param markedStops the marked stops for this round.
     * @return the marked stops for the next round.
     */
    Set<Integer> scan(int round, Set<Integer> markedStops) {
        log.debug("Scanning routes for round {}", round);
        Set<Integer> markedStopsNext = new HashSet<>();

        Raptor.Label[] bestLabelsLastRound = bestLabelsPerRound.get(round - 1);
        bestLabelsPerRound.add(new Raptor.Label[stops.length]);
        Raptor.Label[] bestLabelsThisRound = bestLabelsPerRound.get(round);

        Set<Integer> routesToScan = getRoutesToScan(markedStops);
        log.debug("Routes to scan: {}", routesToScan);

        // scan selected routes
        for (int currentRouteIdx : routesToScan) {
            scanRoute(currentRouteIdx, bestTimeForStops, bestLabelsLastRound, bestLabelsThisRound, markedStops,
                    markedStopsNext);
        }

        return markedStopsNext;
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
     * Scan a route in time type applicable direction to find the best times for each stop on route for given round.
     *
     * @param currentRouteIdx     the index of the current route.
     * @param bestTimes           the best time for each stop.
     * @param bestLabelsLastRound the best label for each stop in the last round.
     * @param bestLabelsThisRound the best label for each stop in the current round.
     * @param markedStops         the set of marked stops from the previous round.
     * @param markedStopsNext     the set of marked stops for the next round.
     */
    private void scanRoute(int currentRouteIdx, int[] bestTimes, Raptor.Label[] bestLabelsLastRound,
                           Raptor.Label[] bestLabelsThisRound, Set<Integer> markedStops, Set<Integer> markedStopsNext) {

        boolean forward = timeType == TimeType.DEPARTURE;
        Route currentRoute = routes[currentRouteIdx];
        log.debug("Scanning route {} {}", currentRoute.id(), forward ? "forward" : "backward");
        final int firstRouteStopIdx = currentRoute.firstRouteStopIdx();
        final int firstStopTimeIdx = currentRoute.firstStopTimeIdx();
        final int numberOfStops = currentRoute.numberOfStops();

        ActiveTrip activeTrip = null;

        int startOffset = forward ? 0 : numberOfStops - 1;
        int endOffset = forward ? numberOfStops : -1;
        int step = forward ? 1 : -1;

        for (int stopOffset = startOffset; stopOffset != endOffset; stopOffset += step) {
            int stopIdx = routeStops[firstRouteStopIdx + stopOffset].stopIndex();
            Stop stop = stops[stopIdx];
            int bestStopTime = bestTimes[stopIdx];

            // find first marked stop in route
            if (activeTrip == null) {
                if (!canEnterAtStop(stop, bestStopTime, markedStops, stopIdx, stopOffset, numberOfStops)) {
                    continue;
                }
            } else {
                // in this case we are on a trip and need to check if time has improved
                StopTime stopTimeObj = stopTimes[firstStopTimeIdx + activeTrip.tripOffset * numberOfStops + stopOffset];
                if (!checkIfTripIsPossibleAndUpdateMarks(stopTimeObj, activeTrip, stop, bestStopTime, bestTimes,
                        stopIdx, bestLabelsThisRound, bestLabelsLastRound, markedStopsNext, currentRouteIdx)) {
                    continue;
                }
            }
            activeTrip = findPossibleTrip(stopIdx, stop, stopOffset, currentRoute, bestLabelsLastRound);
        }
    }

    /**
     * This method checks if a trip can be entered at the stop in the current round. A trip can be entered if the stop
     * was reached in a previous round, and is not the first (targetTime) / last (sourceTime) stop of a trip or (for
     * performance reasons) assuming that this check is only run when not travelling with an active trip, the stop was
     * not marked in a previous round (i.e., the lasts round trip query would be repeated).
     *
     * @param stop          the stop to check if a trip can be entered.
     * @param stopTime      the time at the stop.
     * @param markedStops   the set of marked stops from the previous round.
     * @param stopIdx       the index of the stop to check if a trip can be entered.
     * @param stopOffset    the offset of the stop in the route.
     * @param numberOfStops the number of stops in the route.
     */
    private boolean canEnterAtStop(Stop stop, int stopTime, Set<Integer> markedStops, int stopIdx, int stopOffset,
                                   int numberOfStops) {

        int unreachableValue = timeType == TimeType.DEPARTURE ? INFINITY : -INFINITY;
        if (stopTime == unreachableValue) {
            log.debug("Stop {} cannot be reached, continue", stop.id());
            return false;
        }

        if (!markedStops.contains(stopIdx)) {
            // this stop has already been scanned in previous round without improved target time
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
     * <p>This method checks if the time at a stop can be improved by arriving or departing with the active trip, if so
     * the stop is marked for the next round and the time is updated. If the time is improved it is clear that an
     * earlier or later trip (based on the TimeType) is not possible and the method returns false.</p>
     * <p>If the time was not improved, an additional check will be needed to figure out if an earlier or later trip
     * from the stop is possible within the current round, thus the method returns true.</p>
     *
     * @param stopTime            the stop time to check for an earlier or later trip.
     * @param activeTrip          the active trip to check for an earlier or later trip.
     * @param stop                the stop to check for an earlier or later trip.
     * @param bestStopTime        the earliest or latest time at the stop based on the TimeType.
     * @param bestTimes           the earliest or latest time for each stop based on the TimeType.
     * @param stopIdx             the index of the stop to check for an earlier or later trip.
     * @param bestLabelsThisRound the best label for each stop in the current round based on the TimeType.
     * @param bestLabelsLastRound the best label for each stop in the last round based on the TimeType.
     * @param markedStopsNext     the set of marked stops for the next round.
     * @param currentRouteIdx     the index of the current route.
     * @return true if an earlier or later trip is possible, false otherwise.
     */
    private boolean checkIfTripIsPossibleAndUpdateMarks(StopTime stopTime, ActiveTrip activeTrip, Stop stop,
                                                        int bestStopTime, int[] bestTimes, int stopIdx,
                                                        Raptor.Label[] bestLabelsThisRound,
                                                        Raptor.Label[] bestLabelsLastRound,
                                                        Set<Integer> markedStopsNext, int currentRouteIdx) {

        boolean isImproved = (timeType == TimeType.DEPARTURE) ? stopTime.arrival() < bestStopTime : stopTime.departure() > bestStopTime;

        if (isImproved) {
            log.debug("Stop {} was improved", stop.id());
            bestTimes[stopIdx] = (timeType == TimeType.DEPARTURE) ? stopTime.arrival() : stopTime.departure();
            bestLabelsThisRound[stopIdx] = new Raptor.Label(activeTrip.entryTime(),
                    (timeType == TimeType.DEPARTURE) ? stopTime.arrival() : stopTime.departure(),
                    Raptor.LabelType.ROUTE, currentRouteIdx, activeTrip.tripOffset, stopIdx, activeTrip.previousLabel);
            markedStopsNext.add(stopIdx);
            return false;
        } else {
            log.debug("Stop {} was not improved", stop.id());
            Raptor.Label previous = bestLabelsLastRound[stopIdx];
            boolean isImprovedInSameRound = (timeType == TimeType.DEPARTURE) ? previous == null || previous.targetTime() >= stopTime.arrival() : previous == null || previous.targetTime() <= stopTime.departure();
            if (isImprovedInSameRound) {
                log.debug("Stop {} has been improved in same round, trip not possible within this round", stop.id());
                return false;
            } else {
                log.debug("Checking for trips at stop {}", stop.id());
                return true;
            }
        }
    }

    /**
     * Find the possible trip on the route. This loops through all trips departing or arriving from a given stop for a
     * given route and returns details about the first or last trip that can be taken (departing after or arriving
     * before the time of the previous round at this stop and accounting for transfer constraints).
     *
     * @param stopIdx        the index of the stop to find the possible trip from.
     * @param stop           the stop to find the possible trip from.
     * @param stopOffset     the offset of the stop in the route.
     * @param route          the route to find the possible trip on.
     * @param timesLastRound the earliest arrival or latest departure time for each stop in the last round.
     */
    private @Nullable ActiveTrip findPossibleTrip(int stopIdx, Stop stop, int stopOffset, Route route,
                                                  Raptor.Label[] timesLastRound) {

        int firstStopTimeIdx = route.firstStopTimeIdx();
        int numberOfStops = route.numberOfStops();
        int numberOfTrips = route.numberOfTrips();

        int tripOffset = (timeType == TimeType.DEPARTURE) ? 0 : numberOfTrips - 1;
        int entryTime = 0;
        Raptor.Label previousLabel = timesLastRound[stopIdx];

        // this is the reference time, where we can depart after or arrive earlier
        int referenceTime = previousLabel.targetTime();
        if (previousLabel.type() == Raptor.LabelType.ROUTE) {
            referenceTime += (timeType == TimeType.DEPARTURE) ? Math.max(stop.sameStopTransferTime(),
                    minTransferDuration) : -Math.max(stop.sameStopTransferTime(), minTransferDuration);
        }

        while ((timeType == TimeType.DEPARTURE) ? tripOffset < numberOfTrips : tripOffset >= 0) {
            StopTime currentStopTime = stopTimes[firstStopTimeIdx + tripOffset * numberOfStops + stopOffset];
            if ((timeType == TimeType.DEPARTURE) ? currentStopTime.departure() >= referenceTime : currentStopTime.arrival() <= referenceTime) {
                log.debug("Found active trip ({}) on route {}", tripOffset, route.id());
                entryTime = (timeType == TimeType.DEPARTURE) ? currentStopTime.departure() : currentStopTime.arrival();
                break;
            }
            if ((timeType == TimeType.DEPARTURE) ? tripOffset < numberOfTrips - 1 : tripOffset > 0) {
                tripOffset += (timeType == TimeType.DEPARTURE) ? 1 : -1;
            } else {
                // no active trip found
                log.debug("No active trip found on route {}", route.id());
                return null;
            }
        }

        return new ActiveTrip(tripOffset, entryTime, previousLabel);
    }

    private record ActiveTrip(int tripOffset, int entryTime, Raptor.Label previousLabel) {
    }
}
