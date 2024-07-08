package ch.naviqore.raptor.router;

import ch.naviqore.raptor.TimeType;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static ch.naviqore.raptor.router.StopLabelsAndTimes.INFINITY;

/**
 * Scans routes, which are passing marked stops, for each round.
 */
@Slf4j
class RouteScanner {

    private static final int SECONDS_IN_DAY = 86400;

    private final Stop[] stops;
    private final int[] stopRoutes;
    private final int[] stopTimes;
    private final Route[] routes;
    private final RouteStop[] routeStops;
    private final StopLabelsAndTimes stopLabelsAndTimes;

    private final int maxDaysToScan;
    private final int minTransferDuration;
    private final TimeType timeType;

    private final ArrayList<Map<String, TripMask>> tripMasks = new ArrayList<>();

    /**
     * @param stopLabelsAndTimes      the best time per stop and label per stop and round.
     * @param raptorData              the current raptor data structures.
     * @param minimumTransferDuration The minimum transfer duration time.
     * @param timeType                the time type (arrival or departure).
     * @param referenceDate           the reference date for the query.
     * @param maxDaysToScan           the maximum number of days to scan.
     */
    RouteScanner(StopLabelsAndTimes stopLabelsAndTimes, RaptorData raptorData, int minimumTransferDuration,
                 TimeType timeType, LocalDate referenceDate, int maxDaysToScan) {
        // constant data structures
        this.stops = raptorData.getStopContext().stops();
        this.stopRoutes = raptorData.getStopContext().stopRoutes();
        this.stopTimes = raptorData.getRouteTraversal().stopTimes();
        this.routes = raptorData.getRouteTraversal().routes();
        this.routeStops = raptorData.getRouteTraversal().routeStops();
        // note: will also change outside of scanner, due to footpath relaxation
        this.stopLabelsAndTimes = stopLabelsAndTimes;
        // constant configuration of scanner
        this.minTransferDuration = minimumTransferDuration;
        this.timeType = timeType;

        if (maxDaysToScan < 1) {
            throw new IllegalArgumentException("maxDaysToScan must be greater than 0.");
        } else if (maxDaysToScan == 1) {
            tripMasks.add(raptorData.getRaptorTripMaskProvider().getTripMask(referenceDate));
        } else {
            // always include the previous day to account for trips that overlap from the previous/later day
            // depending on time type
            for (int i = -1; i < (maxDaysToScan - 1); i++) {
                LocalDate date = timeType == TimeType.DEPARTURE ? referenceDate.plusDays(i) : referenceDate.minusDays(
                        i);
                tripMasks.add(raptorData.getRaptorTripMaskProvider().getTripMask(date));
            }
        }
        this.maxDaysToScan = maxDaysToScan;
    }

    /**
     * Scans all routes passing marked stops for the given round.
     *
     * @param round       the current round.
     * @param markedStops the marked stops for this round.
     * @return the marked stops for the next round.
     */
    Set<Integer> scan(int round, Set<Integer> markedStops) {
        Set<Integer> routesToScan = getRoutesToScan(markedStops);
        log.debug("Scanning routes for round {} ({})", round, routesToScan);

        // scan selected routes and mark stops with improved times
        Set<Integer> markedStopsNext = new HashSet<>();
        for (int currentRouteIdx : routesToScan) {
            scanRoute(currentRouteIdx, round, markedStops, markedStopsNext);
        }

        return markedStopsNext;
    }

    /**
     * Get all routes to scan from the marked stops.
     *
     * @param markedStops the set of marked stops from the previous round.
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
     * @param currentRouteIdx the index of the current route.
     * @param round           the current round.
     * @param markedStops     the set of marked stops from the previous round.
     * @param markedStopsNext the set of marked stops for the next round.
     */
    private void scanRoute(int currentRouteIdx, int round, Set<Integer> markedStops, Set<Integer> markedStopsNext) {

        final int lastRound = round - 1;

        boolean forward = timeType == TimeType.DEPARTURE;
        Route currentRoute = routes[currentRouteIdx];
        log.debug("Scanning route {} {}", currentRoute.id(), forward ? "forward" : "backward");
        final int firstRouteStopIdx = currentRoute.firstRouteStopIdx();
        final int firstStopTimeIdx = currentRoute.firstStopTimeIdx();
        final int numberOfStops = currentRoute.numberOfStops();

        if (!isRouteActiveInTimeRange(currentRoute)) {
            log.debug("Route {} is not active in time range.", currentRoute.id());
            return;
        }

        ActiveTrip activeTrip = null;

        int startOffset = forward ? 0 : numberOfStops - 1;
        int endOffset = forward ? numberOfStops : -1;
        int step = forward ? 1 : -1;

        for (int stopOffset = startOffset; stopOffset != endOffset; stopOffset += step) {
            int stopIdx = routeStops[firstRouteStopIdx + stopOffset].stopIndex();
            Stop stop = stops[stopIdx];
            int bestStopTime = stopLabelsAndTimes.getComparableBestTime(stopIdx);
            // find first marked stop in route
            if (activeTrip == null) {
                if (!canEnterAtStop(stop, bestStopTime, markedStops, stopIdx, stopOffset, currentRoute)) {
                    continue;
                }
            } else {
                // in this case we are on a trip and need to check if time has improved
                int stopTimeIndex = firstStopTimeIdx + 2 * (activeTrip.tripOffset * numberOfStops + stopOffset) + 2;
                // the stopTimeIndex points to the arrival time of the stop and stopTimeIndex + 1 to the departure time
                int targetTime = stopTimes[(timeType == TimeType.DEPARTURE) ? stopTimeIndex : stopTimeIndex + 1];
                targetTime += activeTrip.dayTimeOffset;
                if (!checkIfTripIsPossibleAndUpdateMarks(targetTime, activeTrip, stop, bestStopTime, stopIdx, round,
                        lastRound, markedStopsNext, currentRouteIdx)) {
                    continue;
                }
            }
            activeTrip = findPossibleTrip(stopIdx, stop, stopOffset, currentRoute, lastRound);
        }
    }

    private boolean isRouteActiveInTimeRange(Route route) {
        for (int i = 0; i < maxDaysToScan; i++) {
            TripMask tripMask = tripMasks.get(i).get(route.id());
            if (tripMask.earliestTripTime() != TripMask.NO_TRIP && tripMask.latestTripTime() != TripMask.NO_TRIP) {
                return true;
            }
        }
        return false;
    }

    /**
     * This method checks if a trip can be entered at the stop in the current round. A trip can be entered if the stop
     * was reached in a previous round, and is not the first (targetTime) / last (sourceTime) stop of a trip or (for
     * performance reasons) assuming that this check is only run when not travelling with an active trip, the stop was
     * not marked in a previous round (i.e., the lasts round trip query would be repeated).
     *
     * @param stop         the stop to check if a trip can be entered.
     * @param stopTime     the time at the stop.
     * @param markedStops  the set of marked stops from the previous round.
     * @param stopIdx      the index of the stop to check if a trip can be entered.
     * @param stopOffset   the offset of the stop in the route.
     * @param currentRoute the current route.
     */
    private boolean canEnterAtStop(Stop stop, int stopTime, Set<Integer> markedStops, int stopIdx, int stopOffset,
                                   Route currentRoute) {

        int unreachableValue = timeType == TimeType.DEPARTURE ? INFINITY : -INFINITY;
        if (stopTime == unreachableValue) {
            log.debug("Stop {} cannot be reached, continue", stop.id());
            return false;
        }

        TripMask tripMask = tripMasks.getLast().get(currentRoute.id());
        int dayOffset = maxDaysToScan == 1 ? 0 : maxDaysToScan - 2; // subtract previous and reference day
        int timeOffset = dayOffset * SECONDS_IN_DAY;
        if (timeType == TimeType.DEPARTURE && tripMask.latestTripTime() + timeOffset < stopTime) {
            log.debug("No trips departing after best stop time on route {} for stop {}", currentRoute.id(), stop.id());
            return false;
        } else if (timeType == TimeType.ARRIVAL && stopTime < tripMask.earliestTripTime() - timeOffset) {
            log.debug("No trips arriving before best stop time on route {} for stop {}", currentRoute.id(), stop.id());
            return false;
        }

        if (!markedStops.contains(stopIdx)) {
            // this stop has already been scanned in previous round without improved target time
            log.debug("Stop {} was not improved in previous round, continue", stop.id());
            return false;
        }

        if (timeType == TimeType.DEPARTURE && (stopOffset + 1 == currentRoute.numberOfStops())) {
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
     * @param targetTime      the stop time to check for an earlier or later trip.
     * @param activeTrip      the active trip to check for an earlier or later trip.
     * @param stop            the stop to check for an earlier or later trip.
     * @param bestStopTime    the earliest or latest time at the stop based on the TimeType.
     * @param stopIdx         the index of the stop to check for an earlier or later trip.
     * @param markedStopsNext the set of marked stops for the next round.
     * @param currentRouteIdx the index of the current route.
     * @return true if an earlier or later trip is possible, false otherwise.
     */
    private boolean checkIfTripIsPossibleAndUpdateMarks(int targetTime, ActiveTrip activeTrip, Stop stop,
                                                        int bestStopTime, int stopIdx, int thisRound, int lastRound,
                                                        Set<Integer> markedStopsNext, int currentRouteIdx) {

        boolean isImproved = (timeType == TimeType.DEPARTURE) ? targetTime < bestStopTime : targetTime > bestStopTime;

        if (isImproved) {
            log.debug("Stop {} was improved", stop.id());
            stopLabelsAndTimes.setBestTime(stopIdx, targetTime);

            StopLabelsAndTimes.Label label = new StopLabelsAndTimes.Label(activeTrip.entryTime, targetTime,
                    StopLabelsAndTimes.LabelType.ROUTE, currentRouteIdx, activeTrip.tripOffset, stopIdx,
                    activeTrip.previousLabel);
            stopLabelsAndTimes.setLabel(thisRound, stopIdx, label);
            markedStopsNext.add(stopIdx);

            return false;
        } else {
            log.debug("Stop {} was not improved", stop.id());
            StopLabelsAndTimes.Label previous = stopLabelsAndTimes.getLabel(lastRound, stopIdx);

            boolean isImprovedInSameRound = previous == null || ((timeType == TimeType.DEPARTURE) ? previous.targetTime() >= targetTime : previous.targetTime() <= targetTime);
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
     * Find the possible trip on the route for the given trip mask. This loops through all trips departing or arriving
     * from a given stop for a given route and returns details about the first or last trip that can be taken (departing
     * after or arriving before the time of the previous round at this stop and accounting for transfer constraints).
     *
     * @param stopIdx    the index of the stop to find the possible trip from.
     * @param stop       the stop to find the possible trip from.
     * @param stopOffset the offset of the stop in the route.
     * @param route      the route to find the possible trip on.
     * @param lastRound  the last round.
     */
    private @Nullable ActiveTrip findPossibleTrip(int stopIdx, Stop stop, int stopOffset, Route route, int lastRound) {

        int firstStopTimeIdx = route.firstStopTimeIdx();
        int numberOfStops = route.numberOfStops();
        int numberOfTrips = route.numberOfTrips();

        StopLabelsAndTimes.Label previousLabel = stopLabelsAndTimes.getLabel(lastRound, stopIdx);

        // this is the reference time, where we can depart after or arrive earlier
        int referenceTime = previousLabel.targetTime();
        if (previousLabel.type() == StopLabelsAndTimes.LabelType.ROUTE) {
            referenceTime += (timeType == TimeType.DEPARTURE) ? Math.max(stop.sameStopTransferTime(),
                    minTransferDuration) : -Math.max(stop.sameStopTransferTime(), minTransferDuration);
        }

        for (int dayIndex = 0; dayIndex < maxDaysToScan; dayIndex++) {
            TripMask tripMask = tripMasks.get(dayIndex).get(route.id());
            int dayOffset = maxDaysToScan == 1 ? 0 : dayIndex - 1; // subtract previous and reference day
            int timeOffset = (timeType == TimeType.DEPARTURE ? 1 : -1) * dayOffset * SECONDS_IN_DAY;
            int earliestTripTime = tripMask.earliestTripTime() + timeOffset;
            int latestTripTime = tripMask.latestTripTime() + timeOffset;

            // check if the day has any trips relevant
            if ((timeType == TimeType.DEPARTURE ? latestTripTime < referenceTime : referenceTime < earliestTripTime)) {
                log.debug("No usable trips on route {} for stop {} on day {}", route.id(), stop.id(), dayIndex);
                continue;
            }

            for (int i = 0; i < numberOfTrips; i++) {
                int tripOffset = (timeType == TimeType.DEPARTURE) ? i : numberOfTrips - 1 - i;
                if (!tripMask.tripMask()[tripOffset]) {
                    log.debug("Trip {} is not active on route {}", i, route.id());
                    continue;
                }
                int stopTimeIndex = firstStopTimeIdx + 2 * (tripOffset * numberOfStops + stopOffset) + 2;
                // the stopTimeIndex points to the arrival time of the stop and stopTimeIndex + 1 to the departure time
                int relevantStopTime = stopTimes[(timeType == TimeType.DEPARTURE) ? stopTimeIndex + 1 : stopTimeIndex];
                relevantStopTime += timeOffset;
                if ((timeType == TimeType.DEPARTURE) ? relevantStopTime >= referenceTime : relevantStopTime <= referenceTime) {
                    log.debug("Found active trip ({}) on route {}", i, route.id());
                    return new ActiveTrip(tripOffset, relevantStopTime, timeOffset, previousLabel);
                }
            }
        }

        // no active trip found
        log.debug("No active trip found on route {}", route.id());
        return null;
    }

    private record ActiveTrip(int tripOffset, int entryTime, int dayTimeOffset,
                              StopLabelsAndTimes.Label previousLabel) {
    }
}
