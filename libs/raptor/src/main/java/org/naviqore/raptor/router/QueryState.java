package org.naviqore.raptor.router;

import lombok.Getter;
import org.jetbrains.annotations.Nullable;
import org.naviqore.raptor.TimeType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * This object stores the current best labels and times of the raptor routing algorithm for a query instance.
 * Additionally, it also stores information about marked stops for the current and last round.
 */
final class QueryState {

    public final static int INFINITY = Integer.MAX_VALUE;
    public final static int NO_INDEX = -1;

    private final int stopSize;
    private final TimeType timeType;

    // the best labels per stop and round
    private final List<Label[]> bestLabelsPerRound = new ArrayList<>();
    // the global best time per stop
    private final int[] bestTimeForStops;

    // the marked stops for route scanning and footpath relaxing
    private boolean[] markedStopsMaskThisRound;
    private boolean[] markedStopsMaskNextRound;

    @Getter
    private int round;

    QueryState(int stopSize, TimeType timeType) {
        this.stopSize = stopSize;
        this.timeType = timeType;

        // set best times to initial value
        bestTimeForStops = new int[stopSize];
        Arrays.fill(bestTimeForStops, timeType == TimeType.DEPARTURE ? INFINITY : -INFINITY);

        markedStopsMaskThisRound = new boolean[stopSize];
        markedStopsMaskNextRound = new boolean[stopSize];

        round = -1;

        // set empty labels for first round
        addNewRound();
    }

    /**
     * Resets the round and marked stops.
     */
    void resetRounds() {
        round = 0;
        Arrays.fill(markedStopsMaskThisRound, false);
        Arrays.fill(markedStopsMaskNextRound, false);
    }

    /**
     * Adds a new round with empty labels and reset boolean marked stop masks.
     */
    void addNewRound() {
        if (round != -1) {
            // reset boolean marked stop masks, not needed when running the first time
            boolean[] tmp = markedStopsMaskThisRound;
            markedStopsMaskThisRound = markedStopsMaskNextRound;
            markedStopsMaskNextRound = tmp;
            Arrays.fill(markedStopsMaskNextRound, false);
        }

        round++;

        // only add new round if it does not exist yet (-> in range raptor same round can occur more than once)
        if (round >= bestLabelsPerRound.size()) {
            bestLabelsPerRound.add(new Label[stopSize]);
        }
    }

    /**
     * Retrieves the label for a stop at a given round.
     *
     * @param round   the round to get the label from.
     * @param stopIdx the index of the stop to retrieve the label for.
     * @return the label for the stop in the specified round, or null if not present.
     */
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
     * Get global best time of a stop for comparison. The comparison of arrival/departures requires that the same stop
     * transfer time is subtracted (departure) or added (arrival) to the actual target time, so that the comparison is
     * correct with route target times.
     */
    int getComparableBestTime(int stopIdx) {
        return bestTimeForStops[stopIdx];
    }

    /**
     * Get the actual best time of a stop. This is the actual target time. This should not be used to compare times of
     * different label types (transfer vs. route), as the same stop transfer time is not considered.
     */
    int getActualBestTime(int stopIdx) {
        int best_time = (timeType == TimeType.DEPARTURE) ? INFINITY : -INFINITY;

        // because range raptor potentially fills target times in higher rounds which are not the best solutions, every
        // round has to be looked at.
        for (Label[] labels : bestLabelsPerRound) {
            Label label = labels[stopIdx];
            if (label != null) {
                if (timeType == TimeType.DEPARTURE) {
                    best_time = Math.min(best_time, label.targetTime);
                } else {
                    best_time = Math.max(best_time, label.targetTime);
                }
            }
        }

        return best_time;
    }

    /**
     * Set the global best time for a stop.
     */
    void setBestTime(int stopIdx, int time) {
        bestTimeForStops[stopIdx] = time;
    }

    /**
     * Return the result as unmodifiable list, since it should only be modified via the setter and getter methods.
     */
    List<Label[]> getBestLabelsPerRound() {
        return Collections.unmodifiableList(bestLabelsPerRound);
    }

    /**
     * Checks if the stop was marked in the current round.
     *
     * @param stopIdx the index of the stop to check.
     * @return true if the stop was marked in this round, false otherwise.
     */
    boolean isMarkedThisRound(int stopIdx) {
        return markedStopsMaskThisRound[stopIdx];
    }

    /**
     * Checks if the stop has been marked for the next round.
     *
     * @param stopIdx the index of the stop to check.
     * @return true if the stop is marked for the next round, false otherwise.
     */
    boolean isMarkedNextRound(int stopIdx) {
        return markedStopsMaskNextRound[stopIdx];
    }

    /**
     * Marks the stop for the next round.
     *
     * @param stopIdx the index of the stop to mark.
     */
    void mark(int stopIdx) {
        markedStopsMaskNextRound[stopIdx] = true;
    }

    /**
     * Unmarks the stop for the next round.
     *
     * @param stopIdx the index of the stop to unmark.
     */
    void unmark(int stopIdx) {
        markedStopsMaskNextRound[stopIdx] = false;
    }

    /**
     * Checks if any stops have been marked for the next round.
     */
    boolean hasMarkedStops() {
        for (int i = 0; i < stopSize; i++) {
            if (markedStopsMaskNextRound[i]) {
                return true;
            }
        }
        return false;
    }

    /**
     * Creates a deep copy of the marked stops mask for the next round.
     */
    boolean[] cloneMarkedStopsMaskNextRound() {
        return markedStopsMaskNextRound.clone();
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
