package org.naviqore.raptor.router;

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.naviqore.raptor.TimeType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

/**
 * This object stores the current best labels and times of the RAPTOR routing algorithm for a query instance.
 * Additionally, it also stores information about marked stops for the current and last round.
 * <p>
 * A stop is reached on a trip, on foot or at the start of the query, and these arrivals serve differently: a trip can
 * be boarded after any of them, but only an arrival on a trip may be continued on foot, since a connection never walks
 * twice in a row. So a stop has two best arrivals: the best for boarding, compared by {@link #getComparableTime}, and
 * the best on a trip. An arrival on a trip that is too late for boarding can still be the one to walk on from.
 * <p>
 * A range query runs several passes and keeps the labels and times of one pass for the next (see {@link Query}). Its
 * best times are kept per round, as in the RAPTOR paper: the best time of a round is the best of that round and the
 * rounds before it, over all passes so far, and each round starts from the best times of the round before it in the
 * same pass. A label is then only better if no connection with as many trips or fewer was as good, which a single best
 * time over all rounds would not tell: a later pass may have needed more trips for it. A query of one pass needs no
 * best times per round: its rounds only improve on the rounds before them, so the best times of its current round are
 * those of all its rounds, and all rounds share one array.
 * <p>
 * The marked stops are bit sets, which a round reads in the order of the stops by their set bits: a round marks few of
 * all stops, and a range query runs many rounds.
 */
final class QueryState {

    public final static int INFINITY = Integer.MAX_VALUE;
    public final static int NO_INDEX = -1;

    private final Stop[] stops;
    private final TimeType timeType;
    private final int unreached;
    private final boolean bestTimesPerRound;

    // the labels per round and stop of the best arrival for boarding and of the best arrival on a trip, set in the
    // round that improved them
    private final List<Label[]> bestLabelsPerRound = new ArrayList<>();
    private final List<Label[]> bestTripLabelsPerRound = new ArrayList<>();
    // the best times per round and stop, of the round and the rounds before it: comparable for boarding, and of
    // arrivals on a trip
    private final List<int[]> bestTimes = new ArrayList<>();
    private final List<int[]> bestTripTimes = new ArrayList<>();
    // the stops whose best arrival on a trip the current round improved, to walk on from
    private final BitSet tripArrivalImprovedMask;
    // the best time at the target per round, of the round and the rounds before it
    private int[] bestTimesAtTarget = new int[0];
    // the marked stops for route scanning and footpath relaxing
    private BitSet markedStopsMaskThisRound;
    private BitSet markedStopsMaskNextRound;
    @Getter
    private int round;

    /**
     * @param stops             the stops of the router.
     * @param timeType          the time type (arrival or departure).
     * @param bestTimesPerRound whether to keep the best times per round, for a query of several passes.
     */
    QueryState(Stop[] stops, TimeType timeType, boolean bestTimesPerRound) {
        this.stops = stops;
        this.timeType = timeType;
        this.unreached = timeType == TimeType.DEPARTURE ? INFINITY : -INFINITY;
        this.bestTimesPerRound = bestTimesPerRound;

        markedStopsMaskThisRound = new BitSet(stops.length);
        markedStopsMaskNextRound = new BitSet(stops.length);
        tripArrivalImprovedMask = new BitSet(stops.length);

        startPass();
    }

    /**
     * Starts a pass at round 0, without any marked stop, keeping the labels and best times of the passes before.
     */
    void startPass() {
        round = 0;
        markedStopsMaskThisRound.clear();
        markedStopsMaskNextRound.clear();
        tripArrivalImprovedMask.clear();
        ensureRoundExists();
    }

    /**
     * Adds a new round with empty labels and reset marked stop masks. With best times per round, the new round's best
     * times become the better of its own, of the passes before, and those of the round before it.
     */
    void addNewRound() {
        // reset marked stop masks
        BitSet tmp = markedStopsMaskThisRound;
        markedStopsMaskThisRound = markedStopsMaskNextRound;
        markedStopsMaskNextRound = tmp;
        markedStopsMaskNextRound.clear();
        tripArrivalImprovedMask.clear();

        round++;
        ensureRoundExists();

        if (bestTimesPerRound) {
            takeBetter(bestTimes.get(round), bestTimes.get(round - 1));
            takeBetter(bestTripTimes.get(round), bestTripTimes.get(round - 1));
        }
        bestTimesAtTarget[round] = better(bestTimesAtTarget[round], bestTimesAtTarget[round - 1]);
    }

    private void ensureRoundExists() {
        if (round < bestLabelsPerRound.size()) {
            return;
        }

        bestLabelsPerRound.add(new Label[stops.length]);
        bestTripLabelsPerRound.add(new Label[stops.length]);
        if (bestTimesPerRound || round == 0) {
            bestTimes.add(unreachedTimes());
            bestTripTimes.add(unreachedTimes());
        } else {
            // a query of one pass: the rounds share the best times, see the class comment
            bestTimes.add(bestTimes.getFirst());
            bestTripTimes.add(bestTripTimes.getFirst());
        }
        bestTimesAtTarget = Arrays.copyOf(bestTimesAtTarget, round + 1);
        bestTimesAtTarget[round] = unreached;
    }

    private int[] unreachedTimes() {
        int[] times = new int[stops.length];
        Arrays.fill(times, unreached);
        return times;
    }

    private void takeBetter(int[] times, int[] otherTimes) {
        for (int stopIdx = 0; stopIdx < times.length; stopIdx++) {
            times[stopIdx] = better(times[stopIdx], otherTimes[stopIdx]);
        }
    }

    private int better(int time, int otherTime) {
        return timeType == TimeType.DEPARTURE ? Math.min(time, otherTime) : Math.max(time, otherTime);
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
        bestTripTimes.get(round)[stopIdx] = label.targetTime();
        tripArrivalImprovedMask.set(stopIdx);
    }

    /**
     * Removes the label of the best arrival on a trip at a stop in a round, which is no longer of use.
     */
    void removeTripLabel(int round, int stopIdx) {
        bestTripLabelsPerRound.get(round)[stopIdx] = null;
    }

    /**
     * Get the best time of a stop for comparison in the current round. The comparison of arrival/departures requires
     * that the same stop transfer time is subtracted (departure) or added (arrival) to the actual target time, so that
     * the comparison is correct with route target times, see {@link #getComparableTime(Label)}.
     */
    int getComparableBestTime(int stopIdx) {
        return bestTimes.get(round)[stopIdx];
    }

    /**
     * Set the best time for a stop in the current round, comparable for boarding.
     */
    void setBestTime(int stopIdx, int time) {
        bestTimes.get(round)[stopIdx] = time;
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
     * Get the best arrival on a trip at a stop in the current round.
     */
    int getBestTripTime(int stopIdx) {
        return bestTripTimes.get(round)[stopIdx];
    }

    /**
     * Gets the best time at the target in the current round: the earliest arrival (latest departure) of the connections
     * found with as many trips or fewer.
     */
    int getBestTimeAtTarget() {
        return bestTimesAtTarget[round];
    }

    /**
     * Sets the best time at the target in the current round.
     */
    void setBestTimeAtTarget(int time) {
        bestTimesAtTarget[round] = time;
    }

    /**
     * Gets the label of the earliest arrival (latest departure) at each stop, on a trip, on foot or at the start, of
     * all rounds. Of equally early arrivals, the one of the lowest round is kept, which takes the fewest trips, and in
     * a round the one on a trip, which needs no walk at the end.
     *
     * @return the label per stop, null for a stop not reached.
     */
    @Nullable Label[] getEarliestArrivalLabels() {
        Label[] earliestArrivals = new Label[stops.length];
        for (int i = 0; i < bestLabelsPerRound.size(); i++) {
            Label[] tripLabels = bestTripLabelsPerRound.get(i);
            Label[] labels = bestLabelsPerRound.get(i);
            for (int stopIdx = 0; stopIdx < stops.length; stopIdx++) {
                earliestArrivals[stopIdx] = earlier(earliestArrivals[stopIdx], tripLabels[stopIdx]);
                earliestArrivals[stopIdx] = earlier(earliestArrivals[stopIdx], labels[stopIdx]);
            }
        }

        return earliestArrivals;
    }

    /**
     * Of two labels, the one arriving earlier (departing later), or the first of equally good ones.
     */
    @Nullable Label earlier(@Nullable Label label, @Nullable Label otherLabel) {
        if (label == null) {
            return otherLabel;
        }
        if (otherLabel == null) {
            return label;
        }

        int time = label.targetTime();
        int otherTime = otherLabel.targetTime();
        boolean otherIsEarlier = timeType == TimeType.DEPARTURE ? otherTime < time : otherTime > time;
        return otherIsEarlier ? otherLabel : label;
    }

    /**
     * Checks if the stop was marked in the current round.
     *
     * @param stopIdx the index of the stop to check.
     * @return true if the stop was marked in this round, false otherwise.
     */
    boolean isMarkedThisRound(int stopIdx) {
        return markedStopsMaskThisRound.get(stopIdx);
    }

    /**
     * Checks if the stop has been marked for the next round.
     *
     * @param stopIdx the index of the stop to check.
     * @return true if the stop is marked for the next round, false otherwise.
     */
    boolean isMarkedNextRound(int stopIdx) {
        return markedStopsMaskNextRound.get(stopIdx);
    }

    /**
     * Checks if the current round improved the best arrival on a trip at the stop.
     *
     * @param stopIdx the index of the stop to check.
     * @return true if the stop was reached on a trip earlier (later, routing backward) than before.
     */
    boolean isTripArrivalImproved(int stopIdx) {
        return tripArrivalImprovedMask.get(stopIdx);
    }

    /**
     * Marks the stop for the next round.
     *
     * @param stopIdx the index of the stop to mark.
     */
    void mark(int stopIdx) {
        markedStopsMaskNextRound.set(stopIdx);
    }

    /**
     * Unmarks the stop for the next round.
     *
     * @param stopIdx the index of the stop to unmark.
     */
    void unmark(int stopIdx) {
        markedStopsMaskNextRound.clear(stopIdx);
    }

    /**
     * Checks if any stops have been marked for the next round.
     */
    boolean hasMarkedStops() {
        return !markedStopsMaskNextRound.isEmpty();
    }

    /**
     * Gets the next stop marked in the current round, in the order of the stops.
     *
     * @param fromStopIdx the index of the stop to look from, inclusive.
     * @return the index of the stop, or -1 if no stop from there on is marked.
     */
    int nextStopMarkedThisRound(int fromStopIdx) {
        return markedStopsMaskThisRound.nextSetBit(fromStopIdx);
    }

    /**
     * Gets the next stop marked for the next round, in the order of the stops.
     *
     * @param fromStopIdx the index of the stop to look from, inclusive.
     * @return the index of the stop, or -1 if no stop from there on is marked.
     */
    int nextStopMarkedNextRound(int fromStopIdx) {
        return markedStopsMaskNextRound.nextSetBit(fromStopIdx);
    }

    /**
     * Gets the next stop whose best arrival on a trip the current round improved, in the order of the stops.
     *
     * @param fromStopIdx the index of the stop to look from, inclusive.
     * @return the index of the stop, or -1 if there is none from there on.
     */
    int nextStopWithTripArrivalImproved(int fromStopIdx) {
        return tripArrivalImprovedMask.nextSetBit(fromStopIdx);
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
