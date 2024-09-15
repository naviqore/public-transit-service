package ch.naviqore.raptor.router;

import ch.naviqore.raptor.QueryConfig;
import ch.naviqore.raptor.TimeType;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import static ch.naviqore.raptor.router.QueryState.INFINITY;

/**
 * The query represents a request to the raptor router and coordinates the routing logic. Each request needs a new query
 * instance.
 */
@Slf4j
class Query {

    private final int[] sourceStopIndices;
    private final int[] targetStopIndices;
    private final int[] sourceTimes;
    private final int[] walkingDurationsToTarget;

    private final QueryConfig config;
    private final TimeType timeType;

    private final int[] targetStops;
    private final int cutoffTime;
    private final QueryState queryState;
    private final FootpathRelaxer footpathRelaxer;
    private final RouteScanner routeScanner;

    private final int numStops;

    private final int raptorRange;

    /**
     * @param raptorData               the current raptor data structures.
     * @param sourceStopIndices        the indices of the source stops.
     * @param targetStopIndices        the indices of the target stops.
     * @param sourceTimes              the start times at the source stops.
     * @param walkingDurationsToTarget the walking durations to the target stops.
     * @param timeType                 the time type (arrival or departure) of the query.
     * @param config                   the query configuration.
     * @param referenceDate            the reference date for the query.
     * @param raptorConfig             the raptor configuration.
     */
    Query(RaptorData raptorData, int[] sourceStopIndices, int[] targetStopIndices, int[] sourceTimes,
          int[] walkingDurationsToTarget, QueryConfig config, TimeType timeType, LocalDateTime referenceDate,
          RaptorConfig raptorConfig) {

        if (sourceStopIndices.length != sourceTimes.length) {
            throw new IllegalArgumentException("Source stops and departure/arrival times must have the same size.");
        }

        if (targetStopIndices.length != walkingDurationsToTarget.length) {
            throw new IllegalArgumentException("Target stops and walking durations to target must have the same size.");
        }

        this.sourceStopIndices = sourceStopIndices;
        this.targetStopIndices = targetStopIndices;
        this.sourceTimes = sourceTimes;
        this.walkingDurationsToTarget = walkingDurationsToTarget;
        this.config = config;
        this.timeType = timeType;
        this.raptorRange = raptorConfig.getRaptorRange();

        targetStops = new int[targetStopIndices.length * 2];
        cutoffTime = determineCutoffTime();
        numStops = raptorData.getStopContext().stops().length;
        queryState = new QueryState(raptorData.getStopContext().stops().length, timeType);

        // set up footpath relaxer and route scanner and inject stop labels and times
        footpathRelaxer = new FootpathRelaxer(queryState, raptorData, config.getMinimumTransferDuration(),
                config.getMaximumWalkingDuration(), timeType);
        routeScanner = new RouteScanner(queryState, raptorData, config, timeType, referenceDate,
                raptorConfig.getDaysToScan());
    }

    /**
     * Main control flow of the routing algorithm. Spawns from source stops, coordinates route scanning, footpath
     * relaxation, and time/label updates in the correct order.
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
     */
    List<QueryState.Label[]> run() {

        // initially relax all source stops and add the newly improved stops by relaxation to the marked stops
        boolean[] markedStopsMask = initialize();
        footpathRelaxer.relaxInitial(markedStopsMask);
        removeSuboptimalLabelsForRound(0, markedStopsMask);

        // if range is 0 or smaller there is no range, and we don't need to rerun rounds with different start offsets
        if (raptorRange <= 0) {
            doRounds(markedStopsMask);
        } else {
            doRangeRaptor(markedStopsMask);
        }
        return queryState.getBestLabelsPerRound();
    }

    void doRangeRaptor(boolean[] markedStops) {
        // prepare range offsets
        // get initial marked stops to reset after each range offset
        List<Integer> initialMarkedStops = new ArrayList<>();
        for (int stopIdx = 0; stopIdx < markedStops.length; stopIdx++) {
            if (markedStops[stopIdx]) {
                initialMarkedStops.add(stopIdx);
            }
        }
        List<Integer> rangeOffsets = getRangeOffsets(initialMarkedStops, routeScanner);
        HashMap<Integer, Integer> stopIdxSourceTimes = new HashMap<>();
        for (int stopIdx = 0; stopIdx < markedStops.length; stopIdx++) {
            if (!markedStops[stopIdx]) {
                continue;
            }
            stopIdxSourceTimes.put(stopIdx, queryState.getLabel(0, stopIdx).targetTime());
        }
        // scan all range offsets in reverse order (earliest arrival / latest departure first)
        for (int offsetIdx = rangeOffsets.size() - 1; offsetIdx >= 0; offsetIdx--) {
            int rangeOffset = rangeOffsets.get(offsetIdx);
            int timeFactor = timeType == TimeType.DEPARTURE ? 1 : -1;
            log.debug("Running rounds with range offset {}", rangeOffset);

            // set source times to the source times of the previous round
            for (int stopIdx : initialMarkedStops) {
                QueryState.Label label = queryState.getLabel(0, stopIdx);
                int targetTime = stopIdxSourceTimes.get(stopIdx) + timeFactor * rangeOffset;
                queryState.setLabel(0, stopIdx, copyLabelWithNewTargetTime(label, targetTime));
            }
            doRounds(markedStops);
        }
    }

    QueryState.Label copyLabelWithNewTargetTime(QueryState.Label label, int targetTime) {
        int sourceTime = label.sourceTime();

        // if the label is not a source label, we need to adjust the source time by the same offset
        if (label.type() != QueryState.LabelType.INITIAL) {
            int offset = targetTime - label.targetTime();
            sourceTime += offset;
        }

        return new QueryState.Label(sourceTime, targetTime, label.type(), label.routeOrTransferIdx(),
                label.tripOffset(), label.stopIdx(), label.previous());

    }

    /**
     * Method to perform the rounds of the routing algorithm (see {@link #run()}).
     *
     * @param markedStopsMask the initially marked stops mask.
     */
    private void doRounds(boolean[] markedStopsMask) {

        // continue with further rounds as long as there are new marked stops
        int round = 1;

        // check if marked stops has any true values
        while (hasMarkedStops(markedStopsMask) && (round - 1) <= config.getMaximumTransferNumber()) {
            // add label layer for new round
            queryState.addNewRound();

            // scan all routs and mark stops that have improved
            boolean[] markedStopsNext = routeScanner.scan(round, markedStopsMask);

            // relax footpaths for all newly marked stops
            footpathRelaxer.relax(round, markedStopsNext);

            // prepare next round
            removeSuboptimalLabelsForRound(round, markedStopsNext);
            markedStopsMask = markedStopsNext;
            round++;
        }
    }

    /**
     * Check if there are any marked stops in the marked stops mask.
     *
     * @param markedStopsMask the marked stops mask to check.
     * @return true if there are any marked stops, false otherwise.
     */
    private static boolean hasMarkedStops(boolean[] markedStopsMask) {
        for (boolean b : markedStopsMask) {
            if (b) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get the range offsets for the marked stops.
     * <p>
     * The range offsets define the offsets of the requested departure / arrival time when the range raptor
     * implementation shall start spawning from the source stop. E.g. if the source stop has departures at 10:00, 10:10,
     * 10:20, and the range is 30 minutes, the range offsets would be 0, 10, 20.
     * <p>
     * To be efficient, the range offsets are looked at on per route basis. So if Route A has departures at 10:00,
     * 10:10, 10:20, and Route B has departures at 10:05, 10:15, 10:25, the range offsets are be 0, 10, 20 and not 0, 5,
     * 10, 15, 20, 25 (note real values are in seconds and not minutes --> *60).
     *
     * @param initialMarkedStops the initial marked stops to get the range offsets for.
     * @param routeScanner       the route scanner to get the trip offsets for the stops.
     * @return the range offsets (in seconds) applicable for all marked stops.
     */
    private List<Integer> getRangeOffsets(List<Integer> initialMarkedStops, RouteScanner routeScanner) {
        ArrayList<Integer> rangeOffsets = new ArrayList<>();
        for (int stopIdx : initialMarkedStops) {
            List<Integer> stopRangeOffsets = routeScanner.getTripOffsetsForStop(stopIdx, raptorRange);
            for (int i = 0; i < stopRangeOffsets.size(); i++) {
                // if the rangeOffsets list is not long enough, add the offset
                if (rangeOffsets.size() == i) {
                    rangeOffsets.add(stopRangeOffsets.get(i));
                } else {
                    // if the rangeOffsets list is long enough, update the offset to the minimum of the current and the
                    // new offset, this ensures that the range offset is applicable for all marked stops
                    rangeOffsets.set(i, Math.min(rangeOffsets.get(i), stopRangeOffsets.get(i)));
                }
            }
        }

        // if no range offsets are found, add 0 as default to allow "normal" raptor to run
        if (rangeOffsets.isEmpty()) {
            rangeOffsets.add(0);
        }

        return rangeOffsets;
    }

    /**
     * Set up the best times per stop and best labels per round for a new query.
     *
     * @return the initially marked stops.
     */
    boolean[] initialize() {
        boolean[] markedStopsMask = new boolean[numStops];
        log.debug("Initializing global best times per stop and best labels per round");

        // fill target stops
        for (int i = 0; i < targetStops.length; i += 2) {
            int index = (int) Math.ceil(i / 2.0);
            targetStops[i] = targetStopIndices[index];
            targetStops[i + 1] = walkingDurationsToTarget[index];
        }

        // set initial labels, best time and mark source stops
        for (int i = 0; i < sourceStopIndices.length; i++) {
            int currentStopIdx = sourceStopIndices[i];
            int targetTime = sourceTimes[i];

            QueryState.Label label = new QueryState.Label(0, targetTime, QueryState.LabelType.INITIAL,
                    QueryState.NO_INDEX, QueryState.NO_INDEX, currentStopIdx, null);
            queryState.setLabel(0, currentStopIdx, label);
            queryState.setBestTime(currentStopIdx, targetTime);
            markedStopsMask[currentStopIdx] = true;
        }

        return markedStopsMask;
    }

    /**
     * Nullify labels that are suboptimal for the current round. This method checks if the label time is worse than the
     * optimal time mark and removes the mark for the next round and nullifies the label in this case.
     *
     * @param round           the round to remove suboptimal labels for.
     * @param markedStopsMask the marked stops mask to check for suboptimal labels.
     */
    void removeSuboptimalLabelsForRound(int round, boolean[] markedStopsMask) {
        int bestTime = getBestTimeForAllTargetStops();

        if (bestTime == INFINITY || bestTime == -INFINITY) {
            return;
        }

        for (int stopIdx = 0; stopIdx < markedStopsMask.length; stopIdx++) {
            if (!markedStopsMask[stopIdx]) {
                continue;
            }
            QueryState.Label label = queryState.getLabel(round, stopIdx);
            if (label != null) {
                if (timeType == TimeType.DEPARTURE && label.targetTime() > bestTime) {
                    queryState.setLabel(round, stopIdx, null);
                    markedStopsMask[stopIdx] = false;
                } else if (timeType == TimeType.ARRIVAL && label.targetTime() < bestTime) {
                    queryState.setLabel(round, stopIdx, null);
                    markedStopsMask[stopIdx] = false;
                }
            }
        }
    }

    /**
     * Get the best time for the target stops. The best time is the earliest arrival time for each stop if the time type
     * is departure, and the latest arrival time for each stop if the time type is arrival.
     */
    private int getBestTimeForAllTargetStops() {
        int bestTime = cutoffTime;

        for (int i = 0; i < targetStops.length; i += 2) {
            int targetStopIdx = targetStops[i];
            int walkDurationToTarget = targetStops[i + 1];
            int bestTimeForStop = queryState.getActualBestTime(targetStopIdx);

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

    /**
     * The cut-off time is the latest allowed arrival / the earliest allowed departure time, if a stop is reached
     * after/before (depending on timeType), the stop is no longer considered for further expansion.
     *
     * @return the cut-off time.
     */
    private int determineCutoffTime() {
        int cutoffTime;

        if (config.getMaximumTravelTime() == INFINITY) {
            cutoffTime = timeType == TimeType.DEPARTURE ? INFINITY : -INFINITY;
        } else if (timeType == TimeType.DEPARTURE) {
            int earliestDeparture = Arrays.stream(sourceTimes).min().orElseThrow();
            cutoffTime = earliestDeparture + config.getMaximumTravelTime();
        } else {
            int latestArrival = Arrays.stream(sourceTimes).max().orElseThrow();
            cutoffTime = latestArrival - config.getMaximumTravelTime();
        }

        return cutoffTime;
    }

}
