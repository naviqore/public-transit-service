package org.naviqore.raptor.router;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.naviqore.raptor.QueryConfig;
import org.naviqore.raptor.TimeType;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import static org.naviqore.raptor.router.QueryState.INFINITY;

/**
 * Scans routes, which are passing marked stops, for each round.
 */
@Slf4j
class RouteScanner {

    private static final int SECONDS_IN_DAY = 86400;
    private static final int NO_LIMIT = -1;
    private static final int NO_TRIP_FOUND = -1;

    /**
     * The number of trips a route runs on a day from which the trip to take at a stop is found by binary search, not by
     * reading the trips one after the other: on the Swiss feed, reading them is faster up to 63 trips, a binary search
     * from 64 on.
     */
    private static final int BINARY_SEARCH_MIN_TRIPS = 64;

    private final Stop[] stops;
    private final int[] stopRoutes;
    private final Route[] routes;
    private final RouteStop[] routeStops;
    private final QueryState queryState;

    private final int minTransferDuration;
    private final boolean forward;
    private final int timeDirection;

    // the stop times of the days to scan, from the first day (startDayOffset) on, forward or backward in time
    private final DayStopTimes[] days;
    private final int startDayOffset;

    private final BitSet routesToScanMask;

    // how long after the start of a journey its first trip may leave at the latest, see setFirstTripLimit
    private int firstTripLimit = NO_LIMIT;

    /**
     * @param queryState        the query state with the best time per stop and label per stop and round.
     * @param raptorData        the current raptor data structures.
     * @param queryConfig       the query config.
     * @param timeType          the time type (arrival or departure).
     * @param referenceDateTime the reference date time for the query.
     * @param maxDaysToScan     the maximum number of days to scan.
     */
    RouteScanner(QueryState queryState, RaptorData raptorData, QueryConfig queryConfig, TimeType timeType,
                 OffsetDateTime referenceDateTime, int maxDaysToScan) {
        // constant data structures
        this.stops = raptorData.getStopContext().stops();
        this.stopRoutes = raptorData.getStopContext().stopRoutes();
        this.routes = raptorData.getRouteTraversal().routes();
        this.routeStops = raptorData.getRouteTraversal().routeStops();

        // note: will also change outside of scanner, due to footpath relaxation
        this.queryState = queryState;

        // constant configuration of scanner
        this.minTransferDuration = queryConfig.getMinimumTransferDuration();
        this.routesToScanMask = new BitSet(routes.length);
        this.forward = timeType == TimeType.DEPARTURE;
        this.timeDirection = forward ? 1 : -1;

        if (maxDaysToScan < 1) {
            throw new IllegalArgumentException("maxDaysToScan must be greater than 0.");
        }

        LocalDate referenceDate = referenceDateTime.toLocalDate();
        if (maxDaysToScan == 1) {
            // single-day scan
            days = new DayStopTimes[1];
            days[0] = raptorData.getStopTimeProvider().getStopTimesForDate(referenceDate, queryConfig);
            startDayOffset = 0;

        } else {
            // multi-day scan: there is no need to scan the next day for arrival trips but previous day is maybe needed
            // in departure trips
            int actualDaysToScan;
            if (forward) {
                LocalDate previousDay = referenceDate.minusDays(1);
                DayStopTimes previousDayStopTimes = raptorData.getStopTimeProvider()
                        .getStopTimesForDate(previousDay, queryConfig);

                int departureTimeInPreviousDaySeconds = (int) Duration.between(previousDay.atStartOfDay(ZoneOffset.UTC),
                        referenceDateTime).getSeconds();

                // if latest stop time of previous day is after / equal the departure time, we need to include the
                // previous day to scanning
                if (previousDayStopTimes.latest() >= departureTimeInPreviousDaySeconds) {
                    startDayOffset = -1;
                    actualDaysToScan = maxDaysToScan;
                } else {
                    startDayOffset = 0;
                    actualDaysToScan = maxDaysToScan - 1;
                }

            } else {
                actualDaysToScan = maxDaysToScan - 1;
                startDayOffset = 0;
            }

            days = new DayStopTimes[actualDaysToScan];
            for (int i = 0; i < actualDaysToScan; i++) {
                int dayOffset = i + startDayOffset;
                LocalDate date = forward ? referenceDate.plusDays(dayOffset) : referenceDate.minusDays(dayOffset);
                days[i] = raptorData.getStopTimeProvider().getStopTimesForDate(date, queryConfig);
            }
        }
    }

    /**
     * Whether a label starts a journey: the initial label of a source stop, or a walk from one.
     */
    private static boolean isStartOfJourney(QueryState.Label label) {
        if (label.type() == QueryState.LabelType.TRANSFER) {
            QueryState.Label previous = label.previous();
            return previous != null && previous.type() == QueryState.LabelType.INITIAL;
        }
        return label.type() == QueryState.LabelType.INITIAL;
    }

    /**
     * Limits how long after the start of a journey, a source stop at its source time or a stop walked to from it, its
     * first trip may leave (routing backward: how long before the end its last trip may arrive). A pass of a range
     * query takes only trips leaving in the time window, the rest of which this is.
     *
     * @param duration the limit in seconds, inclusive, or -1 for none.
     */
    void setFirstTripLimit(int duration) {
        firstTripLimit = duration;
    }

    /**
     * Scans all routes passing marked stops for the given round.
     *
     * @param round the current round.
     */
    void scan(int round) {
        setRoutesToScanMask();
        log.debug("Scanning routes for round {}", round);

        // scan selected routes and mark stops with improved times
        for (int currentRouteIdx = routesToScanMask.nextSetBit(
                0); currentRouteIdx >= 0; currentRouteIdx = routesToScanMask.nextSetBit(currentRouteIdx + 1)) {
            scanRoute(currentRouteIdx, round);
        }
    }

    /**
     * Set all routes to scan from the marked stops.
     */
    private void setRoutesToScanMask() {
        routesToScanMask.clear();
        for (int stopIdx = queryState.nextStopMarkedThisRound(
                0); stopIdx >= 0; stopIdx = queryState.nextStopMarkedThisRound(stopIdx + 1)) {
            Stop currentStop = stops[stopIdx];
            int stopRouteIdx = currentStop.stopRouteIdx();
            int stopRouteEndIdx = stopRouteIdx + currentStop.numberOfRoutes();

            while (stopRouteIdx < stopRouteEndIdx) {
                routesToScanMask.set(stopRoutes[stopRouteIdx]);
                stopRouteIdx++;
            }
        }
    }

    /**
     * Scan a route in time type applicable direction to find the best times for each stop on route for given round.
     *
     * @param currentRouteIdx the index of the current route.
     * @param round           the current round.
     */
    private void scanRoute(int currentRouteIdx, int round) {
        Route currentRoute = routes[currentRouteIdx];
        if (!isRouteActiveInDaysToScan(currentRouteIdx)) {
            return;
        }

        int lastRound = round - 1;
        ActiveTrip activeTrip = null;

        int firstRouteStopIdx = currentRoute.firstRouteStopIdx();
        int numberOfStops = currentRoute.numberOfStops();

        int startOffset = forward ? 0 : numberOfStops - 1;
        int endOffset = forward ? numberOfStops : -1;

        for (int stopOffset = startOffset; stopOffset != endOffset; stopOffset += timeDirection) {
            int stopIdx = routeStops[firstRouteStopIdx + stopOffset].stopIndex();
            Stop stop = stops[stopIdx];
            int bestStopTime = queryState.getComparableBestTime(stopIdx);

            // find first marked stop in route
            if (activeTrip == null) {
                if (!canEnterAtStop(bestStopTime, stopIdx, stopOffset, currentRouteIdx, numberOfStops)) {
                    continue;
                }
            } else {
                // in this case we are on a trip and need to check if time has improved
                int stopTimeIndex = activeTrip.firstStopTimeIdx + 2 * stopOffset;
                // the stopTimeIndex points to the arrival time of the stop and stopTimeIndex + 1 to the departure time
                int targetTime = activeTrip.stopTimes[forward ? stopTimeIndex : stopTimeIndex + 1];
                targetTime += activeTrip.dayTimeOffset;

                if (!checkIfTripIsPossibleAndUpdateMarks(targetTime, activeTrip, bestStopTime, stopIdx, round,
                        lastRound, currentRouteIdx)) {
                    continue;
                }
            }

            ActiveTrip possibleTrip = findPossibleTrip(stopIdx, stop, stopOffset, currentRouteIdx, numberOfStops,
                    lastRound);
            // a trip boarded here replaces the active trip unless it is worse: the stop's label may be in time for an
            // earlier (later, routing backward) trip than the active one, or only for a worse one, or for none, once
            // the transfer time is added
            if (activeTrip == null || possibleTrip != null && !isBetter(getTripTime(activeTrip, stopOffset),
                    possibleTrip.entryTime())) {
                activeTrip = possibleTrip;
            }
        }
    }

    /**
     * Gets the time of a trip at a stop of its route that a trip boarded at the stop is compared by: its departure, or
     * routing backward its arrival.
     */
    private int getTripTime(ActiveTrip trip, int stopOffset) {
        int stopTimeIndex = trip.firstStopTimeIdx + 2 * stopOffset;
        // the stopTimeIndex points to the arrival time of the stop and stopTimeIndex + 1 to the departure time
        return trip.stopTimes[forward ? stopTimeIndex + 1 : stopTimeIndex] + trip.dayTimeOffset;
    }

    private boolean isRouteActiveInDaysToScan(int routeIdx) {
        for (DayStopTimes day : days) {
            if (day.numberOfTrips(routeIdx) > 0) {
                return true;
            }
        }

        return false;
    }

    /**
     * This method checks if a trip can be entered at the stop in the current round. A trip can be entered if the stop
     * was reached in a previous round, and is not the first (targetTime) / last (sourceTime) stop of a trip or (for
     * performance reasons) assuming that this check is only run when not traveling with an active trip, the stop was
     * not marked in a previous round (i.e., the lasts round trip query would be repeated).
     *
     * @param stopTime      the time at the stop.
     * @param stopIdx       the index of the stop to check if a trip can be entered.
     * @param stopOffset    the offset of the stop in the route.
     * @param routeIdx      the index of the current route.
     * @param numberOfStops the number of stops of the current route.
     */
    private boolean canEnterAtStop(int stopTime, int stopIdx, int stopOffset, int routeIdx, int numberOfStops) {
        int unreachableValue = forward ? INFINITY : -INFINITY;
        if (stopTime == unreachableValue) {
            return false;
        }

        // no trip of the route departs after the time at the stop (arrives before it, routing backward)
        if (isBetter(getFurthestTripTimeOfRoute(routeIdx), stopTime)) {
            return false;
        }

        if (!queryState.isMarkedThisRound(stopIdx)) {
            // this stop has already been scanned in previous round without improved target time
            return false;
        }

        if (forward && (stopOffset + 1 == numberOfStops)) {
            // last stop in route, does not make sense to check for trip to enter
            return false;
        } else if (!forward && (stopOffset == 0)) {
            // first stop in route, does not make sense to check for trip to enter
            return false;
        }

        // got first marked stop in the route
        return true;
    }

    /**
     * Get the latest possible stop time for a route on all days to scan (for time type DEPARTURE) or the earliest
     * possible stop time for a route on all days to scan (for time type ARRIVAL).
     * <p>
     * Returns -INFINITY for DEPARTURE and INFINITY for ARRIVAL if no trip is possible.
     *
     * @param routeIdx the index of the route to get the furthest trip time from.
     * @return the furthest trip time of the route.
     */
    private int getFurthestTripTimeOfRoute(int routeIdx) {
        // every day counts, since trips of one service day can run after trips of the next (e.g. after midnight)
        int furthestTripTime = forward ? -INFINITY : INFINITY;
        for (int dayIndex = 0; dayIndex < days.length; dayIndex++) {
            // the latest trip time for departure and the earliest for arrival
            int time = forward ? days[dayIndex].routeLatest()[routeIdx] : days[dayIndex].routeEarliest()[routeIdx];
            if (time == RaptorTripMaskProvider.RouteTripMask.NO_TRIP) {
                continue;
            }

            time += timeDirection * (dayIndex + startDayOffset) * SECONDS_IN_DAY;
            furthestTripTime = forward ? Math.max(furthestTripTime, time) : Math.min(furthestTripTime, time);
        }

        return furthestTripTime;
    }

    /**
     * <p>This method checks if the time at a stop can be improved by arriving or departing with the active trip, if so
     * the stop is marked for the next round and the time is updated. If the time is improved it is clear that an
     * earlier or later trip (based on the TimeType) is not possible and the method returns false.</p>
     * <p>If the time was not improved, an additional check will be needed to figure out if an earlier or later trip
     * from the stop is possible within the current round, thus the method returns true. The arrival may still be the
     * best on a trip at the stop, though a walk or the start reached the stop in time for an earlier trip: then it is
     * kept as the arrival to walk on from, see {@link QueryState}.</p>
     *
     * @param targetTime      the stop time to check for an earlier or later trip.
     * @param activeTrip      the active trip to check for an earlier or later trip.
     * @param bestStopTime    the earliest or latest time at the stop based on the TimeType.
     * @param stopIdx         the index of the stop to check for an earlier or later trip.
     * @param currentRouteIdx the index of the current route.
     * @return true if an earlier or later trip is possible, false otherwise.
     */
    private boolean checkIfTripIsPossibleAndUpdateMarks(int targetTime, ActiveTrip activeTrip, int bestStopTime,
                                                        int stopIdx, int thisRound, int lastRound,
                                                        int currentRouteIdx) {
        boolean isImproved = forward ? targetTime < bestStopTime : targetTime > bestStopTime;

        if (isImproved) {
            queryState.setBestTime(stopIdx, targetTime);

            QueryState.Label label = new QueryState.Label(activeTrip.entryTime, targetTime, QueryState.LabelType.ROUTE,
                    currentRouteIdx, activeTrip.tripOffset, stopIdx, activeTrip.previousLabel);
            queryState.setLabel(thisRound, stopIdx, label);
            queryState.setTripLabel(stopIdx, label);
            queryState.mark(stopIdx);

            return false;
        } else {
            if (isBetter(targetTime, queryState.getBestTripTime(stopIdx))) {
                // the earliest arrival on a trip so far, which walks start from
                queryState.setTripLabel(stopIdx,
                        new QueryState.Label(activeTrip.entryTime, targetTime, QueryState.LabelType.ROUTE,
                                currentRouteIdx, activeTrip.tripOffset, stopIdx, activeTrip.previousLabel));
            }

            // a stop not reached in the last round of this pass has no label to board an earlier trip from: a label of
            // a pass before, which a range query keeps, belongs to another departure
            QueryState.Label previous = queryState.isMarkedThisRound(stopIdx) ? queryState.getLabel(lastRound,
                    stopIdx) : null;

            boolean isImprovedInSameRound = previous == null || (forward ? previous.targetTime() >= targetTime : previous.targetTime() <= targetTime);
            // an earlier (later, routing backward) trip can only be boarded after a label of the round before
            return !isImprovedInSameRound;
        }
    }

    /**
     * Find the possible trip on the route for the given trip mask. This loops through all trips departing or arriving
     * from a given stop for a given route and returns details about the first or last trip that can be taken (departing
     * after or arriving before the time of the previous round at this stop and accounting for transfer constraints).
     * The first or last trip is looked for over all days to scan, since trips of one service day can run after trips of
     * the next, e.g. trips after midnight stored as 24:30 of one day and as 00:15 of the next.
     *
     * @param stopIdx       the index of the stop to find the possible trip from.
     * @param stop          the stop to find the possible trip from.
     * @param stopOffset    the offset of the stop in the route.
     * @param routeIdx      the index of the route to find the possible trip on.
     * @param numberOfStops the number of stops of the route.
     * @param lastRound     the last round.
     */
    private @Nullable ActiveTrip findPossibleTrip(int stopIdx, Stop stop, int stopOffset, int routeIdx,
                                                  int numberOfStops, int lastRound) {
        QueryState.Label previousLabel = queryState.getLabel(lastRound, stopIdx);

        // this is the reference time, where we can depart after or arrive earlier
        int referenceTime = previousLabel.targetTime();
        if (previousLabel.type() == QueryState.LabelType.ROUTE) {
            referenceTime += forward ? Math.max(stop.sameStopTransferTime(), minTransferDuration) : -Math.max(
                    stop.sameStopTransferTime(), minTransferDuration);
        }

        // the first trip of a journey may have to leave within a limit of its start
        boolean limited = firstTripLimit != NO_LIMIT && isStartOfJourney(previousLabel);
        int limitTime = limited ? previousLabel.targetTime() + timeDirection * firstTripLimit : 0;

        ActiveTrip possibleTrip = null;
        for (int dayIndex = 0; dayIndex < days.length; dayIndex++) {
            DayStopTimes day = days[dayIndex];
            int numberOfTrips = day.numberOfTrips(routeIdx);
            // the route does not run on the day, it has no earliest and latest trip time to add the day's offset to
            if (numberOfTrips == 0) {
                continue;
            }

            int dayOffset = dayIndex + startDayOffset;
            int timeOffset = timeDirection * dayOffset * SECONDS_IN_DAY;
            int earliestTripTime = day.routeEarliest()[routeIdx] + timeOffset;
            int latestTripTime = day.routeLatest()[routeIdx] + timeOffset;

            // check if the day has any trips relevant
            if ((forward ? latestTripTime < referenceTime : referenceTime < earliestTripTime)) {
                continue;
            }

            // check if any trip of the day can be better than the one found on a previous day
            if (possibleTrip != null && !isBetter(forward ? earliestTripTime : latestTripTime,
                    possibleTrip.entryTime())) {
                continue;
            }

            // the first possible trip of a day is the best of the day: the trips keep their order at every stop
            int tripStopTimes = 2 * numberOfStops;
            int firstTripTimeIdx = day.routeStopTimes()[routeIdx] + 2 * stopOffset + (forward ? 1 : 0);
            int trip = findFirstPossibleTrip(day.stopTimes(), firstTripTimeIdx, tripStopTimes, numberOfTrips,
                    referenceTime - timeOffset);
            if (trip == NO_TRIP_FOUND) {
                continue;
            }

            int tripTime = day.stopTimes()[firstTripTimeIdx + trip * tripStopTimes] + timeOffset;
            // beyond the limit, so is every later trip of the day
            if (limited && isBetter(limitTime, tripTime)) {
                continue;
            }
            if (possibleTrip == null || isBetter(tripTime, possibleTrip.entryTime())) {
                possibleTrip = new ActiveTrip(day.tripPosition(routeIdx, trip), tripTime, timeOffset, previousLabel,
                        day.stopTimes(), day.routeStopTimes()[routeIdx] + trip * tripStopTimes);
            }
        }

        return possibleTrip;
    }

    /**
     * Finds the first trip of a route on a day that can be taken at a stop: the first leaving at or after a time, or
     * routing backward the last arriving at or before it. The trips are read at the stop one after the other, or, if
     * the route runs many on the day, by binary search: they keep their order at every stop.
     *
     * @param stopTimes        the stop times of the day.
     * @param firstTripTimeIdx the index of the time of the route's first trip of the day at the stop: its departure, or
     *                         routing backward its arrival.
     * @param tripStopTimes    the number of stop times of a trip, the distance between the times of two trips.
     * @param numberOfTrips    the number of trips of the route on the day.
     * @param time             the time, relative to the day.
     * @return the number of the trip among the route's trips of the day, or {@link #NO_TRIP_FOUND}.
     */
    private int findFirstPossibleTrip(int[] stopTimes, int firstTripTimeIdx, int tripStopTimes, int numberOfTrips,
                                      int time) {
        if (numberOfTrips < BINARY_SEARCH_MIN_TRIPS) {
            if (forward) {
                for (int trip = 0; trip < numberOfTrips; trip++) {
                    if (stopTimes[firstTripTimeIdx + trip * tripStopTimes] >= time) {
                        return trip;
                    }
                }
            } else {
                for (int trip = numberOfTrips - 1; trip >= 0; trip--) {
                    if (stopTimes[firstTripTimeIdx + trip * tripStopTimes] <= time) {
                        return trip;
                    }
                }
            }
            return NO_TRIP_FOUND;
        }

        // the number of trips before the first trip leaving at or after the time (forward), or after the last trip
        // arriving at or before it (backward)
        int low = 0;
        int high = numberOfTrips;
        while (low < high) {
            int middle = (low + high) >>> 1;
            int tripTime = stopTimes[firstTripTimeIdx + middle * tripStopTimes];
            if (forward ? tripTime < time : tripTime <= time) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        if (forward) {
            return low < numberOfTrips ? low : NO_TRIP_FOUND;
        }
        return low > 0 ? low - 1 : NO_TRIP_FOUND;
    }

    /**
     * Whether a trip time is better than another for the time type: earlier when departing, later when arriving.
     */
    private boolean isBetter(int time, int otherTime) {
        return forward ? time < otherTime : time > otherTime;
    }

    /**
     * Gets the departure times (arrival times, when routing backward) of the trips of all routes at a stop in a range,
     * on all days to scan, at every position of the stop on a route where a trip can be boarded: not at its last stop
     * (routing backward: its first stop).
     *
     * @param stopIdx  the index of the stop.
     * @param minValue the earliest time to get.
     * @param maxValue the latest time to get.
     * @return the times in the range, in no particular order.
     */
    List<Integer> getTripTimesAtStop(int stopIdx, int minValue, int maxValue) {
        List<Integer> tripTimes = new ArrayList<>();
        Stop stop = stops[stopIdx];
        int stopRouteEndIdx = stop.stopRouteIdx() + stop.numberOfRoutes();
        int firstOffset = forward ? 0 : 1;
        for (int stopRouteIdx = stop.stopRouteIdx(); stopRouteIdx < stopRouteEndIdx; stopRouteIdx++) {
            int routeIdx = stopRoutes[stopRouteIdx];
            Route route = routes[routeIdx];
            for (int stopOffset = firstOffset; stopOffset < firstOffset + route.numberOfStops() - 1; stopOffset++) {
                if (routeStops[route.firstRouteStopIdx() + stopOffset].stopIndex() == stopIdx) {
                    addStopTimesInRange(tripTimes, routeIdx, route.numberOfStops(), stopOffset, minValue, maxValue);
                }
            }
        }

        return tripTimes;
    }

    /**
     * Adds the stop times (departures, or arrivals when routing backward) at a position on a route in a time range.
     *
     * @param stopTimesInRange the stop times to add to.
     * @param routeIdx         the index of the route.
     * @param numberOfStops    the number of stops of the route.
     * @param stopOffset       the position of the stop on the route.
     * @param minValue         the earliest time to add.
     * @param maxValue         the latest time to add.
     */
    private void addStopTimesInRange(List<Integer> stopTimesInRange, int routeIdx, int numberOfStops, int stopOffset,
                                     int minValue, int maxValue) {
        for (int dayIndex = 0; dayIndex < days.length; dayIndex++) {
            DayStopTimes day = days[dayIndex];
            int numberOfTrips = day.numberOfTrips(routeIdx);
            // the route does not run on the day, it has no earliest and latest trip time to add the day's offset to
            if (numberOfTrips == 0) {
                continue;
            }

            int dayOffset = dayIndex + startDayOffset;
            int timeOffset = timeDirection * dayOffset * SECONDS_IN_DAY;
            int earliestTripTime = day.routeEarliest()[routeIdx] + timeOffset;
            int latestTripTime = day.routeLatest()[routeIdx] + timeOffset;

            // check if the day has any trips relevant; routing backward, the days to scan run back in time
            if (latestTripTime < minValue || earliestTripTime > maxValue) {
                continue;
            }

            int tripStopTimes = 2 * numberOfStops;
            // the departure at the stop, or the arrival when routing backward
            int firstTripTimeIdx = day.routeStopTimes()[routeIdx] + 2 * stopOffset + (forward ? 1 : 0);
            for (int trip = 0; trip < numberOfTrips; trip++) {
                int stopTime = day.stopTimes()[firstTripTimeIdx + trip * tripStopTimes] + timeOffset;
                if (stopTime >= minValue && stopTime <= maxValue) {
                    stopTimesInRange.add(stopTime);
                }
            }
        }
    }

    /**
     * A trip taken on a route.
     *
     * @param tripOffset       the position of the trip among all trips of its route, which gives its id.
     * @param entryTime        the time the trip is boarded at (alighted from, routing backward).
     * @param dayTimeOffset    the offset of the trip's day from the reference day, in seconds.
     * @param previousLabel    the label the trip is boarded after.
     * @param stopTimes        the stop times of the trip's day.
     * @param firstStopTimeIdx the index of the trip's arrival at the first stop of its route in {@code stopTimes}.
     */
    private record ActiveTrip(int tripOffset, int entryTime, int dayTimeOffset, QueryState.Label previousLabel,
                              int[] stopTimes, int firstStopTimeIdx) {
    }

}
