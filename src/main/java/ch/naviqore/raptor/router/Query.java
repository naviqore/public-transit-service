package ch.naviqore.raptor.router;

import ch.naviqore.raptor.QueryConfig;
import ch.naviqore.raptor.TimeType;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.*;

import static ch.naviqore.raptor.router.StopLabelsAndTimes.INFINITY;

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
    private final StopLabelsAndTimes stopLabelsAndTimes;
    private final FootpathRelaxer footpathRelaxer;
    private final RouteScanner routeScanner;

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
        stopLabelsAndTimes = new StopLabelsAndTimes(raptorData.getStopContext().stops().length, timeType);

        // set up footpath relaxer and route scanner and inject stop labels and times
        footpathRelaxer = new FootpathRelaxer(stopLabelsAndTimes, raptorData, config.getMinimumTransferDuration(),
                config.getMaximumWalkingDuration(), timeType);
        routeScanner = new RouteScanner(stopLabelsAndTimes, raptorData, config.getMinimumTransferDuration(), timeType,
                referenceDate, raptorConfig.getDaysToScan());
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
    List<StopLabelsAndTimes.Label[]> run() {

        // initially relax all source stops and add the newly improved stops by relaxation to the marked stops
        Set<Integer> markedStops = initialize();
        markedStops.addAll(footpathRelaxer.relaxInitial(sourceStopIndices));
        markedStops = removeSuboptimalLabelsForRound(0, markedStops);

        // if range is 0 or smaller there is no range, and we don't need to rerun rounds with different start offsets
        if (raptorRange <= 0) {
            doRounds(markedStops);
        } else {
            doRangeRaptor(markedStops);
        }
        return stopLabelsAndTimes.getBestLabelsPerRound();
    }

    void doRangeRaptor(Set<Integer> markedStops) {
        // prepare range offsets
        List<Integer> rangeOffsets = getRangeOffsets(markedStops, routeScanner);
        HashMap<Integer, Integer> stopIdxSourceTimes = new HashMap<>();
        for (int stopIdx : markedStops) {
            stopIdxSourceTimes.put(stopIdx, stopLabelsAndTimes.getLabel(0, stopIdx).targetTime());
        }

        // scan all range offsets in reverse order (earliest arrival / latest departure first)
        for (int offsetIdx = rangeOffsets.size() - 1; offsetIdx >= 0; offsetIdx--) {
            int rangeOffset = rangeOffsets.get(offsetIdx);
            int timeFactor = timeType == TimeType.DEPARTURE ? 1 : -1;
            log.debug("Running rounds with range offset {}", rangeOffset);

            // set source times to the source times of the previous round
            for (int stopIdx : markedStops) {
                StopLabelsAndTimes.Label label = stopLabelsAndTimes.getLabel(0, stopIdx);
                int targetTime = stopIdxSourceTimes.get(stopIdx) + timeFactor * rangeOffset;
                stopLabelsAndTimes.setLabel(0, stopIdx, copyLabelWithNewTargetTime(label, targetTime));
            }
            doRounds(markedStops);
        }
    }

    StopLabelsAndTimes.Label copyLabelWithNewTargetTime(StopLabelsAndTimes.Label label, int targetTime) {
        int sourceTime = label.sourceTime();

        // if the label is not a source label, we need to adjust the source time by the same offset
        if (label.type() != StopLabelsAndTimes.LabelType.INITIAL) {
            int offset = targetTime - label.targetTime();
            sourceTime += offset;
        }

        return new StopLabelsAndTimes.Label(sourceTime, targetTime, label.type(), label.routeOrTransferIdx(),
                label.tripOffset(), label.stopIdx(), label.previous());

    }

    /**
     * Method to perform the rounds of the routing algorithm (see {@link #run()}).
     *
     * @param markedStops the initially marked stops.
     */
    private void doRounds(Set<Integer> markedStops) {

        // continue with further rounds as long as there are new marked stops
        int round = 1;
        while (!markedStops.isEmpty() && (round - 1) <= config.getMaximumTransferNumber()) {
            // add label layer for new round
            stopLabelsAndTimes.addNewRound();

            // scan all routs and mark stops that have improved
            Set<Integer> markedStopsNext = routeScanner.scan(round, markedStops);

            // relax footpaths for all newly marked stops
            markedStopsNext.addAll(footpathRelaxer.relax(round, markedStopsNext));

            // prepare next round
            markedStops = removeSuboptimalLabelsForRound(round, markedStopsNext);
            round++;
        }
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
     * @param markedStops  the marked stops to get the range offsets for.
     * @param routeScanner the route scanner to get the trip offsets for the stops.
     * @return the range offsets (in seconds) applicable for all marked stops.
     */
    private List<Integer> getRangeOffsets(Set<Integer> markedStops, RouteScanner routeScanner) {
        ArrayList<Integer> rangeOffsets = new ArrayList<>();
        for (int stopIdx : markedStops) {
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

        return rangeOffsets;
    }

    /**
     * Set up the best times per stop and best labels per round for a new query.
     *
     * @return the initially marked stops.
     */
    Set<Integer> initialize() {
        log.debug("Initializing global best times per stop and best labels per round");

        // fill target stops
        for (int i = 0; i < targetStops.length; i += 2) {
            int index = (int) Math.ceil(i / 2.0);
            targetStops[i] = targetStopIndices[index];
            targetStops[i + 1] = walkingDurationsToTarget[index];
        }

        // set initial labels, best time and mark source stops
        Set<Integer> markedStops = new HashSet<>();
        for (int i = 0; i < sourceStopIndices.length; i++) {
            int currentStopIdx = sourceStopIndices[i];
            int targetTime = sourceTimes[i];

            StopLabelsAndTimes.Label label = new StopLabelsAndTimes.Label(0, targetTime,
                    StopLabelsAndTimes.LabelType.INITIAL, StopLabelsAndTimes.NO_INDEX, StopLabelsAndTimes.NO_INDEX,
                    currentStopIdx, null);
            stopLabelsAndTimes.setLabel(0, currentStopIdx, label);
            stopLabelsAndTimes.setBestTime(currentStopIdx, targetTime);

            markedStops.add(currentStopIdx);
        }

        return markedStops;
    }

    /**
     * Nullify labels that are suboptimal for the current round. This method checks if the label time is worse than the
     * optimal time mark and removes the mark for the next round and nullifies the label in this case.
     *
     * @param round       the round to remove suboptimal labels for.
     * @param markedStops the marked stops to check for suboptimal labels.
     */
    Set<Integer> removeSuboptimalLabelsForRound(int round, Set<Integer> markedStops) {
        int bestTime = getBestTimeForAllTargetStops();

        if (bestTime == INFINITY || bestTime == -INFINITY) {
            return markedStops;
        }

        Set<Integer> markedStopsClean = new HashSet<>();
        for (int stopIdx : markedStops) {
            StopLabelsAndTimes.Label label = stopLabelsAndTimes.getLabel(round, stopIdx);
            if (label != null) {
                if (timeType == TimeType.DEPARTURE && label.targetTime() > bestTime) {
                    stopLabelsAndTimes.setLabel(round, stopIdx, null);
                } else if (timeType == TimeType.ARRIVAL && label.targetTime() < bestTime) {
                    stopLabelsAndTimes.setLabel(round, stopIdx, null);
                } else {
                    markedStopsClean.add(stopIdx);
                }
            }
        }

        return markedStopsClean;
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
            int bestTimeForStop = stopLabelsAndTimes.getActualBestTime(targetStopIdx);

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
