package org.naviqore.raptor.router;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.naviqore.raptor.QueryConfig;
import org.naviqore.raptor.TimeType;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.naviqore.raptor.router.QueryState.INFINITY;

/**
 * Scans routes, which are passing marked stops, for each round.
 */
@Slf4j
class RouteScanner {

    private static final int SECONDS_IN_DAY = 86400;
    private static final int NO_LIMIT = -1;
    private static final int NO_TRIP_FOUND = -1;
    private static final int NOT_POSSIBLE = -1;
    private static final int NOT_READ = Integer.MIN_VALUE;

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
    private final TripTransfers tripTransfers;
    private final QueryState queryState;

    private final int minTransferDuration;
    private final int maxWalkDuration;
    private final TimeType timeType;
    private final boolean forward;
    private final int timeDirection;

    // the stop times of the days to scan, from the first day (startDayOffset) on, forward or backward in time
    private final DayStopTimes[] days;
    private final int startDayOffset;

    private final BitSet routesToScanMask;

    // the furthest trip time of each route on the days to scan, read from the days when the route is first scanned
    private final int[] furthestTripTimes;
    // of a query of several passes: the trips the groups of trip transfers allow changing to, by the index of the group
    private final KnownTargets @Nullable [] knownTargets;
    // whether the query runs several passes, whose labels are kept for the next, see boardsAtStop
    private final boolean severalPasses;
    // the boardings along trip transfers made in the pass: made again, one would only repeat its work in a later round
    private final Set<BoardingKey> madeBoardings = new HashSet<>();
    // on a route with trip transfers, the trips ridden besides the active trip, which is the earliest: later trips
    // that may change to trips the active trip may not, see covers
    private final List<ActiveTrip> otherTrips = new ArrayList<>();
    // how long after the start of a journey its first trip may leave at the latest, see setFirstTripLimit
    private int firstTripLimit = NO_LIMIT;
    // the source stops and their labels at the start of the current pass, see setSourceLabels
    private int[] sourceStopIndices = new int[0];
    private QueryState.Label[] sourceLabels = new QueryState.Label[0];
    // the time beyond which a label is of no use, see setTimeLimit
    private int timeLimit;
    // the boardings found along trip transfers, by route: those to board in the round scanned, and in the next
    private @Nullable Map<Integer, List<PendingBoarding>> pendingBoardings;
    private @Nullable Map<Integer, List<PendingBoarding>> nextPendingBoardings;

    /**
     * @param queryState        the query state with the best time per stop and label per stop and round.
     * @param raptorData        the current raptor data structures.
     * @param queryConfig       the query config.
     * @param timeType          the time type (arrival or departure).
     * @param referenceDateTime the reference date time for the query.
     * @param maxDaysToScan     the maximum number of days to scan, without a time window.
     * @param timeWindow        the time window of a range query in seconds, 0 for none.
     * @param severalPasses     whether the query runs several passes, which find the same trip transfers again.
     */
    RouteScanner(QueryState queryState, RaptorData raptorData, QueryConfig queryConfig, TimeType timeType,
                 OffsetDateTime referenceDateTime, int maxDaysToScan, int timeWindow, boolean severalPasses) {
        // constant data structures
        this.stops = raptorData.getStopContext().stops();
        this.stopRoutes = raptorData.getStopContext().stopRoutes();
        this.routes = raptorData.getRouteTraversal().routes();
        this.routeStops = raptorData.getRouteTraversal().routeStops();
        this.tripTransfers = raptorData.getTripTransfers();

        // note: will also change outside of scanner, due to footpath relaxation
        this.queryState = queryState;

        // constant configuration of scanner
        this.minTransferDuration = queryConfig.getMinimumTransferDuration();
        this.maxWalkDuration = queryConfig.getMaximumWalkDuration();
        this.routesToScanMask = new BitSet(routes.length);
        this.timeType = timeType;
        this.forward = timeType == TimeType.DEPARTURE;
        this.timeDirection = forward ? 1 : -1;

        if (maxDaysToScan < 1) {
            throw new IllegalArgumentException("maxDaysToScan must be greater than 0.");
        }

        // a time window shifts the source times by up to its length: the days it reaches are scanned too, later ones
        // departing, earlier ones arriving
        LocalDate referenceDate = referenceDateTime.toLocalDate();
        OffsetDateTime windowEnd = referenceDateTime.plusSeconds((long) timeDirection * timeWindow);
        int windowDays = (int) Math.abs(ChronoUnit.DAYS.between(referenceDate, windowEnd.toLocalDate()));
        int actualDaysToScan;
        if (maxDaysToScan == 1) {
            // single-day scan
            actualDaysToScan = 1;
            startDayOffset = 0;

        } else if (forward) {
            // multi-day scan: the previous day is maybe needed in departure trips
            LocalDate previousDay = referenceDate.minusDays(1);
            DayStopTimes previousDayStopTimes = raptorData.getStopTimeProvider()
                    .getStopTimesForDate(previousDay, queryConfig);

            int departureTimeInPreviousDaySeconds = (int) Duration.between(previousDay.atStartOfDay(ZoneOffset.UTC),
                    referenceDateTime).toSeconds();

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
            // multi-day scan: there is no need to scan the next day for arrival trips
            actualDaysToScan = maxDaysToScan - 1;
            startDayOffset = 0;
        }

        days = new DayStopTimes[actualDaysToScan + windowDays];
        for (int i = 0; i < days.length; i++) {
            int dayOffset = i + startDayOffset;
            LocalDate date = forward ? referenceDate.plusDays(dayOffset) : referenceDate.minusDays(dayOffset);
            days[i] = raptorData.getStopTimeProvider().getStopTimesForDate(date, queryConfig);
        }

        furthestTripTimes = new int[routes.length];
        Arrays.fill(furthestTripTimes, NOT_READ);
        timeLimit = forward ? INFINITY : -INFINITY;
        knownTargets = severalPasses ? new KnownTargets[tripTransfers.numberOfGroups()] : null;
        this.severalPasses = severalPasses;
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

    private static QueryState.Label routeLabel(ActiveTrip activeTrip, int targetTime, int routeIdx, int stopIdx) {
        return new QueryState.Label(activeTrip.entryTime, targetTime, QueryState.LabelType.ROUTE, routeIdx,
                activeTrip.tripOffset, stopIdx, activeTrip.previousLabel);
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
     * Sets the source stops and the array their labels at the start of each pass are in. A source stop that another one
     * reaches earlier on foot boards from that walk, but under a limit of the first trip (see
     * {@link #setFirstTripLimit}) also from its own label, whose limit is later.
     *
     * @param stopIndices the indices of the source stops.
     * @param labels      the labels of the source stops, in the order of their indices, updated by each pass.
     */
    void setSourceLabels(int[] stopIndices, QueryState.Label[] labels) {
        sourceStopIndices = stopIndices;
        sourceLabels = labels;
    }

    /**
     * Sets the time beyond which the scan sets no label and boards no trip: later than it, or routing backward earlier.
     * A label beyond the best time at the target or the cut-off time of the query is removed when its round ends, and
     * every label that follows from it is beyond that time too: a trip boarded after it, a walk from it, and a trip
     * transfer from an arrival beyond it.
     *
     * @param timeLimit the time limit, or infinity (routing backward: minus infinity) for none.
     */
    void setTimeLimit(int timeLimit) {
        this.timeLimit = timeLimit;
    }

    /**
     * Whether a time is beyond the time limit, see {@link #setTimeLimit}.
     */
    private boolean isBeyondTimeLimit(int time) {
        return forward ? time > timeLimit : time < timeLimit;
    }

    /**
     * Scans all routes passing marked stops for the given round.
     *
     * @param round the current round.
     */
    void scan(int round) {
        pendingBoardings = nextPendingBoardings;
        nextPendingBoardings = null;
        if (pendingBoardings != null) {
            pendingBoardings.values()
                    .forEach(boardings -> boardings.forEach(boarding -> madeBoardings.add(boarding.key())));
        }
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

        // the routes boarded along trip transfers
        if (pendingBoardings != null) {
            pendingBoardings.keySet().forEach(routesToScanMask::set);
        }
    }

    /**
     * Whether boardings found along trip transfers wait for the next round, which a search has to run then, even if it
     * marked no stop.
     */
    boolean hasPendingBoardings() {
        return nextPendingBoardings != null && !nextPendingBoardings.isEmpty();
    }

    /**
     * Starts a pass of a query: the boardings found along trip transfers in a pass before are dropped, and those made
     * in it are forgotten, since a pass routes from other times.
     */
    void startPass() {
        madeBoardings.clear();
        pendingBoardings = null;
        nextPendingBoardings = null;
    }

    /**
     * Scan a route in time type applicable direction to find the best times for each stop on route for given round.
     *
     * @param currentRouteIdx the index of the current route.
     * @param round           the current round.
     */
    private void scanRoute(int currentRouteIdx, int round) {
        Route currentRoute = routes[currentRouteIdx];
        // without a trip on the days to scan, the route has no furthest trip time
        int furthestTripTime = getFurthestTripTimeOfRoute(currentRouteIdx);
        if (furthestTripTime == (forward ? -INFINITY : INFINITY)) {
            return;
        }

        int lastRound = round - 1;
        ActiveTrip activeTrip = null;

        int firstRouteStopIdx = currentRoute.firstRouteStopIdx();
        int numberOfStops = currentRoute.numberOfStops();

        int startOffset = forward ? 0 : numberOfStops - 1;
        int endOffset = forward ? numberOfStops : -1;

        // the stops with trip transfers are scanned by scanStopWithTransfers, the others as follows
        List<PendingBoarding> pending = pendingBoardings == null ? null : pendingBoardings.get(currentRouteIdx);
        boolean withTransfers = pending != null || tripTransfers.hasTransfers(timeType, currentRouteIdx);
        otherTrips.clear();

        for (int stopOffset = startOffset; stopOffset != endOffset; stopOffset += timeDirection) {
            int routeStopIdx = firstRouteStopIdx + stopOffset;
            RouteStop routeStop = routeStops[routeStopIdx];
            int stopIdx = routeStop.stopIndex();

            if (withTransfers && hasTransfersAt(routeStopIdx, stopOffset, pending)) {
                activeTrip = scanStopWithTransfers(activeTrip, routeStop, routeStopIdx, stopOffset, currentRouteIdx,
                        numberOfStops, round, pending);
                continue;
            }

            Stop stop = stops[stopIdx];
            int bestStopTime = queryState.getComparableBestTime(stopIdx);

            // find first marked stop in route
            if (activeTrip == null) {
                if (!canEnterAtStop(bestStopTime, stopIdx, stopOffset, furthestTripTime, numberOfStops)) {
                    continue;
                }
            } else if (canArrive(routeStop)) {
                // in this case we are on a trip and need to check if time has improved
                int stopTimeIndex = activeTrip.firstStopTimeIdx + 2 * stopOffset;
                // the stopTimeIndex points to the arrival time of the stop and stopTimeIndex + 1 to the departure time
                int targetTime = activeTrip.stopTimes[forward ? stopTimeIndex : stopTimeIndex + 1];
                targetTime += activeTrip.dayTimeOffset;

                if (!checkIfTripIsPossibleAndUpdateMarks(targetTime, activeTrip, bestStopTime, stopIdx, round,
                        lastRound, currentRouteIdx) && !(withTransfers && boardsBesides(activeTrip, stopIdx, stopOffset,
                        lastRound))) {
                    continue;
                }
            } else if (!canEnterAtStop(bestStopTime, stopIdx, stopOffset, furthestTripTime, numberOfStops)) {
                // the trip does not set down passengers at the stop (pick them up, routing backward), but a label of
                // the round before may still catch an earlier trip there
                continue;
            }

            if (!canBoard(routeStop)) {
                continue;
            }

            ActiveTrip possibleTrip = findPossibleTrip(stopIdx, stop, stopOffset, currentRouteIdx, numberOfStops,
                    lastRound);
            if (withTransfers) {
                activeTrip = board(activeTrip, possibleTrip, stopOffset);
            } else if (activeTrip == null || (possibleTrip != null && !isBetter(getTripTime(activeTrip, stopOffset),
                    possibleTrip.entryTime()))) {
                // a trip boarded here replaces the active trip unless it is worse: the stop's label may be in time for
                // an earlier (later, routing backward) trip than the active one, or only for a worse one, or for none,
                // once the transfer time is added
                activeTrip = possibleTrip;
            }
        }
    }

    /**
     * Whether a trip may be boarded at a stop after its label of the round before, which is no earlier (later, routing
     * backward) than the active trip there: if the active trip and the other trips ridden that may change to every
     * later trip reach the stop later still, or there are none, see {@link #covers}.
     */
    private boolean boardsBesides(ActiveTrip activeTrip, int stopIdx, int stopOffset, int lastRound) {
        if (coversAll(activeTrip) || !queryState.isMarkedThisRound(stopIdx)) {
            return false;
        }
        QueryState.Label label = queryState.getLabel(lastRound, stopIdx);
        ActiveTrip coveringTrip = tripCoveringAll(activeTrip);
        return label != null && (coveringTrip == null || isBetter(label.targetTime(),
                getArrivalTime(coveringTrip, stopOffset)));
    }

    /**
     * Boards a trip at a stop of a route with trip transfers: it replaces the active trip unless it is worse, as in
     * {@link #scanRoute}, and either trip is ridden on besides it unless the other covers it.
     *
     * @return the active trip, the earliest (latest, routing backward) ridden.
     */
    private @Nullable ActiveTrip board(@Nullable ActiveTrip activeTrip, @Nullable ActiveTrip trip, int stopOffset) {
        if (trip == null) {
            return activeTrip;
        }
        if (activeTrip == null) {
            return trip;
        }
        if (isBetter(getTripTime(activeTrip, stopOffset), trip.entryTime())) {
            if (!covers(activeTrip, trip, stopOffset) && otherTrips.stream()
                    .noneMatch(other -> covers(other, trip, stopOffset))) {
                otherTrips.removeIf(other -> covers(trip, other, stopOffset));
                otherTrips.add(trip);
            }
            return activeTrip;
        }
        otherTrips.removeIf(other -> covers(trip, other, stopOffset));
        if (!covers(trip, activeTrip, stopOffset)) {
            otherTrips.add(activeTrip);
        }
        return trip;
    }

    /**
     * Whether a trip ridden on a route covers another at a stop: everything the other leads to from there, the first
     * leads to as well, no later (earlier, routing backward). It does if it is no later and may change to every trip
     * the other may change to: the other itself and the later trips of the route, see {@link #addPendingBoardings}. A
     * trip boarded at a stop may change to every later trip (see {@link #coversAll}), one boarded along a trip transfer
     * to those the transfer allows: so it covers only a trip boarded at the same stop along the same group of trip
     * transfers, from the same trip, reaching the stop no later (leaving it no earlier, routing backward).
     */
    private boolean covers(ActiveTrip trip, ActiveTrip other, int stopOffset) {
        if (isAfter(trip, other, stopOffset)) {
            return false;
        }
        BoardingByRule byRule = trip.byRule;
        if (byRule == null) {
            return !isLimited(trip) || coversWithinLimit(trip, other);
        }
        BoardingByRule otherByRule = other.byRule;
        boolean sameStop = trip.entryOffset == other.entryOffset;
        return otherByRule != null && sameStop && byRule.isFromSameTrip(otherByRule) && !isBetter(
                otherByRule.knownTime(), byRule.knownTime());
    }

    /**
     * Whether the first trip of a journey of a range query, which changes only to the later trips leaving within a
     * limit where it was boarded, covers another trip: a first trip too, boarded where it was or later on the route
     * (earlier, routing backward), with a limit no later (earlier, routing backward).
     */
    private boolean coversWithinLimit(ActiveTrip trip, ActiveTrip other) {
        boolean boardedBefore = timeDirection * trip.entryOffset <= timeDirection * other.entryOffset;
        return other.byRule == null && isLimited(other) && boardedBefore && !isBetter(trip.previousLabel.targetTime(),
                other.previousLabel.targetTime());
    }

    /**
     * Whether a trip ridden on a route may change to every later trip of it, see {@link #addPendingBoardings}.
     */
    private boolean coversAll(ActiveTrip trip) {
        return trip.byRule == null && !isLimited(trip);
    }

    /**
     * The earliest (latest, routing backward) trip ridden that may change to every later trip, or null.
     */
    private @Nullable ActiveTrip tripCoveringAll(@Nullable ActiveTrip activeTrip) {
        if (activeTrip == null || coversAll(activeTrip)) {
            return activeTrip;
        }
        for (ActiveTrip other : otherTrips) {
            if (coversAll(other)) {
                return other;
            }
        }
        return null;
    }

    /**
     * Whether the first trip of a journey may only lead to later trips leaving within a limit, see
     * {@link #setFirstTripLimit}.
     */
    private boolean isLimited(ActiveTrip trip) {
        return firstTripLimit != NO_LIMIT && isStartOfJourney(trip.previousLabel);
    }

    /**
     * Whether a trip of a route is after another (before it, routing backward): on the same day by their order, which
     * the trips keep at every stop, else by their times at the stop.
     */
    private boolean isAfter(ActiveTrip trip, ActiveTrip other, int stopOffset) {
        if (trip.dayIndex == other.dayIndex) {
            return timeDirection * trip.firstStopTimeIdx > timeDirection * other.firstStopTimeIdx;
        }
        return isBetter(getTripTime(other, stopOffset), getTripTime(trip, stopOffset));
    }

    /**
     * Whether a stop of a route takes part in trip transfers for the search (see {@link TripTransfers}): the arrivals
     * of the route there go to a slot and start groups, the stop has slots to board after, or a boarding found along a
     * trip transfer is at it.
     */
    private boolean hasTransfersAt(int routeStopIdx, int stopOffset, @Nullable List<PendingBoarding> pending) {
        if (tripTransfers.hasTransfersAt(timeType, routeStopIdx)) {
            return true;
        }
        if (pending != null) {
            for (PendingBoarding boarding : pending) {
                if (boarding.stopOffset() == stopOffset) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Scans a stop of a route that takes part in trip transfers: the active trip reaches it at the slot of the route
     * there, if the route has one, and starts the groups of the route there; and a trip is boarded after the arrival at
     * the stop and those in its slots, unless the trip in a slot changes to this route by a rule (see
     * {@link #changesByRule}), as {@link #scanRoute} boards after the arrival at a stop, or at a boarding found along a
     * trip transfer.
     *
     * @return the trip to ride on from the stop.
     */
    private @Nullable ActiveTrip scanStopWithTransfers(@Nullable ActiveTrip activeTrip, RouteStop routeStop,
                                                       int routeStopIdx, int stopOffset, int routeIdx,
                                                       int numberOfStops, int round,
                                                       @Nullable List<PendingBoarding> pending) {
        int stopIdx = routeStop.stopIndex();
        if (activeTrip != null) {
            // a trip that sets down no one at the stop (picks up no one, routing backward) reaches it only to stay on
            // board, see addPendingBoarding: it records no arrival to change from
            boolean arrives = canArrive(routeStop);
            int arrivalIdx = tripTransfers.arrivalIndex(timeType, routeStopIdx, stopIdx);
            TripTransfers.Group @Nullable [] groups = tripTransfers.groups(timeType, routeStopIdx);
            int activeTime = getArrivalTime(activeTrip, stopOffset);
            if (!isBeyondTimeLimit(activeTime)) {
                boolean boardsAfterArrival = arrives && updateArrival(activeTime, activeTrip, arrivalIdx, round,
                        routeIdx);
                if (groups != null) {
                    addPendingBoardings(groups, activeTrip, activeTime, stopOffset, arrivalIdx, routeIdx,
                            boardsAfterArrival);
                }
            }
            // the other trips reach the stop later than the active trip: only their trip transfers can do more
            otherTrips.removeIf(other -> isBeyondTimeLimit(getArrivalTime(other, stopOffset)));
            if (groups != null) {
                for (ActiveTrip other : otherTrips) {
                    addPendingBoardings(groups, other, getArrivalTime(other, stopOffset), stopOffset, arrivalIdx,
                            routeIdx, false);
                }
            }
        }

        boolean endOfRoute = forward ? stopOffset == numberOfStops - 1 : stopOffset == 0;
        if (endOfRoute) {
            return activeTrip;
        }
        if (!canBoard(routeStop)) {
            // only a trip the rider stays on board of is boarded where it picks up no one (sets down no one)
            return boardPending(activeTrip, pending, stopOffset);
        }

        // a label no earlier (later, routing backward) than a trip ridden that covers all reaches nothing it does not
        Stop stop = stops[stopIdx];
        ActiveTrip coveringTrip = tripCoveringAll(activeTrip);
        int coveredTime = coveringTrip == null ? 0 : getArrivalTime(coveringTrip, stopOffset);
        ActiveTrip possibleTrip = boardAfter(stopIdx, stop, stopOffset, routeIdx, numberOfStops, round - 1,
                coveringTrip != null, coveredTime);
        int[] slots = tripTransfers.slots(timeType, stopIdx);
        if (slots != null) {
            for (int slot : slots) {
                // few slots were reached in the round before
                if (queryState.isMarkedThisRound(slot) && !changesByRule(slot, routeIdx, round - 1)) {
                    possibleTrip = better(possibleTrip,
                            boardAfter(slot, stop, stopOffset, routeIdx, numberOfStops, round - 1, coveringTrip != null,
                                    coveredTime));
                }
            }
        }

        return boardPending(board(activeTrip, possibleTrip, stopOffset), pending, stopOffset);
    }

    /**
     * Boards the trips found along trip transfers at a stop of the route.
     *
     * @return the trip to ride on from the stop.
     */
    private @Nullable ActiveTrip boardPending(@Nullable ActiveTrip activeTrip, @Nullable List<PendingBoarding> pending,
                                              int stopOffset) {
        if (pending != null) {
            for (PendingBoarding boarding : pending) {
                if (boarding.stopOffset() == stopOffset) {
                    activeTrip = board(activeTrip, boarding.trip(), stopOffset);
                }
            }
        }
        return activeTrip;
    }

    /**
     * Records the arrival of the active trip at a stop or slot, as {@link #checkIfTripIsPossibleAndUpdateMarks} does.
     *
     * @return whether the next round boards after the arrival, which it does if the arrival improved the stop or slot.
     */
    private boolean updateArrival(int targetTime, ActiveTrip activeTrip, int idx, int round, int routeIdx) {
        QueryState.Label label = new QueryState.Label(activeTrip.entryTime, targetTime, QueryState.LabelType.ROUTE,
                routeIdx, activeTrip.tripOffset, idx, activeTrip.previousLabel);
        if (!stops[idx].refusesTransfers() && isBetter(targetTime, queryState.getComparableBestTime(idx))) {
            queryState.setBestTime(idx, targetTime);
            queryState.setLabel(round, idx, label);
            queryState.setTripLabel(idx, label);
            queryState.mark(idx);
            return true;
        } else if (isBetter(targetTime, queryState.getBestTripTime(idx))) {
            queryState.setTripLabel(idx, label);
        }
        return false;
    }

    /**
     * Whether the trip that reached a slot, on it or before walking to it, changes to a route by the rules of a group
     * of trip transfers, so that the slot does not board the route as its stop would.
     */
    private boolean changesByRule(int slot, int routeIdx, int lastRound) {
        TripTransfers.Group group = tripTransfers.governingGroup(slot, routeIdx);
        if (group == null) {
            return false;
        }
        QueryState.Label label = queryState.getLabel(lastRound, slot);
        QueryState.Label tripLabel = label == null || label.type() == QueryState.LabelType.ROUTE ? label : label.previous();
        return tripLabel == null || group.hasRules(tripLabel.tripOffset(), forward);
    }

    /**
     * Finds the trip to board at a stop after the label of the round before at the stop or one of its slots, if that
     * round improved it: with a trip ridden that covers all only if the label is earlier (later, routing backward) than
     * that trip at the stop, since only then it can catch a trip that trip does not cover, as
     * {@link #checkIfTripIsPossibleAndUpdateMarks} tells; and without one, as {@link #canEnterAtStop} tells.
     *
     * @param covered     whether a trip ridden covers all, see {@link #tripCoveringAll}.
     * @param coveredTime the time that trip reaches the stop at.
     */
    private @Nullable ActiveTrip boardAfter(int labelIdx, Stop stop, int stopOffset, int routeIdx, int numberOfStops,
                                            int lastRound, boolean covered, int coveredTime) {
        if (!queryState.isMarkedThisRound(labelIdx)) {
            return null;
        }
        QueryState.Label label = queryState.getLabel(lastRound, labelIdx);
        if (label == null) {
            return null;
        }
        if (covered ? !isBetter(label.targetTime(), coveredTime) : isBetter(getFurthestTripTimeOfRoute(routeIdx),
                queryState.getComparableBestTime(labelIdx))) {
            return null;
        }
        return findPossibleTripAfter(label, stop, stopOffset, routeIdx, numberOfStops);
    }

    /**
     * Of two trips to board at a stop, the better one, or the first of equally good ones.
     */
    private @Nullable ActiveTrip better(@Nullable ActiveTrip trip, @Nullable ActiveTrip otherTrip) {
        if (trip == null) {
            return otherTrip;
        }
        if (otherTrip == null) {
            return trip;
        }
        return isBetter(otherTrip.entryTime(), trip.entryTime()) ? otherTrip : trip;
    }

    /**
     * Finds the boardings along the groups of trip transfers a route stop starts, for the next round: for each group
     * and each call of the route it ends at (routing backward: starts from) at its stop, the best trip of that route a
     * trip of this route allows changing to there, since a trip reached too late at one call may be reached in time at
     * another. The trips of this route are those that can be boarded where the active trip was, the active trip and
     * those after it (before it, routing backward) on every day to scan: one trip may refuse a change that a later one
     * allows, also one of another service day. A trip boarded along a trip transfer is the first that transfer allows,
     * so a later trip counts only if the transfer allows it too. The trips of a day are read until one reaches the stop
     * no earlier than the best trip found leaves.
     * <p>
     * A trip no rule of a group applies to changes as the stops allow: from the stop, or from its slot if the next
     * round boards after its arrival there (see {@link #changesByRule}), and every trip if the group is not
     * restrictive. Then only a trip with rules can do better, and only one that reaches the stop before the active trip
     * has changed.
     *
     * @param boardsAfterArrival whether the next round boards after the arrival of the active trip.
     */
    private void addPendingBoardings(TripTransfers.Group[] groups, ActiveTrip activeTrip, int activeTime,
                                     int stopOffset, int arrivalIdx, int routeIdx, boolean boardsAfterArrival) {
        for (TripTransfers.Group group : groups) {
            int[] targetOffsets = forward ? group.toOffsets : group.fromOffsets;
            for (int call = 0; call < targetOffsets.length; call++) {
                addPendingBoarding(group, call, targetOffsets[call], activeTrip, activeTime, stopOffset, arrivalIdx,
                        routeIdx, boardsAfterArrival);
            }
        }
    }

    /**
     * Finds the boarding along a group of trip transfers at a call of the route it ends at (routing backward: starts
     * from), see {@link #addPendingBoardings}.
     *
     * @param call         the number of the call among the calls of the route at the stop of the group.
     * @param targetOffset the position of the call on the route.
     */
    private void addPendingBoarding(TripTransfers.Group group, int call, int targetOffset, ActiveTrip activeTrip,
                                    int activeTime, int stopOffset, int arrivalIdx, int routeIdx,
                                    boolean boardsAfterArrival) {
        // where the trip sets down no one (picks up no one, routing backward), or the call of the other route picks up
        // no one (sets down no one), the rider can only stay on board, which no change by the stops is
        RouteStop routeStop = routeStops[routes[routeIdx].firstRouteStopIdx() + stopOffset];
        boolean inSeat = !canArrive(routeStop) || !(forward ? group.toBoarding : group.fromAlighting)[call];
        if (inSeat && !group.inSeat) {
            return;
        }
        int tripStopTimes = 2 * routes[routeIdx].numberOfStops();
        int activeTripNumber = (activeTrip.firstStopTimeIdx - days[activeTrip.dayIndex].routeStopTimes()[routeIdx]) / tripStopTimes;
        boolean limited = firstTripLimit != NO_LIMIT && isStartOfJourney(activeTrip.previousLabel);
        int limitTime = limited ? activeTrip.previousLabel.targetTime() + timeDirection * firstTripLimit : 0;

        boolean activeHasRules = group.hasRules(activeTrip.tripOffset, forward);
        boolean changesByStops = !inSeat && (!group.restrictive || (boardsAfterArrival && !activeHasRules));
        int stopTransferTime = changesByStops ? transferTime(group, TripTransfers.DEFAULT) : NOT_POSSIBLE;
        int changedByStops = activeTime + timeDirection * stopTransferTime;

        TransferTarget best = null;
        int bestPosition = 0;
        int bestTime = 0;
        int bestEntryTime = 0;
        for (int dayIndex = 0; dayIndex < days.length; dayIndex++) {
            DayStopTimes day = days[dayIndex];
            int numberOfTrips = day.numberOfTrips(routeIdx);
            if (numberOfTrips == 0) {
                continue;
            }

            int[] stopTimes = day.stopTimes();
            int routeStopTimes = day.routeStopTimes()[routeIdx];
            int timeOffset = timeDirection * (dayIndex + startDayOffset) * SECONDS_IN_DAY;
            // the times the trips of the day are boarded at where the active trip was
            int entryTimeIdx = routeStopTimes + 2 * activeTrip.entryOffset + (forward ? 1 : 0);
            int firstTrip;
            if (dayIndex == activeTrip.dayIndex) {
                firstTrip = activeTripNumber;
            } else {
                // another day can do better only if its first trip (routing backward: its last) reaches the stop
                // in time, and if one of its trips is boarded after the active trip (before it, routing backward)
                int dayTime = stopTimes[routeStopTimes + (forward ? 0 : numberOfTrips - 1) * tripStopTimes + 2 * stopOffset + (forward ? 0 : 1)] + timeOffset;
                int furthestTime = (forward ? day.routeLatest()[routeIdx] : day.routeEarliest()[routeIdx]) + timeOffset;
                if ((best != null && !isBetter(dayTime,
                        best.entryTime())) || (stopTransferTime != NOT_POSSIBLE && !isBetter(dayTime,
                        changedByStops)) || isBetter(furthestTime, activeTrip.entryTime)) {
                    continue;
                }
                firstTrip = findFirstPossibleTrip(stopTimes, entryTimeIdx, tripStopTimes, numberOfTrips,
                        activeTrip.entryTime - timeOffset);
            }

            for (int trip = firstTrip; trip >= 0 && trip < numberOfTrips; trip += timeDirection) {
                // when the trip reaches the stop, and when it is boarded where the active trip was
                int time = stopTimes[routeStopTimes + trip * tripStopTimes + 2 * stopOffset + (forward ? 0 : 1)] + timeOffset;
                if ((best != null && !isBetter(time, best.entryTime())) || isBeyondTimeLimit(time)) {
                    break;
                }
                if (stopTransferTime != NOT_POSSIBLE && !isBetter(time, changedByStops)) {
                    break;
                }
                int entryTime = stopTimes[entryTimeIdx + trip * tripStopTimes] + timeOffset;
                if (limited && isBetter(limitTime, entryTime)) {
                    break;
                }

                int position = day.tripPosition(routeIdx, trip);
                boolean active = dayIndex == activeTrip.dayIndex && trip == activeTripNumber;
                boolean hasRules = active ? activeHasRules : group.hasRules(position, forward);
                if (changesByStops && !hasRules) {
                    continue;
                }
                if (!group.mayChange(position, forward)) {
                    continue;
                }
                // whether a later trip can be boarded where the active trip was is asked of the few that do better
                TransferTarget target = inSeat ? findTripTransferTarget(group, targetOffset, position, time,
                        true) : findTripTransferTarget(group, call, targetOffset, position, time, dayIndex, trip);
                if (target != null && (best == null || isBetter(target.entryTime(),
                        best.entryTime())) && (active || isBoardedLikeActiveTrip(activeTrip, position, entryTime))) {
                    best = target;
                    bestPosition = position;
                    bestTime = time;
                    bestEntryTime = entryTime;
                }
            }
        }
        if (best == null || isBeyondTimeLimit(best.entryTime())) {
            return;
        }
        // the labels of a query of several passes may be of a pass before, which boarded after them; and a call that
        // picks up no one is not boarded at the stop
        boolean boardingCall = (forward ? group.toBoarding : group.fromAlighting)[call];
        if (!severalPasses && boardingCall && boardsAtStop(forward ? group.toStopIdx : group.fromStopIdx,
                best.entryTime())) {
            return;
        }

        // the labels the boarding follows: the trip of this route, and between two stops, the transfer as a walk, or
        // staying on board, which is no walk but tells the stop the next trip leaves from (routing backward: reaches)
        QueryState.Label previousLabel = new QueryState.Label(bestEntryTime, bestTime, QueryState.LabelType.ROUTE,
                routeIdx, bestPosition, arrivalIdx, activeTrip.previousLabel);
        if (!group.isAtOneStop()) {
            int rule = forward ? group.rule(bestPosition, best.tripOffset()) : group.rule(best.tripOffset(),
                    bestPosition);
            QueryState.LabelType type = rule == TripTransfers.IN_SEAT ? QueryState.LabelType.IN_SEAT : QueryState.LabelType.TRANSFER;
            previousLabel = new QueryState.Label(bestTime, bestTime + timeDirection * best.transferTime(), type,
                    QueryState.NO_INDEX, QueryState.NO_INDEX, forward ? group.toStopIdx : group.fromStopIdx,
                    previousLabel);
        }
        addPendingBoarding(forward ? group.toRouteIdx : group.fromRouteIdx, targetOffset,
                new ActiveTrip(best.tripOffset(), best.entryTime(), best.dayTimeOffset(), previousLabel,
                        best.stopTimes(), best.firstStopTimeIdx(), best.dayIndex(), best.entryOffset(),
                        new BoardingByRule(group, bestPosition, bestTime, inSeat)));
    }

    /**
     * Whether the labels at a stop let the scan board a trip leaving at a time (arriving, routing backward), as it does
     * in the round after them: that trip or an earlier one is then boarded at the stop with every later trip, so it
     * covers a boarding of that trip along a trip transfer (see {@link #covers}), with as many transfers or fewer.
     */
    private boolean boardsAtStop(int stopIdx, int time) {
        int bestTime = queryState.getComparableBestTime(stopIdx);
        if (bestTime == (forward ? INFINITY : -INFINITY)) {
            return false;
        }
        int transferTime = Math.max(stops[stopIdx].sameStopTransferTime(), minTransferDuration);
        return !isBetter(time, bestTime + timeDirection * transferTime);
    }

    /**
     * Whether a trip of the active trip's route can be boarded where the active trip was, as it is no earlier than the
     * active trip (no later, routing backward): every such trip can, unless the active trip was boarded along a trip
     * transfer, which then has to allow the trip too.
     *
     * @param position  the position of the trip on the route.
     * @param entryTime the time the trip is boarded at (alighted from, routing backward) where the active trip was.
     */
    private boolean isBoardedLikeActiveTrip(ActiveTrip activeTrip, int position, int entryTime) {
        BoardingByRule byRule = activeTrip.byRule;
        return byRule == null || changeTime(byRule.group(), byRule.knownPosition(), byRule.knownTime(), position,
                entryTime, byRule.inSeat()) != NOT_POSSIBLE;
    }

    /**
     * Adds a boarding found along a trip transfer for the next round, unless the pass has made it already, or one at
     * the same stop of the route covers it (see {@link #covers}); it replaces those it covers. An earlier trip alone
     * does not cover a later one: the later one may change to trips the earlier one may not.
     */
    private void addPendingBoarding(int routeIdx, int stopOffset, ActiveTrip trip) {
        PendingBoarding pendingBoarding = new PendingBoarding(stopOffset, trip);
        if (madeBoardings.contains(pendingBoarding.key())) {
            return;
        }
        if (nextPendingBoardings == null) {
            nextPendingBoardings = new HashMap<>();
        }
        List<PendingBoarding> boardings = nextPendingBoardings.computeIfAbsent(routeIdx, _ -> new ArrayList<>(1));
        for (PendingBoarding boarding : boardings) {
            if (boarding.stopOffset() == stopOffset && covers(boarding.trip(), trip, stopOffset)) {
                return;
            }
        }
        boardings.removeIf(
                boarding -> boarding.stopOffset() == stopOffset && covers(trip, boarding.trip(), stopOffset));
        boardings.add(pendingBoarding);
    }

    /**
     * Finds the trip a group of trip transfers allows changing to at a call from a trip of a day to scan, see
     * {@link #findTripTransferTarget(TripTransfers.Group, int, int, int, boolean)}. The trip found depends on the
     * timetable and the configuration of the query alone, so a query of several passes, which finds the same changes
     * pass after pass, keeps it for each call.
     *
     * @param call          the number of the call among the calls of the route at the stop of the group.
     * @param knownDayIndex the index of the day of the known trip among the days to scan.
     * @param knownTrip     the number of the known trip among the trips of its route on that day.
     */
    private @Nullable TransferTarget findTripTransferTarget(TripTransfers.Group group, int call, int offset,
                                                            int knownPosition, int time, int knownDayIndex,
                                                            int knownTrip) {
        if (knownTargets == null) {
            return findTripTransferTarget(group, offset, knownPosition, time, false);
        }

        int knownRouteIdx = forward ? group.fromRouteIdx : group.toRouteIdx;
        KnownTargets known = knownTargets[group.index];
        if (known == null) {
            int numberOfTrips = 0;
            for (DayStopTimes day : days) {
                numberOfTrips += day.numberOfTrips(knownRouteIdx);
            }
            known = new KnownTargets(numberOfTrips, (forward ? group.toOffsets : group.fromOffsets).length);
            knownTargets[group.index] = known;
        }

        int trip = call * known.numberOfTrips + knownTrip;
        for (int dayIndex = 0; dayIndex < knownDayIndex; dayIndex++) {
            trip += days[dayIndex].numberOfTrips(knownRouteIdx);
        }
        // a route that calls at the stop of the group twice reaches it at two times
        if (known.times[trip] != time) {
            known.times[trip] = time;
            known.targets[trip] = findTripTransferTarget(group, offset, knownPosition, time, false);
        }

        return known.targets[trip];
    }

    /**
     * Finds the trip a group of trip transfers allows changing to from a trip reaching the stop the group starts at, at
     * a time: the first trip of the route the group ends at that leaves its stop once the transfer of the pair of trips
     * has taken its time, on the days to scan. Routing backward, the trip known is the one the group ends at, leaving
     * at the time, and the trip found the last of the route the group starts from arriving in time for it. The trips
     * are read in their order until one is possible: a trip transfer may refuse a trip and allow a later one, or allow
     * a change in no time to a trip leaving much later.
     *
     * @param group         the group.
     * @param offset        the position of the call of the route at the stop the trip is found at.
     * @param knownPosition the position of the known trip on its route.
     * @param time          the time the known trip reaches (routing backward: leaves) its stop.
     * @param inSeat        whether only a rule to stay on board allows the change.
     * @return the trip, or null if no trip of the route is possible.
     */
    private @Nullable TransferTarget findTripTransferTarget(TripTransfers.Group group, int offset, int knownPosition,
                                                            int time, boolean inSeat) {
        int routeIdx = forward ? group.toRouteIdx : group.fromRouteIdx;
        int tripStopTimes = 2 * routes[routeIdx].numberOfStops();

        TransferTarget best = null;
        for (int dayIndex = 0; dayIndex < days.length; dayIndex++) {
            DayStopTimes day = days[dayIndex];
            int numberOfTrips = day.numberOfTrips(routeIdx);
            if (numberOfTrips == 0) {
                continue;
            }

            int timeOffset = timeDirection * (dayIndex + startDayOffset) * SECONDS_IN_DAY;
            int firstTripTimeIdx = day.routeStopTimes()[routeIdx] + 2 * offset + (forward ? 1 : 0);
            int trip = findFirstPossibleTrip(day.stopTimes(), firstTripTimeIdx, tripStopTimes, numberOfTrips,
                    time - timeOffset);
            for (; trip >= 0 && trip < numberOfTrips; trip += timeDirection) {
                int tripTime = day.stopTimes()[firstTripTimeIdx + trip * tripStopTimes] + timeOffset;
                if (best != null && !isBetter(tripTime, best.entryTime())) {
                    break;
                }

                int position = day.tripPosition(routeIdx, trip);
                int transferTime = changeTime(group, knownPosition, time, position, tripTime, inSeat);
                if (transferTime == NOT_POSSIBLE) {
                    continue;
                }

                best = new TransferTarget(position, tripTime, timeOffset, day.stopTimes(),
                        day.routeStopTimes()[routeIdx] + trip * tripStopTimes, dayIndex, offset, transferTime);
                break;
            }
        }

        return best;
    }

    /**
     * Gets the time a group of trip transfers takes to change from a known trip to another trip, or
     * {@link #NOT_POSSIBLE} if the rule of the pair refuses the change or the other trip leaves (arrives, routing
     * backward) too early (late) for it.
     *
     * @param knownPosition the position of the known trip on its route.
     * @param time          the time the known trip reaches (routing backward: leaves) its stop.
     * @param position      the position of the other trip on its route.
     * @param tripTime      the time the other trip leaves (routing backward: reaches) its stop.
     * @param inSeat        whether only a rule to stay on board allows the change.
     */
    private int changeTime(TripTransfers.Group group, int knownPosition, int time, int position, int tripTime,
                           boolean inSeat) {
        int rule = forward ? group.rule(knownPosition, position) : group.rule(position, knownPosition);
        if (inSeat && rule != TripTransfers.IN_SEAT) {
            return NOT_POSSIBLE;
        }
        int transferTime = transferTime(group, rule);
        if (transferTime == NOT_POSSIBLE || isBetter(tripTime, time + timeDirection * transferTime)) {
            return NOT_POSSIBLE;
        }
        return transferTime;
    }

    /**
     * Gets the time a rule of a group of trip transfers takes for the search, or {@link #NOT_POSSIBLE}: at one stop at
     * least the minimum transfer duration of the query, as the same stop transfer time, and between two stops the time
     * and that duration, as a walk, which is no longer than the longest walk of the query.
     */
    private int transferTime(TripTransfers.Group group, int rule) {
        if (rule == TripTransfers.IN_SEAT) {
            return 0;
        } else if (rule == TripTransfers.NOT_POSSIBLE) {
            return NOT_POSSIBLE;
        }

        int time = switch (rule) {
            case TripTransfers.STOP_TIME -> group.stopTime;
            case TripTransfers.DEFAULT -> group.defaultTime;
            default -> rule;
        };
        if (time == TripTransfers.NOT_POSSIBLE) {
            return NOT_POSSIBLE;
        } else if (group.isAtOneStop()) {
            return Math.max(time, minTransferDuration);
        }
        return time > maxWalkDuration ? NOT_POSSIBLE : time + minTransferDuration;
    }

    /**
     * Gets the time a trip reaches a stop of its route at: its arrival, or routing backward its departure.
     */
    private int getArrivalTime(ActiveTrip trip, int stopOffset) {
        int stopTimeIndex = trip.firstStopTimeIdx + 2 * stopOffset;
        return trip.stopTimes[forward ? stopTimeIndex : stopTimeIndex + 1] + trip.dayTimeOffset;
    }

    /**
     * Whether a trip of the route reaches the stop for the search: sets down passengers there, or routing backward,
     * picks them up there, since the search then rides it from the stop on.
     */
    private boolean canArrive(RouteStop routeStop) {
        return forward ? routeStop.alighting() : routeStop.boarding();
    }

    /**
     * Whether a trip of the route can be taken at the stop for the search: picks up passengers there, or routing
     * backward, sets them down there, since the search then rides it to the stop.
     */
    private boolean canBoard(RouteStop routeStop) {
        return forward ? routeStop.boarding() : routeStop.alighting();
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

    /**
     * This method checks if a trip can be entered at the stop in the current round. A trip can be entered if the stop
     * was reached in a previous round, and is not the first (targetTime) / last (sourceTime) stop of a trip or (for
     * performance reasons) assuming that this check is only run when not traveling with an active trip, the stop was
     * not marked in a previous round (i.e., the lasts round trip query would be repeated).
     *
     * @param stopTime         the time at the stop.
     * @param stopIdx          the index of the stop to check if a trip can be entered.
     * @param stopOffset       the offset of the stop in the route.
     * @param furthestTripTime the furthest trip time of the current route, see {@link #getFurthestTripTimeOfRoute}.
     * @param numberOfStops    the number of stops of the current route.
     */
    private boolean canEnterAtStop(int stopTime, int stopIdx, int stopOffset, int furthestTripTime, int numberOfStops) {
        int unreachableValue = forward ? INFINITY : -INFINITY;
        if (stopTime == unreachableValue) {
            return false;
        }

        // no trip of the route departs after the time at the stop (arrives before it, routing backward)
        if (isBetter(furthestTripTime, stopTime)) {
            return false;
        }

        if (!queryState.isMarkedThisRound(stopIdx)) {
            // this stop has already been scanned in previous round without improved target time
            return false;
        }

        // the last stop in the route (the first, routing backward) has no trip to enter; else a marked stop is found
        return forward ? stopOffset + 1 != numberOfStops : stopOffset != 0;
    }

    /**
     * Get the latest possible stop time for a route on all days to scan (for time type DEPARTURE) or the earliest
     * possible stop time for a route on all days to scan (for time type ARRIVAL).
     * <p>
     * Returns -INFINITY for DEPARTURE and INFINITY for ARRIVAL if no trip is possible. The time is the same at every
     * stop of the route and in every round of the query, so it is read from the days once per route.
     *
     * @param routeIdx the index of the route to get the furthest trip time from.
     * @return the furthest trip time of the route.
     */
    private int getFurthestTripTimeOfRoute(int routeIdx) {
        int furthestTripTime = furthestTripTimes[routeIdx];
        if (furthestTripTime == NOT_READ) {
            furthestTripTime = readFurthestTripTimeOfRoute(routeIdx);
            furthestTripTimes[routeIdx] = furthestTripTime;
        }

        return furthestTripTime;
    }

    private int readFurthestTripTimeOfRoute(int routeIdx) {
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
        // at a stop that refuses transfers, an arrival on a trip boards no other trip, so it is not one for boarding
        boolean isImproved = !stops[stopIdx].refusesTransfers() && (forward ? targetTime < bestStopTime : targetTime > bestStopTime);
        // an arrival beyond the time limit sets no label: an earlier trip may still be boarded at the stop
        boolean isInTime = !isBeyondTimeLimit(targetTime);

        if (isImproved && isInTime) {
            queryState.setBestTime(stopIdx, targetTime);

            QueryState.Label label = routeLabel(activeTrip, targetTime, currentRouteIdx, stopIdx);
            queryState.setLabel(thisRound, stopIdx, label);
            queryState.setTripLabel(stopIdx, label);
            queryState.mark(stopIdx);

            return false;
        } else {
            if (isInTime && isBetter(targetTime, queryState.getBestTripTime(stopIdx))) {
                // the earliest arrival on a trip so far, which walks start from
                queryState.setTripLabel(stopIdx, routeLabel(activeTrip, targetTime, currentRouteIdx, stopIdx));
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
        QueryState.Label label = queryState.getLabel(lastRound, stopIdx);
        assert label != null : "a stop to board at has a label of the last round";
        return findPossibleTripAfter(label, stop, stopOffset, routeIdx, numberOfStops);
    }

    /**
     * Finds the possible trip on a route after a label at a stop, or where a walk from a source stop reached another
     * one under a limit of the first trip, after either the walk or the source stop's own label: the later label may be
     * in time for a trip beyond the limit of the walk, and of equally good trips, the one without the walk is taken.
     */
    private @Nullable ActiveTrip findPossibleTripAfter(QueryState.Label label, Stop stop, int stopOffset, int routeIdx,
                                                       int numberOfStops) {
        ActiveTrip trip = findPossibleTrip(label, stop, stopOffset, routeIdx, numberOfStops);
        if (firstTripLimit == NO_LIMIT || label.type() != QueryState.LabelType.TRANSFER || !isStartOfJourney(label)) {
            return trip;
        }

        for (int i = 0; i < sourceStopIndices.length; i++) {
            if (sourceStopIndices[i] == label.stopIdx()) {
                return better(findPossibleTrip(sourceLabels[i], stop, stopOffset, routeIdx, numberOfStops), trip);
            }
        }
        return trip;
    }

    /**
     * Finds the possible trip on a route after a label at a stop, see
     * {@link #findPossibleTrip(int, Stop, int, int, int, int)}.
     */
    private @Nullable ActiveTrip findPossibleTrip(QueryState.Label previousLabel, Stop stop, int stopOffset,
                                                  int routeIdx, int numberOfStops) {
        // this is the reference time, where we can depart after or arrive earlier
        int referenceTime = previousLabel.targetTime();
        if (previousLabel.type() == QueryState.LabelType.ROUTE) {
            // no trip is changed for another at a stop that refuses transfers
            if (stop.refusesTransfers()) {
                return null;
            }
            referenceTime += forward ? Math.max(stop.sameStopTransferTime(), minTransferDuration) : -Math.max(
                    stop.sameStopTransferTime(), minTransferDuration);
        }

        // the first trip of a journey may have to leave within a limit of its start
        boolean limited = firstTripLimit != NO_LIMIT && isStartOfJourney(previousLabel);
        int limitTime = limited ? previousLabel.targetTime() + timeDirection * firstTripLimit : 0;

        ActiveTrip possibleTrip = null;
        // the day loop of addStopTimesInRange, kept inline on the hot path
        //noinspection DuplicatedCode
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
            if ((limited && isBetter(limitTime, tripTime)) || isBeyondTimeLimit(tripTime)) {
                continue;
            }
            if (possibleTrip == null || isBetter(tripTime, possibleTrip.entryTime())) {
                possibleTrip = new ActiveTrip(day.tripPosition(routeIdx, trip), tripTime, timeOffset, previousLabel,
                        day.stopTimes(), day.routeStopTimes()[routeIdx] + trip * tripStopTimes, dayIndex, stopOffset,
                        null);
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
     * (routing backward: its first stop), nor where it does not pick up passengers (set them down).
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
                RouteStop routeStop = routeStops[route.firstRouteStopIdx() + stopOffset];
                if (routeStop.stopIndex() == stopIdx && canBoard(routeStop)) {
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
        // the day loop of findPossibleTrip, kept inline on the hot path
        //noinspection DuplicatedCode
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
     * @param dayIndex         the index of the trip's day among the days to scan.
     * @param entryOffset      the position on the route of the stop the trip is boarded at (alighted from).
     * @param byRule           the trip transfer the trip is boarded along, or null if it is boarded at a stop.
     */
    private record ActiveTrip(int tripOffset, int entryTime, int dayTimeOffset, QueryState.Label previousLabel,
                              int[] stopTimes, int firstStopTimeIdx, int dayIndex, int entryOffset,
                              @Nullable BoardingByRule byRule) {
    }

    /**
     * A trip transfer a trip is boarded along: its group, and the trip changed from with the time it reaches the stop
     * (routing backward: the trip changed to, with the time it leaves), and whether only a rule to stay on board allows
     * it, where the rider can neither alight nor board.
     */
    private record BoardingByRule(TripTransfers.Group group, int knownPosition, int knownTime, boolean inSeat) {

        /**
         * Whether another boarding is along the same group of trip transfers from the same trip, at any time.
         */
        boolean isFromSameTrip(BoardingByRule other) {
            return group == other.group && knownPosition == other.knownPosition;
        }
    }

    /**
     * A trip to board at a stop of its route in the next round, found along a trip transfer.
     *
     * @param stopOffset the position of the stop on the route.
     * @param trip       the trip, boarded after the labels of the trip transfer.
     */
    private record PendingBoarding(int stopOffset, ActiveTrip trip) {

        /**
         * What the boarding is made of: the trip, where it is boarded, and the trip transfer with the trip changed from
         * and its time, which tell the later trips it leads to.
         */
        BoardingKey key() {
            BoardingByRule byRule = Objects.requireNonNull(trip.byRule());
            return new BoardingKey(byRule.group().index, stopOffset, trip.dayIndex(), trip.tripOffset(),
                    byRule.knownPosition(), byRule.knownTime());
        }
    }

    /**
     * A boarding along a trip transfer, see {@link PendingBoarding#key()}.
     */
    private record BoardingKey(int group, int stopOffset, int dayIndex, int tripOffset, int knownPosition,
                               int knownTime) {
    }

    /**
     * The trips a group of trip transfers allows changing to from the trips of the route it starts from (routing
     * backward: ends at) on the days to scan, one day after the other, at each call of the other route: for each trip
     * and call, the time it reaches its stop at that the trip was found for, and the trip found, null for none.
     */
    private static final class KnownTargets {

        private final int numberOfTrips;
        private final int[] times;
        private final @Nullable TransferTarget[] targets;

        KnownTargets(int numberOfTrips, int numberOfCalls) {
            this.numberOfTrips = numberOfTrips;
            times = new int[numberOfTrips * numberOfCalls];
            Arrays.fill(times, NOT_READ);
            targets = new TransferTarget[numberOfTrips * numberOfCalls];
        }
    }

    /**
     * A trip that a trip transfer allows changing to, see {@link #findTripTransferTarget}.
     *
     * @param transferTime the time the trip transfer takes.
     */
    private record TransferTarget(int tripOffset, int entryTime, int dayTimeOffset, int[] stopTimes,
                                  int firstStopTimeIdx, int dayIndex, int entryOffset, int transferTime) {
    }

}
