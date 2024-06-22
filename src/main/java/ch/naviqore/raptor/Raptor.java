package ch.naviqore.raptor;

import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

// TODO Raptor is responsible for request and return result to client - pre- and post-processing
// TODO FootPathRelaxer is responsible for relaxing footpaths

/**
 * Raptor algorithm implementation
 */
@Log4j2
public class Raptor {

    public final static int INFINITY = Integer.MAX_VALUE;
    public final static int NO_INDEX = -1;
    private final InputValidator validator = new InputValidator();
    // lookup
    private final Map<String, Integer> stopsToIdx;
    // stop context
    private final Transfer[] transfers;
    private final Stop[] stops;
    private final int[] stopRoutes;
    // route traversal
    private final StopTime[] stopTimes;
    private final Route[] routes;
    private final RouteStop[] routeStops;
    // TODO: Store data structures, remove extracted above when everything is refactored
    private final Lookup lookup;
    private final StopContext stopContext;
    private final RouteTraversal routeTraversal;

    Raptor(Lookup lookup, StopContext stopContext, RouteTraversal routeTraversal) {
        this.lookup = lookup;
        this.stopContext = stopContext;
        this.routeTraversal = routeTraversal;
        // TODO: Try to avoid extraction of data structure here if not needed.
        this.stopsToIdx = lookup.stops();
        this.transfers = stopContext.transfers();
        this.stops = stopContext.stops();
        this.stopRoutes = stopContext.stopRoutes();
        this.stopTimes = routeTraversal.stopTimes();
        this.routes = routeTraversal.routes();
        this.routeStops = routeTraversal.routeStops();
    }

    public static RaptorBuilder builder(int sameStopTransferTime) {
        return new RaptorBuilder(sameStopTransferTime);
    }

    private static int getBestTimeForStop(int stopIdx, List<Label[]> bestLabelsPerRound, TimeType timeType) {
        int timeFactor = timeType == TimeType.DEPARTURE ? 1 : -1;
        int bestTime = timeFactor * INFINITY;
        for (Label[] labels : bestLabelsPerRound) {
            if (labels[stopIdx] == null) {
                continue;
            }
            Label currentLabel = labels[stopIdx];
            if (timeType == TimeType.DEPARTURE) {
                if (currentLabel.targetTime < bestTime) {
                    bestTime = currentLabel.targetTime;
                }
            } else {
                if (currentLabel.targetTime > bestTime) {
                    bestTime = currentLabel.targetTime;
                }
            }
        }

        return bestTime;
    }

    private static @NotNull Map<String, Integer> mapLocalDateToSecondsOfDay(Map<String, LocalDateTime> sourceStops) {
        return sourceStops.entrySet()
                .stream()
                .map(e -> Map.entry(e.getKey(), e.getValue().toLocalTime().toSecondOfDay()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    }

    /**
     * The cut-off time is the latest allowed arrival / the earliest allowed departure time, if a stop is reached
     * after/before (depending on timeType), the stop is no longer considered for further expansion.
     *
     * @param sourceTimes the source times to calculate the cut-off time from.
     * @param config      the query configuration.
     * @param timeType    the time type to calculate the cut-off time for.
     * @return the cut-off time.
     */
    private static int getCutOffTime(int[] sourceTimes, QueryConfig config, TimeType timeType) {
        int cutOffTime;
        if (config.getMaximumTravelTime() == INFINITY) {
            cutOffTime = timeType == TimeType.DEPARTURE ? INFINITY : -INFINITY;
        } else if (timeType == TimeType.DEPARTURE) {
            int earliestDeparture = Arrays.stream(sourceTimes).min().orElseThrow();
            cutOffTime = earliestDeparture + config.getMaximumTravelTime();
        } else {
            int latestArrival = Arrays.stream(sourceTimes).max().orElseThrow();
            cutOffTime = latestArrival - config.getMaximumTravelTime();
        }
        return cutOffTime;
    }

    /**
     * Routing the earliest arrival from departure stops to arrival. Given a set departure time.
     *
     * @param departureStops Map of stop ids and departure times
     * @param arrivalStops   Map of stop ids and walking times to final destination
     * @param config         Query configuration
     * @return a list of pareto-optimal earliest arrival connections
     */
    public List<Connection> routeEarliestArrival(Map<String, LocalDateTime> departureStops,
                                                 Map<String, Integer> arrivalStops, QueryConfig config) {
        checkNonNullStops(departureStops, "Departure");
        checkNonNullStops(arrivalStops, "Arrival");
        log.info("Routing earliest arrival from {} to {} departing at {}", departureStops.keySet(),
                arrivalStops.keySet(), departureStops.values().stream().toList());
        return getConnections(departureStops, arrivalStops, TimeType.DEPARTURE, config);
    }

    /**
     * Routing the latest departure from departure stops to arrival. Given a set arrival time.
     *
     * @param departureStops Map of stop ids and walking times from origin
     * @param arrivalStops   Map of stop ids and arrival times
     * @param config         Query configuration
     * @return a list of pareto-optimal latest departure connections
     */
    public List<Connection> routeLatestDeparture(Map<String, Integer> departureStops,
                                                 Map<String, LocalDateTime> arrivalStops, QueryConfig config) {
        checkNonNullStops(departureStops, "Departure");
        checkNonNullStops(arrivalStops, "Arrival");
        log.info("Routing latest departure from {} to {} arriving at {}", departureStops.keySet(),
                arrivalStops.keySet(), arrivalStops.values().stream().toList());

        return getConnections(arrivalStops, departureStops, TimeType.ARRIVAL, config);
    }

    /**
     * Route isolines from source stops. Given a set of departure or arrival times, the method will return the earliest
     * arrival or latest departure connections for each stop.
     *
     * @param sourceStops is a map of stop ids and departure/arrival times
     * @param timeType    is the type of time to route for (arrival or departure)
     * @param config      is the query configuration
     * @return a pareto-optimal connection for each stop
     */
    public Map<String, Connection> routeIsolines(Map<String, LocalDateTime> sourceStops, TimeType timeType,
                                                 QueryConfig config) {
        checkNonNullStops(sourceStops, "Source");
        Map<Integer, Integer> validatedSourceStopIdx = validator.validateStopsAndGetIndices(
                mapLocalDateToSecondsOfDay(sourceStops));
        int[] sourceStopIndices = validatedSourceStopIdx.keySet().stream().mapToInt(Integer::intValue).toArray();
        int[] refStopTimes = validatedSourceStopIdx.values().stream().mapToInt(Integer::intValue).toArray();

        List<Label[]> bestLabelsPerRound = spawnFromStop(sourceStopIndices, new int[]{}, refStopTimes, new int[]{},
                config, timeType);

        Map<String, Connection> isolines = new HashMap<>();
        for (int i = 0; i < stops.length; i++) {
            Stop stop = stops[i];
            Label bestLabelForStop = null;

            // search best label for stop in all rounds
            for (Label[] labels : bestLabelsPerRound) {
                if (labels[i] != null) {
                    if (bestLabelForStop == null) {
                        bestLabelForStop = labels[i];
                    } else if (labels[i].targetTime < bestLabelForStop.targetTime) {
                        bestLabelForStop = labels[i];
                    }
                }
            }

            if (bestLabelForStop != null && bestLabelForStop.type != LabelType.INITIAL) {
                Connection connection = reconstructConnectionFromLabel(bestLabelForStop, timeType);
                isolines.put(stop.id(), connection);
            }
        }

        return isolines;
    }

    private void checkNonNullStops(Map<String, ?> stops, String labelSource) {
        if (stops == null) {
            throw new IllegalArgumentException(String.format("%s stops must not be null.", labelSource));
        }
    }

    /**
     * This is the main method to route from source to target stops. The method will spawn from the source stops and
     * expand footpaths and routes until the target stops are reached. The method will return the pareto-optimal
     * connections.
     * <p>
     * Note, in case the time type is arrival, the source stop is the arrival stop (last stop of the connection) and the
     * route is calculated backwards in time, searching for the latest possible departure at the departure stop (target
     * stops).
     *
     * @param sourceStops is a map of stop ids and departure/arrival times depending on the time type
     * @param targetStops is a map of stop ids and walking durations to target stops
     * @param timeType    is the type of time to route for (arrival or departure)
     * @param config      is the query configuration
     * @return a list of pareto-optimal connections
     */
    private List<Connection> getConnections(Map<String, LocalDateTime> sourceStops, Map<String, Integer> targetStops,
                                            TimeType timeType, QueryConfig config) {

        Map<String, Integer> sourceStopsSecondsOfDay = mapLocalDateToSecondsOfDay(sourceStops);

        Map<Integer, Integer> validatedSourceStops = validator.validateStopsAndGetIndices(sourceStopsSecondsOfDay);
        Map<Integer, Integer> validatedTargetStops = validator.validateStopsAndGetIndices(targetStops);

        InputValidator.validateStopPermutations(sourceStopsSecondsOfDay, targetStops);
        int[] sourceStopIndices = validatedSourceStops.keySet().stream().mapToInt(Integer::intValue).toArray();
        int[] sourceTimes = validatedSourceStops.values().stream().mapToInt(Integer::intValue).toArray();
        int[] targetStopIndices = validatedTargetStops.keySet().stream().mapToInt(Integer::intValue).toArray();
        int[] walkingDurationsToTarget = validatedTargetStops.values().stream().mapToInt(Integer::intValue).toArray();

        List<Label[]> earliestArrivalsPerRound = spawnFromStop(sourceStopIndices, targetStopIndices, sourceTimes,
                walkingDurationsToTarget, config, timeType);

        // get pareto-optimal solutions
        return reconstructParetoOptimalSolutions(earliestArrivalsPerRound, validatedTargetStops, timeType);
    }

    // if targetStopIdx is not empty, then the search will stop when target stop cannot be pareto optimized
    private List<Label[]> spawnFromStop(int[] sourceStopIndices, int[] targetStopIndices, int[] sourceTimes,
                                        int[] walkingDurationsToTarget, QueryConfig config, TimeType timeType) {
        // initialization
        final int[] bestTimeForStops = new int[stops.length];
        Arrays.fill(bestTimeForStops, timeType == TimeType.DEPARTURE ? INFINITY : -INFINITY);

        if (sourceStopIndices.length != sourceTimes.length) {
            throw new IllegalArgumentException("Source stops and departure/arrival times must have the same size.");
        }

        if (targetStopIndices.length != walkingDurationsToTarget.length) {
            throw new IllegalArgumentException("Target stops and walking durations to target must have the same size.");
        }

        final int cutOffTime = getCutOffTime(sourceTimes, config, timeType);

        int maxWalkingDuration = config.getMaximumWalkingDuration();
        int minTransferDuration = config.getMinimumTransferDuration();

        int[] targetStops = new int[targetStopIndices.length * 2];
        for (int i = 0; i < targetStops.length; i += 2) {
            int index = (int) Math.ceil(i / 2.0);
            targetStops[i] = targetStopIndices[index];
            targetStops[i + 1] = walkingDurationsToTarget[index];
        }

        final List<Label[]> bestLabelsPerRound = new ArrayList<>();
        bestLabelsPerRound.add(new Label[stops.length]);
        Set<Integer> markedStops = new HashSet<>();

        for (int i = 0; i < sourceStopIndices.length; i++) {
            bestTimeForStops[sourceStopIndices[i]] = sourceTimes[i];
            bestLabelsPerRound.getFirst()[sourceStopIndices[i]] = new Label(0, sourceTimes[i], LabelType.INITIAL,
                    NO_INDEX, NO_INDEX, sourceStopIndices[i], null);
            markedStops.add(sourceStopIndices[i]);
        }

        for (int sourceStopIdx : sourceStopIndices) {
            expandFootpathsFromStop(sourceStopIdx, bestTimeForStops, bestLabelsPerRound, markedStops, 0,
                    maxWalkingDuration, minTransferDuration, timeType);
        }

        int bestTime = getBestTime(targetStops, bestLabelsPerRound, cutOffTime, timeType);
        markedStops = removeSubOptimalLabelsForRound(bestTime, 0, timeType, bestLabelsPerRound, markedStops);

        // initialize route scanner with best times
        RouteScanner routeScanner = new RouteScanner(stopContext, routeTraversal, bestLabelsPerRound, bestTimeForStops,
                minTransferDuration, timeType);

        // continue with further rounds as long as there are new marked stops
        int round = 1;
        while (!markedStops.isEmpty() && (round - 1) <= config.getMaximumTransferNumber()) {
            Set<Integer> markedStopsNext = routeScanner.scan(round, markedStops);

            // relax footpaths for all markedStops
            // temp variable to add any new stops to markedStopsNext
            Set<Integer> newStops = new HashSet<>();
            for (int stopIdx : markedStopsNext) {
                expandFootpathsFromStop(stopIdx, bestTimeForStops, bestLabelsPerRound, newStops, round,
                        maxWalkingDuration, minTransferDuration, timeType);
            }
            markedStopsNext.addAll(newStops);

            // prepare next round
            bestTime = getBestTime(targetStops, bestLabelsPerRound, cutOffTime, timeType);
            markedStops = removeSubOptimalLabelsForRound(bestTime, round, timeType, bestLabelsPerRound,
                    markedStopsNext);
            round++;
        }

        return bestLabelsPerRound;
    }

    /**
     * Nullify labels that are suboptimal for the current round. This method checks if the label time is worse than the
     * optimal time mark and removes the mark for the next round and nullifies the label in this case.
     *
     * @param bestTime           - The best time for the current round.
     * @param round              - The round to remove suboptimal labels for.
     * @param timeType           - The time type to check for.
     * @param bestLabelsPerRound - The best time labels per round.
     * @param markedStops        - The marked stops to check for suboptimal labels.
     */
    private Set<Integer> removeSubOptimalLabelsForRound(int bestTime, int round, TimeType timeType,
                                                        List<Label[]> bestLabelsPerRound, Set<Integer> markedStops) {
        if (bestTime == INFINITY || bestTime == -INFINITY) {
            return markedStops;
        }
        Label[] bestLabelsThisRound = bestLabelsPerRound.get(round);
        Set<Integer> markedStopsClean = new HashSet<>();
        for (int stopIdx : markedStops) {
            if (bestLabelsThisRound[stopIdx] != null) {
                if (timeType == TimeType.DEPARTURE && bestLabelsThisRound[stopIdx].targetTime > bestTime) {
                    bestLabelsThisRound[stopIdx] = null;
                } else if (timeType == TimeType.ARRIVAL && bestLabelsThisRound[stopIdx].targetTime < bestTime) {
                    bestLabelsThisRound[stopIdx] = null;
                } else {
                    markedStopsClean.add(stopIdx);
                }
            }
        }
        return markedStopsClean;
    }

    /**
     * Get the best time for the target stops. The best time is the earliest arrival time for each stop if the time type
     * is departure, and the latest arrival time for each stop if the time type is arrival.
     *
     * @param targetStops               - The target stops to reach.
     * @param bestLabelForStopsPerRound - The collection of all best labels for each stop in each round.
     * @param cutOffValue               - The latest accepted target time.
     */
    private int getBestTime(int[] targetStops, List<Label[]> bestLabelForStopsPerRound, int cutOffValue,
                            TimeType timeType) {
        int bestTime = cutOffValue;
        for (int i = 0; i < targetStops.length; i += 2) {
            int targetStopIdx = targetStops[i];
            int walkDurationToTarget = targetStops[i + 1];
            int bestTimeForStop = getBestTimeForStop(targetStopIdx, bestLabelForStopsPerRound, timeType);

            if (timeType == TimeType.DEPARTURE && bestTimeForStop != INFINITY) {
                bestTimeForStop += walkDurationToTarget;
                bestTime = Math.min(bestTime, bestTimeForStop);
            } else if (timeType == TimeType.ARRIVAL && bestTimeForStop != -INFINITY) {
                bestTimeForStop -= walkDurationToTarget;
                bestTime = Math.max(bestTime, bestTimeForStop);
            }
        }

        return bestTime;
    }

    private List<Connection> reconstructParetoOptimalSolutions(List<Label[]> bestLabelsPerRound,
                                                               Map<Integer, Integer> targetStops, TimeType timeType) {
        final List<Connection> connections = new ArrayList<>();

        // iterate over all rounds
        for (Label[] labels : bestLabelsPerRound) {

            Label label = null;
            int bestTime = timeType == TimeType.DEPARTURE ? INFINITY : -INFINITY;

            for (Map.Entry<Integer, Integer> entry : targetStops.entrySet()) {
                int targetStopIdx = entry.getKey();
                int targetStopWalkingTime = entry.getValue();
                if (labels[targetStopIdx] == null) {
                    continue;
                }
                Label currentLabel = labels[targetStopIdx];

                if (timeType == TimeType.DEPARTURE) {
                    int actualArrivalTime = currentLabel.targetTime + targetStopWalkingTime;
                    if (actualArrivalTime < bestTime) {
                        label = currentLabel;
                        bestTime = actualArrivalTime;
                    }
                } else {
                    int actualDepartureTime = currentLabel.targetTime - targetStopWalkingTime;
                    if (actualDepartureTime > bestTime) {
                        label = currentLabel;
                        bestTime = actualDepartureTime;
                    }
                }
            }

            // target stop not reached in this round
            if (label == null) {
                continue;
            }

            Connection connection = reconstructConnectionFromLabel(label, timeType);
            if (connection != null) {
                connections.add(connection);
            }
        }

        return connections;
    }

    private @Nullable Connection reconstructConnectionFromLabel(Label label, TimeType timeType) {
        Connection connection = new Connection();

        ArrayList<Label> labels = new ArrayList<>();
        while (label.type != LabelType.INITIAL) {
            assert label.previous != null;
            labels.add(label);
            label = label.previous;
        }

        // check if first two labels can be combined (transfer + route) due to the same stop transfer penalty for route
        // to target stop
        maybeCombineFirstTwoLabels(labels, timeType);
        maybeCombineLastTwoLabels(labels, timeType);

        for (Label currentLabel : labels) {
            String routeId;
            String tripId = null;
            assert currentLabel.previous != null;
            String fromStopId;
            String toStopId;
            int departureTime;
            int arrivalTime;
            Connection.LegType type;
            if (timeType == TimeType.DEPARTURE) {
                fromStopId = stops[currentLabel.previous.stopIdx].id();
                toStopId = stops[currentLabel.stopIdx].id();
                departureTime = currentLabel.sourceTime;
                arrivalTime = currentLabel.targetTime;
            } else {
                fromStopId = stops[currentLabel.stopIdx].id();
                toStopId = stops[currentLabel.previous.stopIdx].id();
                departureTime = currentLabel.targetTime;
                arrivalTime = currentLabel.sourceTime;
            }

            if (currentLabel.type == LabelType.ROUTE) {
                Route route = routes[currentLabel.routeOrTransferIdx];
                routeId = route.id();
                tripId = route.tripIds()[currentLabel.tripOffset];
                type = Connection.LegType.ROUTE;

            } else if (currentLabel.type == LabelType.TRANSFER) {
                routeId = String.format("transfer_%s_%s", fromStopId, toStopId);
                type = Connection.LegType.WALK_TRANSFER;
            } else {
                throw new IllegalStateException("Unknown label type");
            }

            connection.addLeg(
                    new Connection.Leg(routeId, tripId, fromStopId, toStopId, departureTime, arrivalTime, type));
        }

        // initialize connection: Reverse order of legs and add connection
        if (!connection.getLegs().isEmpty()) {
            connection.initialize();
            return connection;
        } else {
            return null;
        }
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
     * @param labels   the list of labels to check for combination.
     * @param timeType the type of time to check for (arrival or departure).
     */
    private void maybeCombineFirstTwoLabels(ArrayList<Label> labels, TimeType timeType) {
        maybeCombineLabels(labels, timeType, true);
    }

    /**
     * Check if last two labels can be combined to one label to improve the target time. This is because by default the
     * raptor algorithm will relax footpaths from the source stop at the earliest possible time (i.e. the given arrival
     * time or departure time), however, if the transfer reaches a nearby stop and the second leg (second last label) is
     * a route trip that could have also been entered at the source stop, it is possible that the overall travel time
     * can be reduced by combining the two labels. The earliest arrival time or latest departure time is not changed by
     * this operation, but the travel time is reduced.
     * <p>
     * Example: if the departure time is set to 5 am at Stop A and a connection to stop C is queried, the algorithm will
     * relax footpaths from Stop A at 5 am and reach Stop B at 5:05 am. However, the earliest trip on the route
     * travelling from Stop A - B - C leaves at 9:00 am and arrives at C at 9:07. As a consequence, the arrival time for
     * the connection Transfer A (5:00 am) - B (5:05 am) - Route B (9:03 am) - C (9:07 am) is 9:07 am and the connection
     * Route A (9:00 am) - B (9:03 am) - C (9:07 am) is 9:07 am will be identical. However, the latter connection will
     * have travel time of 7 minutes, whereas the former connection will have a travel time of 3 hours and 7 minutes and
     * is therefore less convenient.
     *
     * @param labels   the list of labels to check for combination.
     * @param timeType the type of time to check for (arrival or departure).
     */
    private void maybeCombineLastTwoLabels(ArrayList<Label> labels, TimeType timeType) {
        maybeCombineLabels(labels, timeType, false);
    }

    /**
     * Implementation for the two method above (maybeCombineFirstTwoLabels and maybeCombineLastTwoLabels). For more info
     * see the documentation of the two methods.
     *
     * @param labels    the list of labels to check for combination.
     * @param timeType  the type of time to check for (arrival or departure).
     * @param fromStart if true, the first two labels are checked, if false, the last two labels (first two legs of
     *                  connection) are checked.
     */
    private void maybeCombineLabels(ArrayList<Label> labels, TimeType timeType, boolean fromStart) {
        if (labels.size() < 2) {
            return;
        }

        // define the indices of the labels to check (first two or last two)
        int transferLabelIndex = fromStart ? 0 : labels.size() - 1;
        int routeLabelIndex = fromStart ? 1 : labels.size() - 2;

        Label transferLabel = labels.get(transferLabelIndex);
        Label routeLabel = labels.get(routeLabelIndex);

        // check if the labels are of the correct type else they cannot be combined
        if (transferLabel.type != LabelType.TRANSFER || routeLabel.type != LabelType.ROUTE) {
            return;
        }

        int stopIdx;
        if (fromStart) {
            stopIdx = transferLabel.stopIdx;
        } else {
            assert transferLabel.previous != null;
            stopIdx = transferLabel.previous.stopIdx;
        }

        StopTime stopTime = getTripStopTimeForStopInTrip(stopIdx, routeLabel.routeOrTransferIdx, routeLabel.tripOffset);

        // if stopTime is null, then the stop is not part of the trip of the route label
        if (stopTime == null) {
            return;
        }

        boolean isDeparture = timeType == TimeType.DEPARTURE;
        int timeDirection = isDeparture ? 1 : -1;
        int routeTime = fromStart ? (isDeparture ? stopTime.arrival() : stopTime.departure()) : (isDeparture ? stopTime.departure() : stopTime.arrival());

        // this is the best time achieved with the route / transfer combination
        int referenceTime = fromStart ? timeDirection * transferLabel.targetTime : timeDirection * transferLabel.sourceTime;

        // if the best time is not improved, then the labels should not be combined
        if (fromStart ? (timeDirection * routeTime > referenceTime) : (timeDirection * routeTime < referenceTime)) {
            return;
        }

        // combine and replace labels
        if (fromStart) {
            Label combinedLabel = new Label(routeLabel.sourceTime, routeTime, LabelType.ROUTE,
                    routeLabel.routeOrTransferIdx, routeLabel.tripOffset, transferLabel.stopIdx, routeLabel.previous);
            labels.removeFirst();
            labels.removeFirst();
            labels.addFirst(combinedLabel);
        } else {
            Label combinedLabel = new Label(routeTime, routeLabel.targetTime, LabelType.ROUTE,
                    routeLabel.routeOrTransferIdx, routeLabel.tripOffset, routeLabel.stopIdx, transferLabel.previous);
            labels.removeLast();
            labels.removeLast();
            labels.addLast(combinedLabel);
        }
    }

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
        return stopTimes[firstStopTimeIdx + tripOffset * numberOfStops + stopOffset];
    }

    /**
     * Expands all transfers between stops from a given stop. If a transfer improves the target time at the target stop,
     * then the target stop is marked for the next round. And the improved target time is stored in the bestTimes array
     * and the bestLabelPerRound list (including the new transfer label).
     *
     * @param stopIdx             the index of the stop to expand transfers from.
     * @param bestTimes           an array with the overall best time for each stop, indexed by stop index. Note: The
     *                            best time is reduced by the same stop transfer time for transfers, to make them
     *                            comparable with route arrivals.
     * @param bestLabelsPerRound  a list of arrays with the best label for each stop per round, indexed by round.
     * @param markedStops         a set of stop indices that have been marked for scanning in the next round.
     * @param round               the current round to relax footpaths for.
     * @param maxWalkingDuration  the maximum walking duration to reach the target stop. If the walking duration exceeds
     *                            this value, the target stop is not reached.
     * @param minTransferDuration the minimum transfer duration time, since this is intended as rest period (e.g. coffee
     *                            break) it is added to the walk time.
     * @param timeType            the type of time to check for (arrival or departure), defines if stop is considered as
     *                            arrival or departure stop.
     */
    private void expandFootpathsFromStop(int stopIdx, int[] bestTimes, List<Label[]> bestLabelsPerRound,
                                         Set<Integer> markedStops, int round, int maxWalkingDuration,
                                         int minTransferDuration, TimeType timeType) {
        // if stop has no transfers, then no footpaths can be expanded
        if (stops[stopIdx].numberOfTransfers() == 0) {
            return;
        }
        Stop sourceStop = stops[stopIdx];
        Label previousLabel = bestLabelsPerRound.get(round)[stopIdx];

        // do not relax footpath from stop that was only reached by footpath in the same round
        if (previousLabel == null || previousLabel.type == LabelType.TRANSFER) {
            return;
        }

        int sourceTime = previousLabel.targetTime;
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
            if (comparableTargetTime * timeDirection >= bestTimes[transfer.targetStopIdx()] * timeDirection) {
                continue;
            }

            log.debug("Stop {} was improved by transfer from stop {}", targetStop.id(), sourceStop.id());
            // update best times with comparable target time
            bestTimes[transfer.targetStopIdx()] = comparableTargetTime;
            // add real target time to label
            bestLabelsPerRound.get(round)[transfer.targetStopIdx()] = new Label(sourceTime, targetTime,
                    LabelType.TRANSFER, i, NO_INDEX, transfer.targetStopIdx(), bestLabelsPerRound.get(round)[stopIdx]);
            markedStops.add(transfer.targetStopIdx());
        }
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
                 @Nullable Raptor.Label previous) {
    }

    /**
     * Validate inputs to raptor.
     */
    private class InputValidator {
        private static final int MIN_DEPARTURE_TIME = 0;
        private static final int MAX_DEPARTURE_TIME = 48 * 60 * 60; // 48 hours
        private static final int MIN_WALKING_TIME_TO_TARGET = 0;

        private static void validateStopPermutations(Map<String, Integer> sourceStops,
                                                     Map<String, Integer> targetStops) {
            sourceStops.values().forEach(InputValidator::validateDepartureTime);
            targetStops.values().forEach(InputValidator::validateWalkingTimeToTarget);

            // ensure departure and arrival stops are not the same
            Set<String> intersection = new HashSet<>(sourceStops.keySet());
            intersection.retainAll(targetStops.keySet());
            if (!intersection.isEmpty()) {
                throw new IllegalArgumentException("Source and target stop IDs must not be the same.");
            }

        }

        private static void validateDepartureTime(int departureTime) {
            if (departureTime < MIN_DEPARTURE_TIME || departureTime > MAX_DEPARTURE_TIME) {
                throw new IllegalArgumentException(
                        "Departure time must be between " + MIN_DEPARTURE_TIME + " and " + MAX_DEPARTURE_TIME + " seconds.");
            }
        }

        private static void validateWalkingTimeToTarget(int walkingDurationToTarget) {
            if (walkingDurationToTarget < MIN_WALKING_TIME_TO_TARGET) {
                throw new IllegalArgumentException(
                        "Walking duration to target must be greater or equal to " + MIN_WALKING_TIME_TO_TARGET + "seconds.");
            }
        }

        /**
         * Validate the stops provided in the query. This method will check that the map of stop ids and their
         * corresponding departure / walk to target times are valid. This is done by checking if the map is not empty
         * and then checking each entry if the stop id is present in the lookup. If not it is removed from the query. If
         * no valid stops are found an IllegalArgumentException is thrown.
         *
         * @param stops the stops to validate.
         * @return a map of valid stop IDs and their corresponding departure / walk to target times.
         */
        private Map<Integer, Integer> validateStopsAndGetIndices(Map<String, Integer> stops) {
            if (stops.isEmpty()) {
                throw new IllegalArgumentException("At least one stop ID must be provided.");
            }

            // loop over all stop pairs and check if stop exists in raptor, then validate departure time
            Map<Integer, Integer> validStopIds = new HashMap<>();
            for (Map.Entry<String, Integer> entry : stops.entrySet()) {
                String stopId = entry.getKey();
                int time = entry.getValue();

                if (stopsToIdx.containsKey(stopId)) {
                    validateDepartureTime(time);
                    validStopIds.put(stopsToIdx.get(stopId), time);
                } else {
                    log.warn("Stop ID {} not found in lookup removing from query.", entry.getKey());
                }
            }

            if (validStopIds.isEmpty()) {
                throw new IllegalArgumentException("No valid stops provided.");
            }

            return validStopIds;
        }
    }

}
