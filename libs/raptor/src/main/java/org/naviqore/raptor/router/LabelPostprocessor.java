package org.naviqore.raptor.router;

import org.jspecify.annotations.Nullable;
import org.naviqore.raptor.Connection;
import org.naviqore.raptor.Leg;
import org.naviqore.raptor.TimeType;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;

import static org.naviqore.raptor.router.QueryState.INFINITY;

/**
 * Postprocessing of the RAPTOR algorithm results. Reconstructs connections from the labels per round.
 */
class LabelPostprocessor {

    private final Stop[] stops;
    private final int[] stopTimes;
    private final Route[] routes;
    private final RouteStop[] routeStops;

    private final TimeType timeType;
    private final LocalDate referenceDate;
    private final ZoneId defaultZoneId;

    /**
     * Postprocessor to convert labels into connections
     *
     * @param raptorData        the current raptor instance for access to the data structures.
     * @param timeType          the time type (arrival or departure).
     * @param referenceDateTime the reference datetime used for timezone calculations.
     */
    LabelPostprocessor(RaptorData raptorData, TimeType timeType, OffsetDateTime referenceDateTime) {
        this.stops = raptorData.getStopContext().stops();
        this.stopTimes = raptorData.getRouteTraversal().stopTimes();
        this.routes = raptorData.getRouteTraversal().routes();
        this.routeStops = raptorData.getRouteTraversal().routeStops();
        this.timeType = timeType;
        this.referenceDate = referenceDateTime.toLocalDate();
        this.defaultZoneId = referenceDateTime.getOffset();
    }

    /**
     * Reconstructs isolines from the best labels per round.
     *
     * @param bestLabelsPerRound the best labels per round.
     * @return a map containing the best connection to reach all stops.
     */
    Map<String, Connection> reconstructIsolines(List<QueryState.Label[]> bestLabelsPerRound) {
        Map<String, Connection> isolines = new HashMap<>();
        for (int i = 0; i < stops.length; i++) {
            Stop stop = stops[i];
            QueryState.Label bestLabelForStop = getBestLabelForStop(bestLabelsPerRound, i);
            if (bestLabelForStop != null && bestLabelForStop.type() != QueryState.LabelType.INITIAL) {
                Connection connection = reconstructConnectionFromLabel(bestLabelForStop);
                isolines.put(stop.id(), connection);
            }
        }

        return isolines;
    }

    /**
     * Reconstructs pareto-optimal connections from the best labels per round.
     *
     * @param bestLabelsPerRound the best labels per round.
     * @param targetStops        map of target stop indices and walk durations to destination.
     * @return a list of pareto-optimal connections.
     */
    List<Connection> reconstructParetoOptimalSolutions(List<QueryState.Label[]> bestLabelsPerRound,
                                                       Map<Integer, Integer> targetStops) {
        List<Connection> connections = new ArrayList<>();
        int bestTime = timeType == TimeType.DEPARTURE ? INFINITY : -INFINITY;

        // iterate over all rounds
        for (QueryState.Label[] labels : bestLabelsPerRound) {
            QueryState.Label label = findBestLabelInRound(labels, targetStops, bestTime);

            if (label != null) {
                // update best time for Pareto filtering
                int walkTime = targetStops.get(label.stopIdx());
                bestTime = (timeType == TimeType.DEPARTURE) ? label.targetTime() + walkTime : label.targetTime() - walkTime;

                Connection connection = reconstructConnectionFromLabel(label);
                if (connection != null) {
                    connections.add(connection);
                }
            }
        }

        return connections;
    }

    /**
     * Identifies the best label for a set of target stops within a single round.
     *
     * @param labels          array of labels for the current round.
     * @param targetStops     map of target stops and their walk durations.
     * @param currentBestTime the current best time across all previous rounds.
     * @return the best label for this round, or null if no improvement was found.
     */
    private QueryState.@Nullable Label findBestLabelInRound(QueryState.Label[] labels,
                                                            Map<Integer, Integer> targetStops, int currentBestTime) {
        QueryState.Label bestLabel = null;
        int bestRoundTime = currentBestTime;

        for (Map.Entry<Integer, Integer> entry : targetStops.entrySet()) {
            int stopIdx = entry.getKey();
            int walkTime = entry.getValue();
            QueryState.Label label = labels[stopIdx];

            if (label == null) {
                continue;
            }

            if (timeType == TimeType.DEPARTURE) {
                int arrivalTime = label.targetTime() + walkTime;
                if (arrivalTime < bestRoundTime) {
                    bestLabel = label;
                    bestRoundTime = arrivalTime;
                }
            } else {
                int departureTime = label.targetTime() - walkTime;
                if (departureTime > bestRoundTime) {
                    bestLabel = label;
                    bestRoundTime = departureTime;
                }
            }
        }

        return bestLabel;
    }

    /**
     * Reconstruct a connection by backtracking through labels and applying optimizations.
     *
     * @param finalLabel the label at the target stop.
     * @return the reconstructed connection, or null if no legs were found.
     */
    private @Nullable Connection reconstructConnectionFromLabel(QueryState.Label finalLabel) {
        // collect labels by backtracking via linked labels from target to source
        ArrayList<QueryState.Label> labels = collectLabels(finalLabel);

        // combine labels where possible to merge adjacent transfers and routes into optimized route legs
        maybeCombineFirstTwoLabels(labels);
        maybeCombineLastTwoLabels(labels);

        // reverse to chronological order (source to target) for leg construction and timezone propagation
        Collections.reverse(labels);

        // build the connection
        RaptorConnection connection = new RaptorConnection();
        for (int i = 0; i < labels.size(); i++) {
            RaptorLeg leg = createLeg(labels, i);
            connection.addLeg(leg);
        }

        // if the journey contains legs, finalize and initialize the connection
        if (!connection.getLegs().isEmpty()) {
            connection.initialize();
            return connection;
        }

        // no legs were found
        return null;
    }

    /**
     * Backtracks from a given label to the initial label to collect the sequence of labels.
     *
     * @param label the starting label, the target stop.
     * @return an ordered list of labels from target to source.
     */
    private ArrayList<QueryState.Label> collectLabels(QueryState.Label label) {
        ArrayList<QueryState.Label> labels = new ArrayList<>();
        while (label.type() != QueryState.LabelType.INITIAL) {
            assert label.previous() != null;
            labels.add(label);
            label = label.previous();
        }

        return labels;
    }

    /**
     * Creates a single leg from the list of chronological labels at the given index. Handles timezone resolution logic
     * by looking at adjacent route legs.
     *
     * @param chronologicalLabels the list of labels in chronological order.
     * @param index               the index of the current label to convert.
     * @return the constructed RaptorLeg.
     */
    private RaptorLeg createLeg(List<QueryState.Label> chronologicalLabels, int index) {
        QueryState.Label currentLabel = chronologicalLabels.get(index);
        LegContext context = extractLegContext(currentLabel);

        ZoneId departureZone;
        ZoneId arrivalZone;

        if (context.type == Leg.Type.ROUTE) {
            Route route = routes[currentLabel.routeOrTransferIdx()];
            // for routes, the agency timezone applies to both ends
            departureZone = route.zoneId();
            arrivalZone = route.zoneId();
        } else {
            // for walks, infer timezone from adjacent route legs
            departureZone = resolveTransferTimezone(chronologicalLabels, index, true);
            arrivalZone = resolveTransferTimezone(chronologicalLabels, index, false);

            // sync zones: if one end is specific and the other is default, adopt the specific one
            if (!departureZone.equals(defaultZoneId) && arrivalZone.equals(defaultZoneId)) {
                arrivalZone = departureZone;
            } else if (!arrivalZone.equals(defaultZoneId) && departureZone.equals(defaultZoneId)) {
                departureZone = arrivalZone;
            }
        }

        OffsetDateTime departureTime = DateTimeConverter.toOffsetDateTime(context.departureTimestamp, referenceDate,
                departureZone);
        OffsetDateTime arrivalTime = DateTimeConverter.toOffsetDateTime(context.arrivalTimestamp, referenceDate,
                arrivalZone);

        return new RaptorLeg(context.routeId, context.tripId, context.fromStopId, context.toStopId, departureTime,
                arrivalTime, context.type);
    }

    /**
     * Resolves the timezone for a transfer end by looking at adjacent legs.
     *
     * @param labels      the list of chronological labels.
     * @param index       the index of the current transfer label.
     * @param isDeparture true if resolving departure zone (look behind), false if arrival zone (look ahead).
     * @return the resolved ZoneId or the defaultZoneId.
     */
    private ZoneId resolveTransferTimezone(List<QueryState.Label> labels, int index, boolean isDeparture) {
        int adjacentIndex = isDeparture ? index - 1 : index + 1;

        if (adjacentIndex >= 0 && adjacentIndex < labels.size()) {
            QueryState.Label adjacentLabel = labels.get(adjacentIndex);
            if (adjacentLabel.type() == QueryState.LabelType.ROUTE) {
                return routes[adjacentLabel.routeOrTransferIdx()].zoneId();
            }
        }

        return defaultZoneId;
    }

    /**
     * Extracts raw data (IDs, timestamps, types) from a label based on the query TimeType.
     *
     * @param label the label to extract data from.
     * @return a LegContext containing the raw leg data.
     */
    private LegContext extractLegContext(QueryState.Label label) {
        assert label.previous() != null;
        String fromStopId;
        String toStopId;
        int departureTimestamp;
        int arrivalTimestamp;

        if (timeType == TimeType.DEPARTURE) {
            fromStopId = stops[label.previous().stopIdx()].id();
            toStopId = stops[label.stopIdx()].id();
            departureTimestamp = label.sourceTime();
            arrivalTimestamp = label.targetTime();
        } else {
            fromStopId = stops[label.stopIdx()].id();
            toStopId = stops[label.previous().stopIdx()].id();
            departureTimestamp = label.targetTime();
            arrivalTimestamp = label.sourceTime();
        }

        String routeId;
        String tripId = null;
        Leg.Type type;

        if (label.type() == QueryState.LabelType.ROUTE) {
            Route route = routes[label.routeOrTransferIdx()];
            routeId = route.id();
            tripId = route.tripIds()[label.tripOffset()];
            type = Leg.Type.ROUTE;
        } else {
            routeId = String.format("transfer_%s_%s", fromStopId, toStopId);
            type = Leg.Type.WALK_TRANSFER;
        }

        return new LegContext(routeId, tripId, fromStopId, toStopId, departureTimestamp, arrivalTimestamp, type);
    }

    /**
     * Check if first two labels can be combined to one label to improve the target time. This is to catch an edge case
     * where a transfer overwrote the best time route label because the same stop transfer time was subtracted from the
     * walk time, this can be the case in local transit where stops are very close and can not be caught during routing,
     * as it is not always clear if a transfer is the final label of a route (especially in Isolines where no target
     * stop indices are provided).
     * <p>
     * The labels are combined to one label if the second label is a route and the last label is a transfer, the trip of
     * the second label can reach the target stop of the first label and the route time is better than the transfer time
     * to the target stop (either earlier arrival for time type DEPARTURE or later departure for time type ARRIVAL).
     * <p>
     * If the labels are combined, the first two labels are removed and the combined label is added to the list of
     * labels.
     *
     * @param labels the list of labels to check for combination.
     */
    private void maybeCombineFirstTwoLabels(ArrayList<QueryState.Label> labels) {
        maybeCombineLabels(labels, true);
    }

    /**
     * Check if last two labels can be combined to one label to improve the target time. This is because by default the
     * raptor algorithm will relax footpaths from the source stop at the earliest possible time (i.e. the given arrival
     * time or departure time), however, if the transfer reaches a nearby stop and the second leg (second last label) is
     * a route trip that could have also been entered at the source stop, it is possible that the overall travel time
     * can be reduced by combining the two labels. The earliest arrival time or latest departure time is not changed by
     * this operation, but the travel time is reduced. If the labels cannot be combined because the route trip does not
     * pass through the source stop, the transfer label is shifted in time that there is no idleTime between the
     * transfer and the route trip (see maybeShiftSourceTransferCloserToFirstRoute).
     * <p>
     * Example: if the departure time is set to 5 am at Stop A and a connection to stop C is queried, the algorithm will
     * relax footpaths from Stop A at 5 am and reach Stop B at 5:05 am. However, the earliest trip on the route
     * traveling from Stop A - B - C leaves at 9:00 am and arrives at C at 9:07. As a consequence, the arrival time for
     * the connection Transfer A (5:00 am) - B (5:05 am) - Route B (9:03 am) - C (9:07 am) is 9:07 am and the connection
     * Route A (9:00 am) - B (9:03 am) - C (9:07 am) is 9:07 am will be identical. However, the latter connection will
     * have travel time of 7 minutes, whereas the former connection will have a travel time of 3 hours and 7 minutes and
     * is therefore less convenient.
     *
     * @param labels the list of labels to check for combination.
     */
    private void maybeCombineLastTwoLabels(ArrayList<QueryState.Label> labels) {
        maybeCombineLabels(labels, false);
    }

    /**
     * Implementation for the two method above (maybeCombineFirstTwoLabels and maybeCombineLastTwoLabels). For more info
     * see the documentation of the two methods.
     *
     * @param labels     the list of labels to check for combination.
     * @param fromTarget if true, the first two labels are checked, if false, the last two labels (first two legs of
     *                   connection) are checked. Note the first two labels are the two labels closest to the target!
     */
    private void maybeCombineLabels(ArrayList<QueryState.Label> labels, boolean fromTarget) {
        if (labels.size() < 2) {
            return;
        }

        // define the indices of the labels to check (first two or last two)
        int transferLabelIndex = fromTarget ? 0 : labels.size() - 1;
        int routeLabelIndex = fromTarget ? 1 : labels.size() - 2;

        QueryState.Label transferLabel = labels.get(transferLabelIndex);
        QueryState.Label routeLabel = labels.get(routeLabelIndex);

        // check if the labels are of the correct type else they cannot be combined
        if (transferLabel.type() != QueryState.LabelType.TRANSFER || routeLabel.type() != QueryState.LabelType.ROUTE) {
            return;
        }

        int stopIdx;
        if (fromTarget) {
            stopIdx = transferLabel.stopIdx();
        } else {
            assert transferLabel.previous() != null;
            stopIdx = transferLabel.previous().stopIdx();
        }

        StopTime stopTime = getTripStopTimeForStopInTrip(stopIdx, routeLabel.routeOrTransferIdx(),
                routeLabel.tripOffset());

        // if stopTime is null, then the stop is not part of the trip of the route label, if stop time is not null, then
        // check if the temporal order of the stop time and the route label is correct (e.g. for time type departure the
        // stop time departure must be before the route label target time)
        if (stopTime == null || (fromTarget ? !canStopTimeBeTarget(stopTime, routeLabel, transferLabel,
                timeType) : !canStopTimeBeSource(stopTime, routeLabel, transferLabel, timeType))) {
            if (!fromTarget) {
                maybeShiftSourceTransferCloserToFirstRoute(labels, transferLabel, routeLabel, transferLabelIndex);
            }
            return;
        }

        boolean isDeparture = timeType == TimeType.DEPARTURE;
        int timeDirection = isDeparture ? 1 : -1;
        int routeTime = fromTarget ? (isDeparture ? stopTime.arrival() : stopTime.departure()) : (isDeparture ? stopTime.departure() : stopTime.arrival());

        // this is the best time achieved with the route / transfer combination
        int referenceTime = fromTarget ? timeDirection * transferLabel.targetTime() : timeDirection * transferLabel.sourceTime();

        // if the best time is not improved, then the labels should not be combined
        if (fromTarget ? (timeDirection * routeTime > referenceTime) : (timeDirection * routeTime < referenceTime)) {
            return;
        }

        // combine and replace labels
        if (fromTarget) {
            QueryState.Label combinedLabel = new QueryState.Label(routeLabel.sourceTime(), routeTime,
                    QueryState.LabelType.ROUTE, routeLabel.routeOrTransferIdx(), routeLabel.tripOffset(),
                    transferLabel.stopIdx(), routeLabel.previous());
            labels.removeFirst();
            labels.removeFirst();
            labels.addFirst(combinedLabel);
        } else {
            QueryState.Label combinedLabel = new QueryState.Label(routeTime, routeLabel.targetTime(),
                    QueryState.LabelType.ROUTE, routeLabel.routeOrTransferIdx(), routeLabel.tripOffset(),
                    routeLabel.stopIdx(), transferLabel.previous());
            labels.removeLast();
            labels.removeLast();
            labels.addLast(combinedLabel);
        }
    }

    /**
     * This method checks if there is idle time between the source transfer (note this method expects that is only
     * applied on source labels of type transfer) and the first route label. If there is idle time, i.e. the transfer
     * arrives/leaves not directly before/after the route departs/arrives, then the transfer label can be shifted to the
     * route label (shortening the travel time).
     *
     * @param labels             the list of all labels for the connection
     * @param transferLabel      the source transfer label
     * @param routeLabel         the following route label
     * @param transferLabelIndex the index of the transfer label in the list of labels (either last or first)
     */
    private void maybeShiftSourceTransferCloserToFirstRoute(ArrayList<QueryState.Label> labels,
                                                            QueryState.Label transferLabel, QueryState.Label routeLabel,
                                                            int transferLabelIndex) {
        // if there is idle time (a gap between the initial or final transfer and route) then the transfer label can
        // be shifted to the route label (shortening the travel time)
        int idleTime = routeLabel.sourceTime() - transferLabel.targetTime();
        if (idleTime != 0) {
            labels.set(transferLabelIndex,
                    new QueryState.Label(transferLabel.sourceTime() + idleTime, transferLabel.targetTime() + idleTime,
                            QueryState.LabelType.TRANSFER, transferLabel.routeOrTransferIdx(),
                            transferLabel.tripOffset(), transferLabel.stopIdx(), transferLabel.previous()));
        }
    }

    /**
     * Check if the stop time can be the source of the route target time. This is the case if the stop time departure is
     * before the route target time for departure time type and the stop time arrival is after the route target time for
     * arrival time type.
     *
     * @param stopTime      the stop time to check.
     * @param routeLabel    the second label of the connection (a route leg)
     * @param transferLabel the first label of the connection which may be replaced by the route (a transfer leg)
     * @param timeType      the time type (arrival or departure).
     * @return true if the stop time can be the source of the route target time, false otherwise.
     */
    private boolean canStopTimeBeSource(StopTime stopTime, QueryState.Label routeLabel, QueryState.Label transferLabel,
                                        TimeType timeType) {
        if (timeType == TimeType.DEPARTURE && stopTime.departure() <= routeLabel.targetTime() && stopTime.departure() >= transferLabel.sourceTime()) {
            return true;
        } else {
            return timeType == TimeType.ARRIVAL && stopTime.arrival() >= routeLabel.targetTime() && stopTime.arrival() <= transferLabel.sourceTime();
        }
    }

    /**
     * Check if the stop time can be the target of the route source time. This is the case if the stop time arrival is
     * after the route source time for departure time type and the stop time departure is before the route source time
     * for arrival time type.
     *
     * @param stopTime      the stop time to check.
     * @param routeLabel    second last label of the connection done by route
     * @param transferLabel last label of the connection which is a transfer
     * @param timeType      the time type (arrival or departure).
     * @return true if the stop time can be the target of the route source time, false otherwise.
     */
    private boolean canStopTimeBeTarget(StopTime stopTime, QueryState.Label routeLabel, QueryState.Label transferLabel,
                                        TimeType timeType) {
        if (timeType == TimeType.DEPARTURE && stopTime.arrival() >= routeLabel.sourceTime() && stopTime.arrival() <= transferLabel.targetTime()) {
            return true;
        } else {
            return timeType == TimeType.ARRIVAL && stopTime.departure() <= routeLabel.sourceTime() && stopTime.departure() >= transferLabel.targetTime();
        }
    }

    /**
     * Retrieve the stop time adjusted to UTC for a specific stop on a specific trip.
     *
     * @param stopIdx    the index of the stop.
     * @param routeIdx   the index of the route.
     * @param tripOffset the offset of the trip on the route.
     * @return the stop time adjusted to UTC, or null if stop not on trip.
     */
    private @Nullable StopTime getTripStopTimeForStopInTrip(int stopIdx, int routeIdx, int tripOffset) {
        int firstStopTimeIdx = routes[routeIdx].firstStopTimeIdx();
        int numberOfStops = routes[routeIdx].numberOfStops();

        int stopOffset = -1;
        for (int i = 0; i < numberOfStops; i++) {
            if (routeStops[routes[routeIdx].firstRouteStopIdx() + i].stopIndex() == stopIdx) {
                stopOffset = i;
                break;
            }
        }

        if (stopOffset == -1) {
            return null;
        }

        int stopTimeIndex = firstStopTimeIdx + 2 * (tripOffset * numberOfStops + stopOffset) + 2;

        // apply UTC offset to the raw local time from the array
        Route route = routes[routeIdx];
        int utcOffset = DateTimeConverter.getLocalToUtcOffset(referenceDate, route.zoneId());

        return new StopTime(stopTimes[stopTimeIndex] + utcOffset, stopTimes[stopTimeIndex + 1] + utcOffset);
    }

    /**
     * Loops through labels in reverse order to find the earliest/latest occurrence for a stop.
     *
     * @param bestLabelsPerRound the list of labels per round.
     * @param stopIdx            the stop index.
     * @return the best label for the stop, or null if never reached.
     */
    private QueryState.@Nullable Label getBestLabelForStop(List<QueryState.Label[]> bestLabelsPerRound, int stopIdx) {
        // loop through the list in reverse order since the first occurrence will be the best target time
        for (int i = bestLabelsPerRound.size() - 1; i >= 0; i--) {
            QueryState.Label label = bestLabelsPerRound.get(i)[stopIdx];
            if (label != null) {
                return label;
            }
        }

        return null;
    }

    /**
     * Internal record to transport intermediate leg data before final construction.
     */
    private record LegContext(String routeId, @Nullable String tripId, String fromStopId, String toStopId,
                              int departureTimestamp, int arrivalTimestamp, Leg.Type type) {
    }
}