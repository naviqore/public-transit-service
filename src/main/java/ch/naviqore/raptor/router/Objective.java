package ch.naviqore.raptor.router;

import ch.naviqore.raptor.QueryConfig;
import ch.naviqore.raptor.TimeType;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * The objective stores the progress of the raptor algorithm. Each request needs a new objective instance.
 */
@Log4j2
class Objective {

    public final static int INFINITY = Integer.MAX_VALUE;
    public final static int NO_INDEX = -1;

    private final int stopSize;
    private final int[] sourceStopIndices;
    private final int[] targetStopIndices;
    private final int[] sourceTimes;
    private final int[] walkingDurationsToTarget;

    @Getter
    private final QueryConfig config;
    @Getter
    private final TimeType timeType;

    private final int[] targetStops;
    private final int cutOffTime;

    /**
     * The global best time per stop.
     */
    private final int[] bestTimeForStops;

    /**
     * The best labels per stop and round.
     */
    @Getter
    private final List<Label[]> bestLabelsPerRound;

    /**
     * @param stopSize                 the number of stops in the stop context.
     * @param sourceStopIndices        the indices of the source stops.
     * @param targetStopIndices        the indices of the target stops.
     * @param sourceTimes              the start times at the source stops.
     * @param walkingDurationsToTarget the walking durations to the target stops.
     * @param timeType                 the type of time to check for (arrival or departure), defines if stop is
     *                                 considered as arrival or departure stop.
     * @param config                   the query configuration.
     */
    Objective(int stopSize, int[] sourceStopIndices, int[] targetStopIndices, int[] sourceTimes,
              int[] walkingDurationsToTarget, QueryConfig config, TimeType timeType) {

        if (sourceStopIndices.length != sourceTimes.length) {
            throw new IllegalArgumentException("Source stops and departure/arrival times must have the same size.");
        }

        if (targetStopIndices.length != walkingDurationsToTarget.length) {
            throw new IllegalArgumentException("Target stops and walking durations to target must have the same size.");
        }

        this.stopSize = stopSize;
        this.sourceStopIndices = sourceStopIndices;
        this.targetStopIndices = targetStopIndices;
        this.sourceTimes = sourceTimes;
        this.walkingDurationsToTarget = walkingDurationsToTarget;
        this.config = config;
        this.timeType = timeType;
        this.bestTimeForStops = new int[stopSize];
        this.bestLabelsPerRound = new ArrayList<>();

        this.targetStops = new int[targetStopIndices.length * 2];
        this.cutOffTime = determineCutOffTime();
    }

    Label getLabel(int round, int stopIdx) {
        return bestLabelsPerRound.get(round)[stopIdx];
    }

    /**
     * Sets a new label for a stop and round.
     */
    void setLabel(int round, int stopIdx, Label label) {
        bestLabelsPerRound.get(round)[stopIdx] = label;
    }

    /**
     * Get global best time of a stop.
     */
    int getBestTime(int stopIdx) {
        return bestTimeForStops[stopIdx];
    }

    /**
     * Set the global best time for a stop.
     */
    void setBestTime(int stopIdx, int time) {
        bestTimeForStops[stopIdx] = time;
    }

    /**
     * Adds a new round with empty labels.
     */
    void addNewRound() {
        bestLabelsPerRound.add(new Label[stopSize]);
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
        addNewRound();

        // set initial labels, best time and mark source stops
        Set<Integer> markedStops = new HashSet<>();
        for (int i = 0; i < sourceStopIndices.length; i++) {
            int currentStopIdx = sourceStopIndices[i];
            int targetTime = sourceTimes[i];

            Label label = new Label(0, targetTime, LabelType.INITIAL, NO_INDEX, NO_INDEX, currentStopIdx, null);
            setLabel(0, currentStopIdx, label);
            setBestTime(currentStopIdx, targetTime);

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
    Set<Integer> removeSubOptimalLabelsForRound(int round, Set<Integer> markedStops) {
        int bestTime = getBestTimeForAllTargetStops();

        if (bestTime == INFINITY || bestTime == -INFINITY) {
            return markedStops;
        }

        Label[] bestLabelsThisRound = bestLabelsPerRound.get(round);
        Set<Integer> markedStopsClean = new HashSet<>();
        for (int stopIdx : markedStops) {
            if (bestLabelsThisRound[stopIdx] != null) {
                if (timeType == TimeType.DEPARTURE && bestLabelsThisRound[stopIdx].targetTime > bestTime) {
                    bestLabelsThisRound[stopIdx] = null;
                } else if (timeType == TimeType.ARRIVAL && bestLabelsThisRound[stopIdx].targetTime < bestTime) {
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
    private int getBestTimeForAllTargetStops() {
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
        int bestTime = (timeType == TimeType.DEPARTURE) ? INFINITY : -INFINITY;

        for (Label[] labels : bestLabelsPerRound) {
            if (labels[stopIdx] == null) {
                continue;
            }

            Label currentLabel = labels[stopIdx];
            if (timeType == TimeType.DEPARTURE) {
                if (currentLabel.targetTime < bestTime) {
                    bestTime = currentLabel.targetTime;
                }
            } else {
                if (currentLabel.targetTime > bestTime) {
                    bestTime = currentLabel.targetTime;
                }
            }
        }

        return bestTime;
    }

    /**
     * Arrival type of the label.
     */
    enum LabelType {

        /**
         * First label in the connection, so there is no previous label set.
         */
        INITIAL,
        /**
         * A route label uses a public transit trip in the network.
         */
        ROUTE,
        /**
         * Uses a transfer between stops (not a same stop transfer).
         */
        TRANSFER

    }

    /**
     * A label is a part of a connection in the same mode (PT or walk).
     *
     * @param sourceTime         the source time of the label in seconds after midnight.
     * @param targetTime         the target time of the label in seconds after midnight.
     * @param type               the type of the label, can be INITIAL, ROUTE or TRANSFER.
     * @param routeOrTransferIdx the index of the route or of the transfer, see arrival type (or NO_INDEX).
     * @param tripOffset         the trip offset on the current route (or NO_INDEX).
     * @param stopIdx            the target stop of the label.
     * @param previous           the previous label, null if it is the initial label.
     */
    record Label(int sourceTime, int targetTime, LabelType type, int routeOrTransferIdx, int tripOffset, int stopIdx,
                 @Nullable Label previous) {
    }
}
