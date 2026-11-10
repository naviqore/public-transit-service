package org.naviqore.raptor.router;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
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

    private final int minTransferDuration;
    private final int maxWalkDuration;
    private final TimeType timeType;
    private final boolean allowSourceTransfers;
    private final boolean allowTargetTransfers;
    private final Set<Integer> targetStopIndices;

    private final QueryState queryState;

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
        // constant data structures
        this.transfers = raptorData.getStopContext().transfers();
        this.stops = raptorData.getStopContext().stops();
        // constant configuration of relaxer
        this.minTransferDuration = minimumTransferDuration;
        this.maxWalkDuration = maximumWalkDuration;
        this.timeType = timeType;
        // note: will also change outside relaxer, due to route scanning
        this.queryState = queryState;
        this.allowSourceTransfers = allowSourceTransfers;
        this.allowTargetTransfers = allowTargetTransfers;
        this.targetStopIndices = IntStream.of(targetStopIndices).boxed().collect(Collectors.toSet());
    }

    /**
     * Relax all footpaths from all initial source stops.
     */
    void relaxInitial() {
        log.debug("Initial relaxing of footpaths for source stops");
        relax(0);
    }

    /**
     * Relax all footpaths from the stops reached in a round that a connection may walk on from: in the first round (0),
     * the source stops at the start of the query, if transfers from them are allowed; in later rounds, the stops whose
     * best arrival on a trip the round improved. A walk never continues another walk, and walks from a stop reached on
     * foot earlier still start at the stop's arrival on a trip, see {@link QueryState}.
     * <p>
     * The labels to walk from are taken before any walk of the round sets a label, so that the walks do not depend on
     * the order of the stops.
     *
     * @param round the current round.
     */
    void relax(int round) {
        log.debug("Relaxing footpaths for round {}", round);
        List<QueryState.Label> labelsToWalkFrom = new ArrayList<>();
        for (int stopIdx = nextStopToWalkFrom(0, round); stopIdx >= 0; stopIdx = nextStopToWalkFrom(stopIdx + 1,
                round)) {
            QueryState.Label label = getLabelToWalkFrom(stopIdx, round);
            if (label != null && stops[stopIdx].numberOfTransfers() > 0) {
                labelsToWalkFrom.add(label);
            }
        }

        for (QueryState.Label label : labelsToWalkFrom) {
            expandFootpathsFromStop(label, round);
        }
    }

    /**
     * Gets the next stop a round may walk on from, in the order of the stops: in later rounds a stop whose best arrival
     * on a trip the round improved, in the first a source stop.
     */
    private int nextStopToWalkFrom(int fromStopIdx, int round) {
        if (round > 0) {
            return queryState.nextStopWithTripArrivalImproved(fromStopIdx);
        }
        return queryState.nextStopMarkedNextRound(fromStopIdx);
    }

    private QueryState.@Nullable Label getLabelToWalkFrom(int stopIdx, int round) {
        if (round > 0) {
            return queryState.isTripArrivalImproved(stopIdx) ? queryState.getTripLabel(round, stopIdx) : null;
        }

        // in round 0 all labels are those of the source stops at the start of the query
        if (!allowSourceTransfers || !queryState.isMarkedNextRound(stopIdx)) {
            return null;
        }
        QueryState.Label label = queryState.getLabel(0, stopIdx);
        return label != null && label.type() == QueryState.LabelType.INITIAL ? label : null;
    }

    /**
     * Expands all transfers between stops from the stop of a label. If a transfer improves the target time at the
     * target stop, then the target stop is marked for the next round. And the improved target time is stored in the
     * bestTimes array and the bestLabelPerRound list (including the new transfer label).
     *
     * @param previousLabel the label to walk on from, at its stop.
     * @param round         the current round to relax footpaths for.
     */
    private void expandFootpathsFromStop(QueryState.Label previousLabel, int round) {
        Stop sourceStop = stops[previousLabel.stopIdx()];
        int sourceTime = previousLabel.targetTime();
        int timeDirection = timeType == TimeType.DEPARTURE ? 1 : -1;

        for (int i = sourceStop.transferIdx(); i < sourceStop.transferIdx() + sourceStop.numberOfTransfers(); i++) {
            Transfer transfer = transfers[i];
            if (!allowTargetTransfers && targetStopIndices.contains(transfer.targetStopIdx())) {
                continue;
            }
            int duration = transfer.duration();
            if (maxWalkDuration < duration) {
                continue;
            }

            // calculate the target time for the transfer in the given time direction
            int targetTime = sourceTime + timeDirection * (transfer.duration() + minTransferDuration);

            // compare the walk as it allows to board a trip: right away, without the same stop transfer time a route
            // target time does not yet include
            int comparableTargetTime = queryState.getComparableTime(transfer.targetStopIdx(), targetTime,
                    QueryState.LabelType.TRANSFER);

            // if label is not improved, continue
            if (comparableTargetTime * timeDirection >= queryState.getComparableBestTime(
                    transfer.targetStopIdx()) * timeDirection) {
                continue;
            }

            // update best times with comparable target time
            queryState.setBestTime(transfer.targetStopIdx(), comparableTargetTime);
            // add real target time to label
            QueryState.Label label = new QueryState.Label(sourceTime, targetTime, QueryState.LabelType.TRANSFER, i,
                    NO_INDEX, transfer.targetStopIdx(), previousLabel);
            queryState.setLabel(round, transfer.targetStopIdx(), label);
            queryState.mark(transfer.targetStopIdx());
        }
    }
}
