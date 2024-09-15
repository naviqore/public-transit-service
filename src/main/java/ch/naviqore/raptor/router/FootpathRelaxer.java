package ch.naviqore.raptor.router;

import ch.naviqore.raptor.TimeType;
import lombok.extern.slf4j.Slf4j;

import static ch.naviqore.raptor.router.QueryState.NO_INDEX;

@Slf4j
class FootpathRelaxer {

    private final Transfer[] transfers;
    private final Stop[] stops;

    private final int minTransferDuration;
    private final int maxWalkingDuration;
    private final TimeType timeType;

    private final QueryState queryState;

    /**
     * @param queryState              the query state with the best time per stop and label per stop and round.
     * @param raptorData              the current raptor data structures.
     * @param minimumTransferDuration The minimum transfer duration time, since this is intended as rest period (e.g.
     *                                coffee break) it is added to the walk time.
     * @param maximumWalkingDuration  The maximum walking duration to reach the target stop. If the walking duration
     *                                exceeds this value, the target stop is not reached.
     * @param timeType                the time type (arrival or departure).
     */
    FootpathRelaxer(QueryState queryState, RaptorData raptorData, int minimumTransferDuration,
                    int maximumWalkingDuration, TimeType timeType) {
        // constant data structures
        this.transfers = raptorData.getStopContext().transfers();
        this.stops = raptorData.getStopContext().stops();
        // constant configuration of relaxer
        this.minTransferDuration = minimumTransferDuration;
        this.maxWalkingDuration = maximumWalkingDuration;
        this.timeType = timeType;
        // note: will also change outside of relaxer, due to route scanning
        this.queryState = queryState;
    }

    /**
     * Relax all footpaths from all initial source stops.
     *
     * @param markedStopsMask the mask of the stops to be relaxed.
     */
    void relaxInitial(boolean[] markedStopsMask) {
        log.debug("Initial relaxing of footpaths for source stops");
        relax(0, markedStopsMask);
    }

    /**
     * Relax all footpaths from marked stops.
     *
     * @param round           the current round.
     * @param markedStopsMask the mask of the stops to be relaxed.
     */
    void relax(int round, boolean[] markedStopsMask) {
        log.debug("Relaxing footpaths for round {}", round);
        // to prevent extending transfers from stops that were only reached by footpath in the same round
        boolean[] routeMarkedStops = markedStopsMask.clone();

        for (int sourceStopIdx = 0; sourceStopIdx < markedStopsMask.length; sourceStopIdx++) {
            if (!routeMarkedStops[sourceStopIdx]) {
                continue;
            }
            expandFootpathsFromStop(sourceStopIdx, round, markedStopsMask);
        }
    }

    /**
     * Expands all transfers between stops from a given stop. If a transfer improves the target time at the target stop,
     * then the target stop is marked for the next round. And the improved target time is stored in the bestTimes array
     * and the bestLabelPerRound list (including the new transfer label).
     *
     * @param stopIdx         the index of the stop to expand transfers from.
     * @param round           the current round to relax footpaths for.
     * @param markedStopsMask a mask of stop indices that have been marked for scanning in the next round, which will be
     *                        extended if new stops improve due to relaxation.
     */
    private void expandFootpathsFromStop(int stopIdx, int round, boolean[] markedStopsMask) {
        // if stop has no transfers, then no footpaths can be expanded
        if (stops[stopIdx].numberOfTransfers() == 0) {
            return;
        }
        Stop sourceStop = stops[stopIdx];
        QueryState.Label previousLabel = queryState.getLabel(round, stopIdx);

        // do not relax footpath from stop that was only reached by footpath in the same round
        if (previousLabel == null || previousLabel.type() == QueryState.LabelType.TRANSFER) {
            return;
        }

        int sourceTime = previousLabel.targetTime();
        int timeDirection = timeType == TimeType.DEPARTURE ? 1 : -1;

        for (int i = sourceStop.transferIdx(); i < sourceStop.transferIdx() + sourceStop.numberOfTransfers(); i++) {
            Transfer transfer = transfers[i];
            Stop targetStop = stops[transfer.targetStopIdx()];
            int duration = transfer.duration();
            if (maxWalkingDuration < duration) {
                continue;
            }

            // calculate the target time for the transfer in the given time direction
            int targetTime = sourceTime + timeDirection * (transfer.duration() + minTransferDuration);

            // subtract the same stop transfer time from the walk transfer target time. This accounts for the case when
            // the walk transfer would allow to catch an earlier trip, since the route target time does not yet include
            // the same stop transfer time.
            int comparableTargetTime = targetTime - targetStop.sameStopTransferTime() * timeDirection;

            // if label is not improved, continue
            if (comparableTargetTime * timeDirection >= queryState.getComparableBestTime(
                    transfer.targetStopIdx()) * timeDirection) {
                continue;
            }

            log.debug("Stop {} was improved by transfer from stop {}", targetStop.id(), sourceStop.id());
            // update best times with comparable target time
            queryState.setBestTime(transfer.targetStopIdx(), comparableTargetTime);
            // add real target time to label
            QueryState.Label label = new QueryState.Label(sourceTime, targetTime, QueryState.LabelType.TRANSFER, i,
                    NO_INDEX, transfer.targetStopIdx(), queryState.getLabel(round, stopIdx));
            queryState.setLabel(round, transfer.targetStopIdx(), label);
            // mark stop as improved
            markedStopsMask[transfer.targetStopIdx()] = true;
        }
    }
}
