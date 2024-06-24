package ch.naviqore.raptor.impl;

import ch.naviqore.raptor.TimeType;
import lombok.extern.log4j.Log4j2;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import static ch.naviqore.raptor.impl.Objective.NO_INDEX;

@Log4j2
class FootpathRelaxer {

    private final Transfer[] transfers;
    private final Stop[] stops;

    private final Objective objective;

    /**
     * The minimum transfer duration time, since this is intended as rest period (e.g. coffee break) it is added to the
     * walk time.
     */
    private final int minTransferDuration;
    /**
     * The maximum walking duration to reach the target stop. If the walking duration exceeds this value, the target
     * stop is not reached.
     */
    private final int maxWalkingDuration;
    private final TimeType timeType;

    /**
     * @param raptorRouter the current raptor instance for access to the data structures.
     * @param objective    the best time per stop and label per stop and round.
     */
    FootpathRelaxer(RaptorRouter raptorRouter, Objective objective) {
        // constant data structures
        this.transfers = raptorRouter.getStopContext().transfers();
        this.stops = raptorRouter.getStopContext().stops();
        // note: objective will change also outside of relaxer, due to route scanning
        this.objective = objective;
        // constant configuration of relaxer
        this.minTransferDuration = objective.getConfig().getMinimumTransferDuration();
        this.maxWalkingDuration = objective.getConfig().getMaximumWalkingDuration();
        this.timeType = objective.getTimeType();
    }

    /**
     * Relax all footpaths from all initial source stops.
     *
     * @param stopIndices the indices of the stops to be relaxed.
     * @return returns the newly marked stops due to the relaxation.
     */
    Set<Integer> relaxInitial(int[] stopIndices) {
        log.debug("Initial relaxing of footpaths for source stops");
        Set<Integer> newlyMarkedStops = new HashSet<>();

        for (int sourceStopIdx : stopIndices) {
            expandFootpathsFromStop(sourceStopIdx, 0, newlyMarkedStops);
        }

        return newlyMarkedStops;
    }

    /**
     * Relax all footpaths from marked stops.
     *
     * @param round       the current round.
     * @param stopIndices the indices of the stops to be relaxed.
     * @return returns the newly marked stops due to the relaxation.
     */
    Set<Integer> relax(int round, Collection<Integer> stopIndices) {
        log.debug("Relaxing footpaths for round {}", round);
        Set<Integer> newlyMarkedStops = new HashSet<>();

        for (int sourceStopIdx : stopIndices) {
            expandFootpathsFromStop(sourceStopIdx, round, newlyMarkedStops);
        }

        return newlyMarkedStops;
    }

    /**
     * Expands all transfers between stops from a given stop. If a transfer improves the target time at the target stop,
     * then the target stop is marked for the next round. And the improved target time is stored in the bestTimes array
     * and the bestLabelPerRound list (including the new transfer label).
     *
     * @param stopIdx     the index of the stop to expand transfers from.
     * @param round       the current round to relax footpaths for.
     * @param markedStops a set of stop indices that have been marked for scanning in the next round, which will be
     *                    extended if new stops improve due to relaxation.
     */
    private void expandFootpathsFromStop(int stopIdx, int round, Set<Integer> markedStops) {
        // if stop has no transfers, then no footpaths can be expanded
        if (stops[stopIdx].numberOfTransfers() == 0) {
            return;
        }
        Stop sourceStop = stops[stopIdx];
        Objective.Label previousLabel = objective.getLabel(round, stopIdx);

        // do not relax footpath from stop that was only reached by footpath in the same round
        if (previousLabel == null || previousLabel.type() == Objective.LabelType.TRANSFER) {
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
            if (comparableTargetTime * timeDirection >= objective.getBestTime(
                    transfer.targetStopIdx()) * timeDirection) {
                continue;
            }

            log.debug("Stop {} was improved by transfer from stop {}", targetStop.id(), sourceStop.id());
            // update best times with comparable target time
            objective.setBestTime(transfer.targetStopIdx(), comparableTargetTime);
            // add real target time to label
            Objective.Label label = new Objective.Label(sourceTime, targetTime, Objective.LabelType.TRANSFER, i,
                    NO_INDEX, transfer.targetStopIdx(), objective.getLabel(round, stopIdx));
            objective.setLabel(round, transfer.targetStopIdx(), label);
            // mark stop as improved
            markedStops.add(transfer.targetStopIdx());
        }
    }
}
