package ch.naviqore.raptor;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import java.util.*;

import static ch.naviqore.raptor.Raptor.INFINITY;
import static ch.naviqore.raptor.Raptor.NO_INDEX;

/**
 * The objective stores the progress of the raptor algorithm. Each request needs a new objective instance.
 */
@Log4j2
class Objective {

    private final Stop[] stops;

    private final int[] sourceStopIndices;
    private final int[] targetStopIndices;
    private final int[] sourceTimes;
    private final int[] walkingDurationsToTarget;

    private final QueryConfig config;
    private final TimeType timeType;

    private final int[] targetStops;
    private final int cutOffTime;

    /**
     * The global best time per stop.
     */
    @Getter
    private final int[] bestTimeForStops;

    /**
     * The best labels per stop and round.
     */
    @Getter
    private final List<Raptor.Label[]> bestLabelsPerRound;

    Objective(StopContext stopContext, int[] sourceStopIndices, int[] targetStopIndices, int[] sourceTimes,
              int[] walkingDurationsToTarget, QueryConfig config, TimeType timeType) {

        if (sourceStopIndices.length != sourceTimes.length) {
            throw new IllegalArgumentException("Source stops and departure/arrival times must have the same size.");
        }

        if (targetStopIndices.length != walkingDurationsToTarget.length) {
            throw new IllegalArgumentException("Target stops and walking durations to target must have the same size.");
        }

        this.stops = stopContext.stops();
        this.sourceStopIndices = sourceStopIndices;
        this.targetStopIndices = targetStopIndices;
        this.sourceTimes = sourceTimes;
        this.walkingDurationsToTarget = walkingDurationsToTarget;
        this.config = config;
        this.timeType = timeType;
        this.bestTimeForStops = new int[stops.length];
        this.bestLabelsPerRound = new ArrayList<>();

        this.targetStops = new int[targetStopIndices.length * 2];
        this.cutOffTime = determineCutOffTime();
    }

    /**
     * Set up the initial objective for a new query.
     *
     * @return the initially marked stops.
     */
    Set<Integer> initialize() {
        log.info("Initializing objective (global best times per stop and best labels per round)");

        // set best times to zero
        Arrays.fill(bestTimeForStops, timeType == TimeType.DEPARTURE ? INFINITY : -INFINITY);

        // fill target stops
        for (int i = 0; i < targetStops.length; i += 2) {
            int index = (int) Math.ceil(i / 2.0);
            targetStops[i] = targetStopIndices[index];
            targetStops[i + 1] = walkingDurationsToTarget[index];
        }

        // set empty labels for first round
        this.bestLabelsPerRound.add(new Raptor.Label[stops.length]);

        // set initial labels and mark source stops
        Set<Integer> markedStops = new HashSet<>();
        for (int i = 0; i < sourceStopIndices.length; i++) {
            bestTimeForStops[sourceStopIndices[i]] = sourceTimes[i];
            bestLabelsPerRound.getFirst()[sourceStopIndices[i]] = new Raptor.Label(0, sourceTimes[i],
                    Raptor.LabelType.INITIAL, NO_INDEX, NO_INDEX, sourceStopIndices[i], null);
            markedStops.add(sourceStopIndices[i]);
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
    Set<Integer> removeSubOptimalLabelsForRound(int round, Set<Integer> markedStops) {
        int bestTime = getBestTime();

        if (bestTime == INFINITY || bestTime == -INFINITY) {
            return markedStops;
        }

        Raptor.Label[] bestLabelsThisRound = bestLabelsPerRound.get(round);
        Set<Integer> markedStopsClean = new HashSet<>();
        for (int stopIdx : markedStops) {
            if (bestLabelsThisRound[stopIdx] != null) {
                if (timeType == TimeType.DEPARTURE && bestLabelsThisRound[stopIdx].targetTime() > bestTime) {
                    bestLabelsThisRound[stopIdx] = null;
                } else if (timeType == TimeType.ARRIVAL && bestLabelsThisRound[stopIdx].targetTime() < bestTime) {
                    bestLabelsThisRound[stopIdx] = null;
                } else {
                    markedStopsClean.add(stopIdx);
                }
            }
        }

        return markedStopsClean;
    }

    /**
     * The cut-off time is the latest allowed arrival / the earliest allowed departure time, if a stop is reached
     * after/before (depending on timeType), the stop is no longer considered for further expansion.
     *
     * @return the cut-off time.
     */
    private int determineCutOffTime() {
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

        return cutOffTime;
    }

    /**
     * Get the best time for the target stops. The best time is the earliest arrival time for each stop if the time type
     * is departure, and the latest arrival time for each stop if the time type is arrival.
     */
    private int getBestTime() {
        int bestTime = cutOffTime;

        for (int i = 0; i < targetStops.length; i += 2) {
            int targetStopIdx = targetStops[i];
            int walkDurationToTarget = targetStops[i + 1];
            int bestTimeForStop = getBestTimeForStop(targetStopIdx);

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

    private int getBestTimeForStop(int stopIdx) {
        int timeFactor = timeType == TimeType.DEPARTURE ? 1 : -1;
        int bestTime = timeFactor * INFINITY;

        for (Raptor.Label[] labels : bestLabelsPerRound) {
            if (labels[stopIdx] == null) {
                continue;
            }

            Raptor.Label currentLabel = labels[stopIdx];
            if (timeType == TimeType.DEPARTURE) {
                if (currentLabel.targetTime() < bestTime) {
                    bestTime = currentLabel.targetTime();
                }
            } else {
                if (currentLabel.targetTime() > bestTime) {
                    bestTime = currentLabel.targetTime();
                }
            }
        }

        return bestTime;
    }

}
