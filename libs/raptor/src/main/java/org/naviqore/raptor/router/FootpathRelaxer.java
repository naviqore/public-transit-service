package org.naviqore.raptor.router;

import lombok.extern.slf4j.Slf4j;
import org.naviqore.raptor.TimeType;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.naviqore.raptor.router.QueryState.NO_INDEX;

@Slf4j
class FootpathRelaxer {

    private final Transfer[] transfers;
    private final Stop[] stops;
    private final TripTransfers tripTransfers;

    private final int minTransferDuration;
    private final int maxWalkDuration;
    private final TimeType timeType;
    private final boolean allowSourceTransfers;
    private final boolean allowTargetTransfers;
    private final Set<Integer> targetStopIndices;

    private final QueryState queryState;

    // the time beyond which a label is of no use, see setTimeLimit
    private int timeLimit;

    /**
     * @param queryState              the query state with the best time per stop and label per stop and round.
     * @param raptorData              the current raptor data structures.
     * @param minimumTransferDuration The minimum transfer duration time, since this is intended as rest period (e.g.
     *                                coffee break) it is added to the walk time.
     * @param maximumWalkDuration     The maximum walk duration to reach the target stop. If the walk duration exceeds
     *                                this value, the target stop is not reached.
     * @param timeType                the time type (arrival or departure).
     * @param allowSourceTransfers    defines if transfers from source stops are possible
     * @param allowTargetTransfers    defines if transfers to target stops are possible
     * @param targetStopIndices       array holding all indices of target stops, used to check if transfer target is
     *                                target stop in case allowTargetTransfers is false
     */
    FootpathRelaxer(QueryState queryState, RaptorData raptorData, int minimumTransferDuration, int maximumWalkDuration,
                    TimeType timeType, boolean allowSourceTransfers, boolean allowTargetTransfers,
                    int[] targetStopIndices) {
        // constant data structures: the walks of the direction of the search
        this.transfers = raptorData.getStopContext().transfers(timeType);
        this.stops = raptorData.getStopContext().stops();
        this.tripTransfers = raptorData.getTripTransfers();
        // constant configuration of relaxer
        this.minTransferDuration = minimumTransferDuration;
        this.maxWalkDuration = maximumWalkDuration;
        this.timeType = timeType;
        // note: will also change outside relaxer, due to route scanning
        this.queryState = queryState;
        this.allowSourceTransfers = allowSourceTransfers;
        this.allowTargetTransfers = allowTargetTransfers;
        this.targetStopIndices = IntStream.of(targetStopIndices).boxed().collect(Collectors.toSet());
        this.timeLimit = timeType == TimeType.DEPARTURE ? QueryState.INFINITY : -QueryState.INFINITY;
    }

    /**
     * Sets the time beyond which a walk sets no label: later than it, or routing backward earlier. A label beyond the
     * best time at the target or the cut-off time of the query is removed when its round ends, and nothing that follows
     * from it is of use.
     *
     * @param timeLimit the time limit, or infinity (routing backward: minus infinity) for none.
     */
    void setTimeLimit(int timeLimit) {
        this.timeLimit = timeLimit;
    }

    /**
     * Relaxes the footpaths from the source stops at the start of a pass, if transfers from them are allowed: from each
     * source stop's own label, also where a pass reached the stop earlier on foot from another source stop, since a
     * stop reached on foot cannot be walked on from.
     *
     * @param sourceLabels the labels of the source stops at the start of the pass.
     */
    void relaxInitial(QueryState.Label[] sourceLabels) {
        log.debug("Initial relaxing of footpaths for source stops");
        if (!allowSourceTransfers) {
            return;
        }

        for (QueryState.Label label : sourceLabels) {
            if (stops[label.stopIdx()].numberOfTransfers(timeType) > 0) {
                expandFootpathsFromStop(label, 0);
            }
        }
    }

    /**
     * Relax all footpaths from the stops whose best arrival on a trip a round improved. A walk never continues another
     * walk, and walks from a stop reached on foot earlier still start at the stop's arrival on a trip, see
     * {@link QueryState}.
     * <p>
     * The labels to walk from are taken before any walk of the round sets a label, so that the walks do not depend on
     * the order of the stops.
     *
     * @param round the current round, after the first.
     */
    void relax(int round) {
        log.debug("Relaxing footpaths for round {}", round);
        List<QueryState.Label> labelsToWalkFrom = new ArrayList<>();
        for (int stopIdx = queryState.nextStopWithTripArrivalImproved(
                0); stopIdx >= 0; stopIdx = queryState.nextStopWithTripArrivalImproved(stopIdx + 1)) {
            QueryState.Label label = queryState.getTripLabel(round, stopIdx);
            if (label != null && stops[stopIdx].numberOfTransfers(timeType) > 0) {
                labelsToWalkFrom.add(label);
            }
        }

        for (QueryState.Label label : labelsToWalkFrom) {
            expandFootpathsFromStop(label, round);
        }
    }

    /**
     * Expands all transfers between stops from the stop of a label: departing the walks from it, arriving the walks to
     * it, which the search follows backward. If a transfer improves the target time at the target stop, then the target
     * stop is marked for the next round. And the improved target time is stored in the bestTimes array and the
     * bestLabelPerRound list (including the new transfer label).
     *
     * @param previousLabel the label to walk on from, at its stop.
     * @param round         the current round to relax footpaths for.
     */
    private void expandFootpathsFromStop(QueryState.Label previousLabel, int round) {
        int sourceIdx = previousLabel.stopIdx();
        Stop sourceStop = stops[sourceIdx];
        int sourceTime = previousLabel.targetTime();
        int timeDirection = timeType == TimeType.DEPARTURE ? 1 : -1;

        // a slot of trip transfers has the walks of its stop: if the stop was reached on a trip as early, it has walked
        // them, and the slot only walks to the stops its rules end at, where the walks land in slots of its own
        int sourceStopIdx = tripTransfers.stopOf(sourceIdx);
        int[] walks = sourceStopIdx != sourceIdx && sourceTime * timeDirection >= queryState.getBestTripTime(
                sourceStopIdx) * timeDirection ? tripTransfers.walksToOwnSlots(sourceIdx) : null;

        int numberOfWalks = walks == null ? sourceStop.numberOfTransfers(timeType) : walks.length;
        for (int w = 0; w < numberOfWalks; w++) {
            int walk = walks == null ? w : walks[w];
            int i = sourceStop.transferIdx(timeType) + walk;
            Transfer transfer = transfers[i];
            if (!allowTargetTransfers && targetStopIndices.contains(transfer.targetStopIdx())) {
                continue;
            }
            int duration = transfer.duration();
            if (maxWalkDuration < duration) {
                continue;
            }

            // a walk from an arrival whose route changes to routes at the target stop along trip transfers lands in a
            // slot, see TripTransfers
            int targetIdx = tripTransfers.walkTarget(sourceIdx, walk, transfer.targetStopIdx());

            // calculate the target time for the transfer in the given time direction
            int targetTime = sourceTime + timeDirection * (transfer.duration() + minTransferDuration);
            if (targetTime * timeDirection > timeLimit * timeDirection) {
                continue;
            }

            // compare the walk as it allows to board a trip: right away, without the same stop transfer time a route
            // target time does not yet include
            int comparableTargetTime = queryState.getComparableTime(targetIdx, targetTime,
                    QueryState.LabelType.TRANSFER);

            // if label is not improved, continue
            if (comparableTargetTime * timeDirection >= queryState.getComparableBestTime(targetIdx) * timeDirection) {
                continue;
            }

            // update best times with comparable target time
            queryState.setBestTime(targetIdx, comparableTargetTime);
            // add real target time to label
            QueryState.Label label = new QueryState.Label(sourceTime, targetTime, QueryState.LabelType.TRANSFER, i,
                    NO_INDEX, targetIdx, previousLabel);
            queryState.setLabel(round, targetIdx, label);
            queryState.mark(targetIdx);
        }
    }
}
