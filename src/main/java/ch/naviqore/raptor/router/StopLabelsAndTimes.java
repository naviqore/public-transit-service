package ch.naviqore.raptor.router;

import ch.naviqore.raptor.TimeType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * This object stores the current best labels and times of the raptor routing algorithm for a query instance.
 */
final class StopLabelsAndTimes {

    public final static int INFINITY = Integer.MAX_VALUE;
    public final static int NO_INDEX = -1;

    // the best labels per stop and round
    private final List<Label[]> bestLabelsPerRound = new ArrayList<>();

    // the global best time per stop
    private final int[] bestTimeForStops;

    private final int stopSize;
    private final TimeType timeType;

    StopLabelsAndTimes(int stopSize, TimeType timeType) {
        this.stopSize = stopSize;
        this.timeType = timeType;

        // set best times to initial value
        bestTimeForStops = new int[stopSize];
        Arrays.fill(bestTimeForStops, timeType == TimeType.DEPARTURE ? INFINITY : -INFINITY);

        // set empty labels for first round
        addNewRound();
    }

    /**
     * Adds a new round with empty labels.
     */
    void addNewRound() {
        bestLabelsPerRound.add(new Label[stopSize]);
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
