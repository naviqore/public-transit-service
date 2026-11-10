package org.naviqore.raptor.router;

import org.jspecify.annotations.Nullable;
import org.naviqore.raptor.TimeType;

/**
 * The label of the shortest travel time to each stop over the passes of an isoline range query, see {@link Query}. A
 * pass sets a label only where it improves on the passes before it, which started later in the window (earlier, routing
 * backward), and of those labels, its earliest arrival at a stop has the shortest travel time of the pass. After the
 * pass, that arrival replaces the stop's shortest so far if its travel time from the start of the pass is no longer, so
 * that of equally short travel times, the one of the pass nearest the start of the window is kept.
 */
final class ShortestTravelTimes {

    private final QueryState queryState;
    private final boolean forward;

    // the label of the earliest arrival at each stop the current pass improved, and those stops
    private final QueryState.@Nullable Label[] passLabels;
    private final int[] passStops;
    // the label of the shortest travel time of each stop, and that travel time from the start of the window
    private final QueryState.@Nullable Label[] labels;
    private final int[] travelTimes;
    private int passStopCount;

    ShortestTravelTimes(QueryState queryState, int numStops, TimeType timeType) {
        this.queryState = queryState;
        this.forward = timeType == TimeType.DEPARTURE;
        passLabels = new QueryState.Label[numStops];
        passStops = new int[numStops];
        labels = new QueryState.Label[numStops];
        travelTimes = new int[numStops];
    }

    /**
     * Collects the labels the current pass set in a round: of the stops marked for the next round, and of those whose
     * arrival on a trip the round improved.
     *
     * @param round the round.
     */
    void collect(int round) {
        for (int stopIdx = 0; stopIdx < passLabels.length; stopIdx++) {
            if (queryState.isTripArrivalImproved(stopIdx)) {
                collect(stopIdx, queryState.getTripLabel(round, stopIdx));
            }
            if (queryState.isMarkedNextRound(stopIdx)) {
                collect(stopIdx, queryState.getLabel(round, stopIdx));
            }
        }
    }

    private void collect(int stopIdx, QueryState.@Nullable Label label) {
        // the initial label of a source stop counts too: it is the start, which no connection back to it beats
        if (label == null) {
            return;
        }
        if (passLabels[stopIdx] == null) {
            passStops[passStopCount++] = stopIdx;
        }
        // of equally early arrivals, the first collected: of the lower round, and in a round the one on a trip
        passLabels[stopIdx] = queryState.earlier(passLabels[stopIdx], label);
    }

    /**
     * Ends the current pass, which started at an offset into the window.
     *
     * @param offset the offset of the pass into the window, in seconds.
     */
    void endPass(int offset) {
        for (int i = 0; i < passStopCount; i++) {
            int stopIdx = passStops[i];
            QueryState.Label label = passLabels[stopIdx];
            assert label != null;
            // the travel time but for the time the window starts at (ends at, routing backward), which all stops share:
            // the arrival less the offset, or the negated offset less the departure
            int travelTime = forward ? label.targetTime() - offset : -offset - label.targetTime();
            if (labels[stopIdx] == null || travelTime <= travelTimes[stopIdx]) {
                labels[stopIdx] = label;
                travelTimes[stopIdx] = travelTime;
            }
            passLabels[stopIdx] = null;
        }
        passStopCount = 0;
    }

    /**
     * The label of the shortest travel time of each stop, null for a stop no pass reached; the initial label for a
     * source stop not reached from another one.
     */
    QueryState.@Nullable Label[] labels() {
        return labels;
    }

}
