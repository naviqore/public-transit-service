package ch.naviqore.raptor.router;

import ch.naviqore.raptor.QueryConfig;
import ch.naviqore.raptor.TimeType;
import lombok.extern.log4j.Log4j2;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static ch.naviqore.raptor.router.Objective.INFINITY;

/**
 * The query represents a request to the raptor router and coordinates the routing logic. Each request needs a new query
 * instance.
 */
@Log4j2
class Query {

    private final int[] sourceStopIndices;
    private final int[] targetStopIndices;
    private final int[] sourceTimes;
    private final int[] walkingDurationsToTarget;

    private final RaptorData raptorData;
    private final QueryConfig config;
    private final TimeType timeType;

    private final int[] targetStops;
    private final int cutoffTime;
    private final Objective objective;

    /**
     * @param raptorData               the current raptor data structures.
     * @param sourceStopIndices        the indices of the source stops.
     * @param targetStopIndices        the indices of the target stops.
     * @param sourceTimes              the start times at the source stops.
     * @param walkingDurationsToTarget the walking durations to the target stops.
     * @param timeType                 the time type (arrival or departure) of the query.
     * @param config                   the query configuration.
     */
    Query(RaptorData raptorData, int[] sourceStopIndices, int[] targetStopIndices, int[] sourceTimes,
          int[] walkingDurationsToTarget, QueryConfig config, TimeType timeType) {

        if (sourceStopIndices.length != sourceTimes.length) {
            throw new IllegalArgumentException("Source stops and departure/arrival times must have the same size.");
        }

        if (targetStopIndices.length != walkingDurationsToTarget.length) {
            throw new IllegalArgumentException("Target stops and walking durations to target must have the same size.");
        }

        this.raptorData = raptorData;
        this.sourceStopIndices = sourceStopIndices;
        this.targetStopIndices = targetStopIndices;
        this.sourceTimes = sourceTimes;
        this.walkingDurationsToTarget = walkingDurationsToTarget;
        this.config = config;
        this.timeType = timeType;

        targetStops = new int[targetStopIndices.length * 2];
        cutoffTime = determineCutoffTime();
        // set up new query objective to be minimized (travel time and transfers)
        objective = new Objective(raptorData.getStopContext().stops().length, timeType);
    }

    /**
     * Main control flow of the routing algorithm. Spawns from source stops, coordinates route scanning, footpath
     * relaxation, and objective updates in the correct order.
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
    List<Objective.Label[]> run() {
        // set up footpath relaxer and route scanner and inject query objective
        FootpathRelaxer footpathRelaxer = new FootpathRelaxer(objective, raptorData,
                config.getMinimumTransferDuration(), config.getMaximumWalkingDuration(), timeType);
        RouteScanner routeScanner = new RouteScanner(objective, raptorData, config.getMinimumTransferDuration(),
                timeType);

        // initially relax all source stops and add the newly improved stops by relaxation to the marked stops
        Set<Integer> markedStops = initialize();
        markedStops.addAll(footpathRelaxer.relaxInitial(sourceStopIndices));
        markedStops = removeSuboptimalLabelsForRound(0, markedStops);

        // continue with further rounds as long as there are new marked stops
        int round = 1;
        while (!markedStops.isEmpty() && (round - 1) <= config.getMaximumTransferNumber()) {
            // add label layer for new round
            objective.addNewRound();

            // scan all routs and mark stops that have improved
            Set<Integer> markedStopsNext = routeScanner.scan(round, markedStops);

            // relax footpaths for all newly marked stops
            markedStopsNext.addAll(footpathRelaxer.relax(round, markedStopsNext));

            // prepare next round
            markedStops = removeSuboptimalLabelsForRound(round, markedStopsNext);
            round++;
        }

        return objective.getBestLabelsPerRound();
    }

    /**
     * Set up the initial objective for a new query.
     *
     * @return the initially marked stops.
     */
    Set<Integer> initialize() {
        log.info("Initializing objective (global best times per stop and best labels per round)");

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

            Objective.Label label = new Objective.Label(0, targetTime, Objective.LabelType.INITIAL, Objective.NO_INDEX,
                    Objective.NO_INDEX, currentStopIdx, null);
            objective.setLabel(0, currentStopIdx, label);
            objective.setBestTime(currentStopIdx, targetTime);

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
            Objective.Label label = objective.getLabel(round, stopIdx);
            if (label != null) {
                if (timeType == TimeType.DEPARTURE && label.targetTime() > bestTime) {
                    objective.setLabel(round, stopIdx, null);
                } else if (timeType == TimeType.ARRIVAL && label.targetTime() < bestTime) {
                    objective.setLabel(round, stopIdx, null);
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
            int bestTimeForStop = objective.getBestTime_REMOVE(targetStopIdx);

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
