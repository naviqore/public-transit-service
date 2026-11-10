package org.naviqore.raptor.router;

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.naviqore.raptor.TimeType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * This object stores the current best labels and times of the RAPTOR routing algorithm for a query instance.
 * Additionally, it also stores information about marked stops for the current and last round.
 * <p>
 * A stop is reached on a trip, on foot or at the start of the query, and these arrivals serve differently: a trip can
 * be boarded after any of them, but only an arrival on a trip may be continued on foot, since a connection never walks
 * twice in a row. So a stop has two best arrivals: the best for boarding, compared by {@link #getComparableTime}, and
 * the best on a trip. An arrival on a trip that is too late for boarding can still be the one to walk on from.
 */
final class QueryState {

    public final static int INFINITY = Integer.MAX_VALUE;
    public final static int NO_INDEX = -1;

    private final Stop[] stops;
    private final TimeType timeType;

    // the best labels per stop and round, by the time a trip can be boarded after them
    private final List<Label[]> bestLabelsPerRound = new ArrayList<>();
    // the labels per stop and round of the best arrival on a trip, set in the round that improved it
    private final List<Label[]> bestTripLabelsPerRound = new ArrayList<>();
    // the global best time per stop, comparable for boarding
    private final int[] bestTimeForStops;
    // the global best arrival on a trip per stop
    private final int[] bestTripTimeForStops;
    // the stops whose best arrival on a trip the current round improved, to walk on from
    private final boolean[] tripArrivalImprovedMask;
    // the marked stops for route scanning and footpath relaxing
    private boolean[] markedStopsMaskThisRound;
    private boolean[] markedStopsMaskNextRound;
    @Getter
    private int round;

    QueryState(Stop[] stops, TimeType timeType) {
        this.stops = stops;
        this.timeType = timeType;

        // set best times to initial value
        bestTimeForStops = new int[stops.length];
        Arrays.fill(bestTimeForStops, timeType == TimeType.DEPARTURE ? INFINITY : -INFINITY);
        bestTripTimeForStops = bestTimeForStops.clone();

        markedStopsMaskThisRound = new boolean[stops.length];
        markedStopsMaskNextRound = new boolean[stops.length];
        tripArrivalImprovedMask = new boolean[stops.length];

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
        Arrays.fill(tripArrivalImprovedMask, false);
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
            Arrays.fill(tripArrivalImprovedMask, false);
        }

        round++;

        // only add new round if it does not exist yet (-> in range raptor same round can occur more than once)
        if (round >= bestLabelsPerRound.size()) {
            bestLabelsPerRound.add(new Label[stops.length]);
            bestTripLabelsPerRound.add(new Label[stops.length]);
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
    void setLabel(int round, int stopIdx, @Nullable Label label) {
        bestLabelsPerRound.get(round)[stopIdx] = label;
    }

    /**
     * Retrieves the label of the best arrival on a trip at a stop, if the round improved it.
     *
     * @param round   the round to get the label from.
     * @param stopIdx the index of the stop.
     * @return the label, or null if the round did not improve the best arrival on a trip at the stop.
     */
    @Nullable Label getTripLabel(int round, int stopIdx) {
        return bestTripLabelsPerRound.get(round)[stopIdx];
    }

    /**
     * Sets the label of a new best arrival on a trip at a stop in the current round, and its time as the best arrival
     * on a trip at the stop.
     *
     * @param stopIdx the index of the stop.
     * @param label   the label of the arrival on a trip.
     */
    void setTripLabel(int stopIdx, Label label) {
        bestTripLabelsPerRound.get(round)[stopIdx] = label;
        bestTripTimeForStops[stopIdx] = label.targetTime();
        tripArrivalImprovedMask[stopIdx] = true;
    }

    /**
     * Removes the label of the best arrival on a trip at a stop in a round, which is no longer of use.
     */
    void removeTripLabel(int round, int stopIdx) {
        bestTripLabelsPerRound.get(round)[stopIdx] = null;
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
     * Gets the time a label reaches its stop at, as compared for boarding a trip there. After an arrival on a trip, the
     * next trip can be boarded once the same stop transfer time has passed; after an arrival on foot or at the start of
     * the query, right away. So an arrival on a trip compares at its time, and any other at its time less (routing
     * backward: plus) the same stop transfer time.
     *
     * @param label the label.
     * @return the time to compare the label by.
     */
    int getComparableTime(Label label) {
        return getComparableTime(label.stopIdx(), label.targetTime(), label.type());
    }

    /**
     * Gets the time an arrival of a type at a stop is compared at for boarding a trip there, see
     * {@link #getComparableTime(Label)}.
     */
    int getComparableTime(int stopIdx, int time, LabelType type) {
        if (type == LabelType.ROUTE) {
            return time;
        }

        int sameStopTransferTime = stops[stopIdx].sameStopTransferTime();
        return timeType == TimeType.DEPARTURE ? time - sameStopTransferTime : time + sameStopTransferTime;
    }

    /**
     * Get the global best arrival on a trip at a stop.
     */
    int getBestTripTime(int stopIdx) {
        return bestTripTimeForStops[stopIdx];
    }

    /**
     * Get the actual best time of a stop: the earliest arrival (latest departure) at it of all its labels, on a trip,
     * on foot or at the start. This should not be used to compare times of different label types (transfer vs. route),
     * as the same stop transfer time is not considered.
     */
    int getActualBestTime(int stopIdx) {
        int best_time = (timeType == TimeType.DEPARTURE) ? INFINITY : -INFINITY;

        // because range raptor potentially fills target times in higher rounds which are not the best solutions, every
        // round has to be looked at.
        for (int i = 0; i < bestLabelsPerRound.size(); i++) {
            best_time = earlier(best_time, bestLabelsPerRound.get(i)[stopIdx]);
            best_time = earlier(best_time, bestTripLabelsPerRound.get(i)[stopIdx]);
        }

        return best_time;
    }

    private int earlier(int time, @Nullable Label label) {
        if (label == null) {
            return time;
        }

        return timeType == TimeType.DEPARTURE ? Math.min(time, label.targetTime()) : Math.max(time, label.targetTime());
    }

    /**
     * Set the global best time for a stop.
     */
    void setBestTime(int stopIdx, int time) {
        bestTimeForStops[stopIdx] = time;
    }

    /**
     * Return the result as unmodifiable lists, since it should only be modified via the setter and getter methods.
     */
    LabelsPerRound getLabelsPerRound() {
        return new LabelsPerRound(Collections.unmodifiableList(bestLabelsPerRound),
                Collections.unmodifiableList(bestTripLabelsPerRound));
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
     * Checks if the current round improved the best arrival on a trip at the stop.
     *
     * @param stopIdx the index of the stop to check.
     * @return true if the stop was reached on a trip earlier (later, routing backward) than before.
     */
    boolean isTripArrivalImproved(int stopIdx) {
        return tripArrivalImprovedMask[stopIdx];
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
        for (int i = 0; i < stops.length; i++) {
            if (markedStopsMaskNextRound[i]) {
                return true;
            }
        }
        return false;
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
     * The labels of a query per round and stop.
     *
     * @param best       the best labels, by the time a trip can be boarded after them.
     * @param bestOnTrip the labels of the best arrivals on a trip, in the round that improved them.
     */
    record LabelsPerRound(List<Label[]> best, List<Label[]> bestOnTrip) {
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
