package ch.naviqore.raptor.router;

import ch.naviqore.raptor.TimeType;
import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static ch.naviqore.raptor.router.QueryState.NO_INDEX;

@Slf4j
class FootpathRelaxer {

    private final Transfer[] transfers;
    private final Stop[] stops;

    private final int minTransferDuration;
    private final int maxWalkingDuration;
    private final TimeType timeType;
    private final boolean allowSourceTransfers;
    private final boolean allowTargetTransfers;
    private final Set<Integer> targetStopIndices;

    private final QueryState queryState;

    /**
     * @param queryState                the query state with the best time per stop and label per stop and round.
     * @param raptorData                the current raptor data structures.
     * @param minimumTransferDuration   The minimum transfer duration time, since this is intended as rest period (e.g.
     *                                  coffee break) it is added to the walk time.
     * @param maximumWalkingDuration    The maximum walking duration to reach the target stop. If the walking duration
     *                                  exceeds this value, the target stop is not reached.
     * @param timeType                  the time type (arrival or departure).
     * @param allowSourceTransfers      defines if transfers from source stops are possible
     * @param allowTargetTransfers      defines if transfers to target stops are possible
     * @param targetStopIndices         array holding all indices of target stops, used to check if transfer target is
     *                                  target stop in case allowTargetTransfers is false
     */
    FootpathRelaxer(QueryState queryState, RaptorData raptorData, int minimumTransferDuration,
                    int maximumWalkingDuration, TimeType timeType, boolean allowSourceTransfers,
                    boolean allowTargetTransfers, int[] targetStopIndices) {
        // constant data structures
        this.transfers = raptorData.getStopContext().transfers();
        this.stops = raptorData.getStopContext().stops();
        // constant configuration of relaxer
        this.minTransferDuration = minimumTransferDuration;
        this.maxWalkingDuration = maximumWalkingDuration;
        this.timeType = timeType;
        // note: will also change outside of relaxer, due to route scanning
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
     * Relax all footpaths from marked stops.
     *
     * @param round the current round.
     */
    void relax(int round) {
        log.debug("Relaxing footpaths for round {}", round);
        // to prevent extending transfers from stops that were only reached by footpath in the same round
        boolean[] routeMarkedStops = queryState.cloneMarkedStopsMaskNextRound();

        for (int sourceStopIdx = 0; sourceStopIdx < routeMarkedStops.length; sourceStopIdx++) {
            if (!routeMarkedStops[sourceStopIdx]) {
                continue;
            }
            if (!allowSourceTransfers) {
                // in round 0 all transfers to expand are from source stops
                if (round == 0) {
                    continue;
                } else if (round == 1 && queryState.getLabel(round, sourceStopIdx) == null) {
                    // this case handles "initial transfer relaxation" in round 1 when doInitialTransferRelaxation is
                    // false, using label from round 0 because source stops are always round 0!
                    QueryState.Label label = queryState.getLabel(0, sourceStopIdx);
                    if (label != null && label.type() == QueryState.LabelType.INITIAL) {
                        continue;
                    }
                }
            }

            expandFootpathsFromStop(sourceStopIdx, round);
        }
    }

    /**
     * Expands all transfers between stops from a given stop. If a transfer improves the target time at the target stop,
     * then the target stop is marked for the next round. And the improved target time is stored in the bestTimes array
     * and the bestLabelPerRound list (including the new transfer label).
     *
     * @param stopIdx the index of the stop to expand transfers from.
     * @param round   the current round to relax footpaths for.
     */
    private void expandFootpathsFromStop(int stopIdx, int round) {
        // if stop has no transfers, then no footpaths can be expanded
        if (stops[stopIdx].numberOfTransfers() == 0) {
            return;
        }
        Stop sourceStop = stops[stopIdx];
        QueryState.Label previousLabel = queryState.getLabel(round, stopIdx);

        // handle case where initial transfer relaxation was not performed
        if( round == 1 && previousLabel == null ){
            previousLabel = queryState.getLabel(0, stopIdx);
        }

        // do not relax footpath from stop that was only reached by footpath in the same round
        if (previousLabel == null || previousLabel.type() == QueryState.LabelType.TRANSFER) {
            return;
        }

        int sourceTime = previousLabel.targetTime();
        int timeDirection = timeType == TimeType.DEPARTURE ? 1 : -1;

        for (int i = sourceStop.transferIdx(); i < sourceStop.transferIdx() + sourceStop.numberOfTransfers(); i++) {
            Transfer transfer = transfers[i];
            if (!allowTargetTransfers && targetStopIndices.contains(transfer.targetStopIdx())) {
                continue;
            }
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
                    NO_INDEX, transfer.targetStopIdx(), previousLabel);
            queryState.setLabel(round, transfer.targetStopIdx(), label);
            queryState.mark(transfer.targetStopIdx());
        }
    }
}
