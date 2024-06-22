package ch.naviqore.raptor;

import lombok.extern.log4j.Log4j2;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static ch.naviqore.raptor.Raptor.NO_INDEX;

@Log4j2
public class FootpathRelaxer {

    private final Transfer[] transfers;
    private final Stop[] stops;
    private final int[] stopRoutes;

    private final StopTime[] stopTimes;
    private final Route[] routes;
    private final RouteStop[] routeStops;

    private final List<Raptor.Label[]> bestLabelsPerRound;
    private final int[] bestTimeForStops;

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
     * @param stopContext        the stop context data structure.
     * @param routeTraversal     the route traversal data structure.
     * @param bestLabelsPerRound the prepared layer from raptor wo keep track of the best labels per round.
     * @param bestTimeForStops   the global best time per stop.
     * @param timeType           the type of time to check for (arrival or departure), defines if stop is considered as
     *                           arrival or departure stop.
     * @param config             the query configuration.
     */
    FootpathRelaxer(StopContext stopContext, RouteTraversal routeTraversal, List<Raptor.Label[]> bestLabelsPerRound,
                    int[] bestTimeForStops, TimeType timeType, QueryConfig config) {
        // constant data structures
        this.transfers = stopContext.transfers();
        this.stops = stopContext.stops();
        this.stopRoutes = stopContext.stopRoutes();
        this.stopTimes = routeTraversal.stopTimes();
        this.routes = routeTraversal.routes();
        this.routeStops = routeTraversal.routeStops();
        // variable labels and best times (note: will vary also outside of relaxer, due to route scanning)
        this.bestLabelsPerRound = bestLabelsPerRound;
        this.bestTimeForStops = bestTimeForStops;
        // constant configuration of scanner
        this.minTransferDuration = config.getMinimumTransferDuration();
        this.maxWalkingDuration = config.getMaximumWalkingDuration();
        this.timeType = timeType;
    }

    /**
     * Relax all footpaths from source stops.
     *
     * @param round       the current round.
     * @param stopIndices the indices of the stops to be relaxed.
     * @return returns the newly marked stops due to the relaxation.
     */
    Set<Integer> relax(int round, int[] stopIndices) {
        Set<Integer> newlyMarkedStops = new HashSet<>();

        for (int sourceStopIdx : stopIndices) {
            expandFootpathsFromStop(sourceStopIdx, round, newlyMarkedStops);
        }

        return newlyMarkedStops;
    }

    /**
     * Relax all footpaths from source stops.
     *
     * @param round       the current round.
     * @param stopIndices the indices of the stops to be relaxed.
     * @return returns the newly marked stops due to the relaxation.
     */
    Set<Integer> relax(int round, Collection<Integer> stopIndices) {
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
        Raptor.Label previousLabel = bestLabelsPerRound.get(round)[stopIdx];

        // do not relax footpath from stop that was only reached by footpath in the same round
        if (previousLabel == null || previousLabel.type() == Raptor.LabelType.TRANSFER) {
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
            if (comparableTargetTime * timeDirection >= bestTimeForStops[transfer.targetStopIdx()] * timeDirection) {
                continue;
            }

            log.debug("Stop {} was improved by transfer from stop {}", targetStop.id(), sourceStop.id());
            // update best times with comparable target time
            bestTimeForStops[transfer.targetStopIdx()] = comparableTargetTime;
            // add real target time to label
            bestLabelsPerRound.get(round)[transfer.targetStopIdx()] = new Raptor.Label(sourceTime, targetTime,
                    Raptor.LabelType.TRANSFER, i, NO_INDEX, transfer.targetStopIdx(),
                    bestLabelsPerRound.get(round)[stopIdx]);
            markedStops.add(transfer.targetStopIdx());
        }
    }
}
