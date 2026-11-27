package org.naviqore.raptor.router;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.naviqore.raptor.QueryConfig;
import org.naviqore.raptor.TimeType;

import java.time.OffsetDateTime;
import java.util.*;

import static org.naviqore.raptor.router.QueryState.INFINITY;

/**
 * The query represents a request to the RAPTOR router and coordinates the routing logic. Each request needs a new query
 * instance.
 * <p>
 * A query routes from the source stops at their times in one pass of RAPTOR. A range query (rRAPTOR in the RAPTOR
 * paper) routes over a time window that starts at the source times (ends at them, routing backward): one pass per time
 * in the window at which leaving the source stops just catches a trip, the latest first, and the labels and best times
 * of one pass kept for the next. A label of a later departure bounds every earlier one, since whoever leaves earlier
 * can wait, so a pass improves only what it reaches earlier than the passes before, and continues from that alone. The
 * window shifts all source times alike, so a connection leaves in it if it leaves the source stops in it.
 * <p>
 * The connections of a range query are those leaving in the window that no other connection beats, also one leaving
 * after the window: a first pass leaves at the end of the window and finds the best connections leaving after it, which
 * bound the passes in the window without being reported, and every pass takes the first trips it can catch, also after
 * the window, but keeps only the connections leaving in it. So a connection that one leaving after the window beats is
 * not reported, and the windows that follow one another report the connections of a longer one. The isolines of a range
 * query are the shortest travel times among the connections leaving in the window: the first trip of a pass must leave
 * in the rest of the window, which the route scanner limits, since a connection leaving after the window would take the
 * place of one leaving in it.
 */
@Slf4j
class Query {

    private static final int NO_LIMIT = -1;

    private final int[] sourceStopIndices;
    private final int[] targetStopIndices;
    private final int[] sourceTimes;
    private final int[] walkDurationsToTarget;

    private final QueryConfig config;
    private final TimeType timeType;
    private final int timeDirection;
    private final int timeWindow;

    private final Stop[] stops;
    // the walks of the direction of the search, see StopContext
    private final Transfer[] transfers;
    private final QueryState queryState;
    private final FootpathRelaxer footpathRelaxer;
    private final RouteScanner routeScanner;

    private final int numStops;

    // the labels at the target stops of the connections found
    private final List<QueryState.Label> targetLabels = new ArrayList<>();

    // of the current pass
    private int cutoffTime;
    private int offset;

    /**
     * @param raptorData            the current raptor data structures.
     * @param sourceStopIndices     the indices of the source stops.
     * @param targetStopIndices     the indices of the target stops.
     * @param sourceTimes           the start times at the source stops.
     * @param walkDurationsToTarget the walking durations to the target stops.
     * @param config                the query configuration.
     * @param timeType              the time type (arrival or departure) of the query.
     * @param timeWindow            the time window of a range query in seconds, 0 for a query at the source times.
     * @param referenceDateTime     the reference date time for the query.
     * @param raptorConfig          the raptor configuration.
     */
    Query(RaptorData raptorData, int[] sourceStopIndices, int[] targetStopIndices, int[] sourceTimes,
          int[] walkDurationsToTarget, QueryConfig config, TimeType timeType, int timeWindow,
          OffsetDateTime referenceDateTime, RaptorConfig raptorConfig) {

        if (sourceStopIndices.length != sourceTimes.length) {
            throw new IllegalArgumentException("Source stops and departure/arrival times must have the same size.");
        }

        if (targetStopIndices.length != walkDurationsToTarget.length) {
            throw new IllegalArgumentException("Target stops and walk durations to target must have the same size.");
        }

        if (timeWindow < 0) {
            throw new IllegalArgumentException("Time window must not be negative.");
        }

        this.sourceStopIndices = sourceStopIndices;
        this.targetStopIndices = targetStopIndices;
        this.sourceTimes = sourceTimes;
        this.walkDurationsToTarget = walkDurationsToTarget;
        this.config = config;
        this.timeType = timeType;
        this.timeWindow = timeWindow;
        timeDirection = timeType == TimeType.DEPARTURE ? 1 : -1;

        stops = raptorData.getStopContext().stops();
        transfers = raptorData.getStopContext().transfers(timeType);
        numStops = stops.length;
        queryState = new QueryState(stops, timeType, isRangeQuery());

        // set up footpath relaxer and route scanner and inject stop labels and times
        footpathRelaxer = new FootpathRelaxer(queryState, raptorData, config.getMinimumTransferDuration(),
                config.getMaximumWalkDuration(), timeType, config.isAllowSourceTransfer(),
                config.isAllowTargetTransfer(), targetStopIndices);
        routeScanner = new RouteScanner(queryState, raptorData, config, timeType, referenceDateTime,
                raptorConfig.getDaysToScan());
    }

    private static boolean isOnATrip(QueryState.Label label) {
        return label.type() == QueryState.LabelType.ROUTE;
    }

    private boolean isRangeQuery() {
        return timeWindow > 0;
    }

    /**
     * Routes the connections to the target stops: those that no other one beats in its time at the target and its
     * number of trips, and for a range query also in its time at the source stops. They are found where a round
     * improves the best time at the target, see {@link #updateBestTimeAtTarget}.
     *
     * @return the labels at the target stops of the connections, in the order found: a query's by number of trips, a
     * range query's by pass, the latest departure (earliest arrival, routing backward) first.
     */
    List<QueryState.Label> routeConnections() {
        run(false, round -> {
        });

        return targetLabels;
    }

    /**
     * Routes isolines: the earliest arrival (latest departure) at each stop, or for a range query, the shortest travel
     * time to each stop among the connections leaving the source stops in the window (reaching them, routing
     * backward).
     *
     * @return the label to reconstruct the connection to each stop from, null for a stop not reached.
     */
    QueryState.@Nullable Label[] routeIsolines() {
        if (!isRangeQuery()) {
            run(false, round -> {
            });
            return queryState.getEarliestArrivalLabels();
        }

        ShortestTravelTimes shortestTravelTimes = new ShortestTravelTimes(queryState, numStops, timeType);
        run(true, new PassListener() {
            @Override
            public void afterRound(int round) {
                shortestTravelTimes.collect(round);
            }

            @Override
            public void afterPass(int passOffset) {
                shortestTravelTimes.endPass(passOffset);
            }
        });

        return shortestTravelTimes.labels();
    }

    /**
     * Main control flow of the routing algorithm: one pass from the source stops at their times, or for a range query,
     * one per offset into the time window, see {@link #getPassOffsets(boolean)}.
     *
     * @param limitFirstTrip whether the first trip of a range query must leave in the window.
     * @param listener       what to do with the labels of each round and pass.
     */
    private void run(boolean limitFirstTrip, PassListener listener) {
        if (!isRangeQuery()) {
            runPass(0, NO_LIMIT, listener);
            return;
        }

        // without a first trip limit, the connections leaving after the window bound those leaving in it: a first pass
        // at its end finds the best of them
        for (int passOffset : getPassOffsets(!limitFirstTrip)) {
            // a connection leaving in the window takes its first trip in the rest of it
            runPass(passOffset, limitFirstTrip ? timeWindow - passOffset - 1 : NO_LIMIT, listener);
        }
    }

    /**
     * Runs a pass of the routing algorithm. Spawns from source stops, coordinates route scanning, footpath relaxation,
     * and time/label updates in the correct order.
     * <p>
     * The process starts by relaxing all source stops and adding the newly improved stops by relaxation to the set of
     * marked stops. It then iterates through rounds of route scanning and footpath relaxation until no new stops are
     * marked or the maximum number of transfers is reached.
     * <p>
     * Each round includes the following steps:
     * <ul>
     *     <li>Add a new label layer for the current round.</li>
     *     <li>Scan all routes and mark stops that have improved.</li>
     *     <li>Relax footpaths for all newly marked stops.</li>
     *     <li>Prepare for the next round by removing suboptimal labels.</li>
     * </ul>
     *
     * @param passOffset     the offset of the pass into the time window, in seconds, 0 for a query without one.
     * @param firstTripLimit how long after the start of a journey its first trip may leave at the latest, in seconds,
     *                       or {@link #NO_LIMIT}.
     * @param listener       what to do with the labels of each round and of the pass.
     */
    private void runPass(int passOffset, int firstTripLimit, PassListener listener) {
        log.debug("Running a pass with offset {}", passOffset);
        offset = passOffset;
        cutoffTime = determineCutoffTime(passOffset);
        queryState.startPass();
        initializeSourceStops(passOffset);
        routeScanner.setFirstTripLimit(firstTripLimit);

        // initially relax all source stops and add the newly improved stops by relaxation to the marked stops
        footpathRelaxer.relaxInitial();
        endRound(0, listener);

        while (hasNextRound()) {
            // add label layer for new round
            queryState.addNewRound();

            // scan all routs and mark stops that have improved
            routeScanner.scan(queryState.getRound());

            // relax footpaths for all newly marked stops
            footpathRelaxer.relax(queryState.getRound());

            // prepare next round
            endRound(queryState.getRound(), listener);
        }

        listener.afterPass(passOffset);
    }

    /**
     * Ends a round: records where it reached the target best, and removes its suboptimal labels.
     *
     * @param round    the round, once its labels are set.
     * @param listener what to do with the labels of the round.
     */
    private void endRound(int round, PassListener listener) {
        QueryState.Label targetLabel = updateBestTimeAtTarget(round);
        removeSuboptimalLabelsForRound(round);
        if (targetLabel != null && isConnection(targetLabel, round)) {
            targetLabels.add(targetLabel);
        }
        listener.afterRound(round);
    }

    /**
     * Whether the label at the target of a round is a connection: not if it is beyond the cut-off time, nor if it
     * leaves after the window of a range query. A connection on foot alone, of round 0, can leave at any time: a range
     * query records it once, leaving at the start of the window (arriving at its end, routing backward).
     */
    private boolean isConnection(QueryState.Label targetLabel, int round) {
        boolean onFootAlone = round == 0;
        boolean inTime = !isWorse(targetLabel.targetTime(), cutoffTime);
        return inTime && (!onFootAlone || offset == 0) && leavesInWindow(targetLabel);
    }

    /**
     * Whether the connection of a label leaves in the time window, if the query has one: whether its first trip (its
     * last trip, routing backward) leaves in the rest of the window after the start of the current pass, from a source
     * stop or after walking from one, as the route scanner limits it for isolines. A connection on foot alone leaves at
     * the start of the pass.
     *
     * @param targetLabel the label at the end of the connection.
     */
    private boolean leavesInWindow(QueryState.Label targetLabel) {
        if (!isRangeQuery()) {
            return true;
        }

        QueryState.Label firstTrip = null;
        for (QueryState.Label label = targetLabel; label.previous() != null; label = label.previous()) {
            if (label.type() == QueryState.LabelType.ROUTE) {
                firstTrip = label;
            }
        }
        if (firstTrip == null) {
            return true;
        }

        QueryState.Label start = firstTrip.previous();
        assert start != null;
        int latestTime = start.targetTime() + timeDirection * (timeWindow - 1 - offset);
        return timeDirection * firstTrip.sourceTime() <= timeDirection * latestTime;
    }

    private boolean hasNextRound() {
        return queryState.hasMarkedStops() && queryState.getRound() <= config.getMaximumTransfers();
    }

    /**
     * Gets the offsets into the time window at which passes start: the start of the window, and every time in it at
     * which leaving the source stops just catches a trip, at a source stop or after walking from one.
     *
     * @param withWindowEnd whether to start a pass at the end of the window too, the first of all.
     * @return the offsets in seconds, the latest first.
     */
    private int[] getPassOffsets(boolean withWindowEnd) {
        Set<Integer> offsets = new HashSet<>();
        offsets.add(0);
        if (withWindowEnd) {
            offsets.add(timeWindow);
        }
        for (int i = 0; i < sourceStopIndices.length; i++) {
            int stopIdx = sourceStopIndices[i];
            addPassOffsets(offsets, stopIdx, sourceTimes[i]);
            if (!config.isAllowSourceTransfer()) {
                continue;
            }

            // a transfer label allows the next trip after its target time, see RouteScanner
            Stop stop = stops[stopIdx];
            int transferEndIdx = stop.transferIdx(timeType) + stop.numberOfTransfers(timeType);
            for (int transferIdx = stop.transferIdx(timeType); transferIdx < transferEndIdx; transferIdx++) {
                Transfer transfer = transfers[transferIdx];
                if (transfer.duration() <= config.getMaximumWalkDuration()) {
                    int walk = transfer.duration() + config.getMinimumTransferDuration();
                    addPassOffsets(offsets, transfer.targetStopIdx(), sourceTimes[i] + timeDirection * walk);
                }
            }
        }

        return offsets.stream().sorted(Comparator.reverseOrder()).mapToInt(Integer::intValue).toArray();
    }

    /**
     * Adds the offsets into the time window at which the trips at a stop can just be caught.
     *
     * @param offsets the offsets to add to.
     * @param stopIdx the index of the stop.
     * @param time    the time the stop is reached at when leaving the source stop at the start of the window.
     */
    private void addPassOffsets(Set<Integer> offsets, int stopIdx, int time) {
        int minValue = timeType == TimeType.DEPARTURE ? time : time - timeWindow + 1;
        int maxValue = timeType == TimeType.DEPARTURE ? time + timeWindow - 1 : time;
        for (int tripTime : routeScanner.getTripTimesAtStop(stopIdx, minValue, maxValue)) {
            offsets.add(timeDirection * (tripTime - time));
        }
    }

    /**
     * Sets the initial labels of the source stops at their times shifted by an offset into a time window, and marks
     * them. A source stop that a pass before reached earlier for boarding, on foot from another source stop, keeps that
     * label.
     *
     * @param passOffset the offset into the window, 0 for a query without one.
     */
    private void initializeSourceStops(int passOffset) {
        for (int i = 0; i < sourceStopIndices.length; i++) {
            int stopIdx = sourceStopIndices[i];
            int targetTime = sourceTimes[i] + timeDirection * passOffset;

            QueryState.Label label = new QueryState.Label(0, targetTime, QueryState.LabelType.INITIAL,
                    QueryState.NO_INDEX, QueryState.NO_INDEX, stopIdx, null);
            int comparableTime = queryState.getComparableTime(label);
            if (timeDirection * comparableTime < timeDirection * queryState.getComparableBestTime(stopIdx)) {
                queryState.setLabel(0, stopIdx, label);
                queryState.setBestTime(stopIdx, comparableTime);
                queryState.mark(stopIdx);
            }
        }
    }

    /**
     * Updates the best time at the target with the labels the current round set at the target stops: the best time of
     * all connections found with as many trips or fewer, over all passes so far.
     * <p>
     * Only the labels this round of this pass set count, since the labels of passes before, which a range query keeps,
     * belong to their connections. Of equally good labels, an arrival on a trip is taken, which needs no walk at the
     * end: a walk to another target stop, which the feed may join to it by a transfer of no time, is a leg too many.
     * Else the label at the first target stop is taken.
     *
     * @param round the current round.
     * @return the label of the improvement at its target stop, or null if the round did not improve the target.
     */
    private QueryState.@Nullable Label updateBestTimeAtTarget(int round) {
        int bestTime = queryState.getBestTimeAtTarget();
        QueryState.Label bestLabel = null;
        for (int i = 0; i < targetStopIndices.length; i++) {
            int stopIdx = targetStopIndices[i];
            int walkDuration = walkDurationsToTarget[i];
            QueryState.Label tripLabel = queryState.isTripArrivalImproved(stopIdx) ? queryState.getTripLabel(round,
                    stopIdx) : null;
            QueryState.Label label = queryState.isMarkedNextRound(stopIdx) ? queryState.getLabel(round, stopIdx) : null;
            for (QueryState.Label candidate : new QueryState.Label[]{tripLabel, label}) {
                if (candidate == null) {
                    continue;
                }
                int timeAtTarget = candidate.targetTime() + timeDirection * walkDuration;
                boolean better = timeDirection * timeAtTarget < timeDirection * bestTime;
                boolean asGoodOnATrip = bestLabel != null && timeAtTarget == bestTime && isOnATrip(
                        candidate) && !isOnATrip(bestLabel);
                if (better || asGoodOnATrip) {
                    bestTime = timeAtTarget;
                    bestLabel = candidate;
                }
            }
        }

        if (bestLabel != null) {
            queryState.setBestTimeAtTarget(bestTime);
        }

        return bestLabel;
    }

    /**
     * Nullify labels that are suboptimal for the current round. This method checks if the label time is worse than the
     * optimal time mark and removes the mark for the next round and nullifies the label in this case. The optimal time
     * is the best time at the target, or the cut-off time if that is better.
     *
     * @param round the round to remove suboptimal labels for.
     */
    private void removeSuboptimalLabelsForRound(int round) {
        int bestTime = cutoffTime;
        if (timeDirection * queryState.getBestTimeAtTarget() < timeDirection * bestTime) {
            bestTime = queryState.getBestTimeAtTarget();
        }

        if (bestTime == INFINITY || bestTime == -INFINITY) {
            return;
        }

        // an arrival on a trip of the round, which the walks of the round have been relaxed from
        for (int stopIdx = queryState.nextStopWithTripArrivalImproved(
                0); stopIdx >= 0; stopIdx = queryState.nextStopWithTripArrivalImproved(stopIdx + 1)) {
            QueryState.Label tripLabel = queryState.getTripLabel(round, stopIdx);
            if (tripLabel != null && isWorse(tripLabel.targetTime(), bestTime)) {
                queryState.removeTripLabel(round, stopIdx);
            }
        }

        for (int stopIdx = queryState.nextStopMarkedNextRound(
                0); stopIdx >= 0; stopIdx = queryState.nextStopMarkedNextRound(stopIdx + 1)) {
            QueryState.Label label = queryState.getLabel(round, stopIdx);
            if (label != null) {
                if (isWorse(label.targetTime(), bestTime)) {
                    queryState.setLabel(round, stopIdx, null);
                    queryState.unmark(stopIdx);
                }
            } else {
                // if label is null there is no reason to mark the stop!
                queryState.unmark(stopIdx);
            }
        }
    }

    /**
     * Whether a time is worse than another for the time type: later when departing, earlier when arriving.
     */
    private boolean isWorse(int time, int otherTime) {
        return timeType == TimeType.DEPARTURE ? time > otherTime : time < otherTime;
    }

    /**
     * The cut-off time is the latest allowed arrival / the earliest allowed departure time, if a stop is reached
     * after/before (depending on timeType), the stop is no longer considered for further expansion.
     *
     * @param passOffset the offset of the source times into a time window, 0 for a query without one.
     * @return the cut-off time.
     */
    private int determineCutoffTime(int passOffset) {
        int cutoffTime;

        if (config.getMaximumTravelDuration() == INFINITY) {
            cutoffTime = timeType == TimeType.DEPARTURE ? INFINITY : -INFINITY;
        } else if (timeType == TimeType.DEPARTURE) {
            int earliestDeparture = Arrays.stream(sourceTimes).min().orElseThrow() + passOffset;
            cutoffTime = earliestDeparture + config.getMaximumTravelDuration();
        } else {
            int latestArrival = Arrays.stream(sourceTimes).max().orElseThrow() - passOffset;
            cutoffTime = latestArrival - config.getMaximumTravelDuration();
        }

        return cutoffTime;
    }

    /**
     * What a query does with the labels of its passes.
     */
    private interface PassListener {

        /**
         * After a round of a pass, once its labels are final.
         *
         * @param round the round.
         */
        void afterRound(int round);

        /**
         * After a pass.
         *
         * @param passOffset the offset of the pass into the time window, in seconds.
         */
        default void afterPass(int passOffset) {
        }
    }

}
