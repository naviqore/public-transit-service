package ch.naviqore.raptor.impl;

import ch.naviqore.raptor.Connection;
import ch.naviqore.raptor.QueryConfig;
import ch.naviqore.raptor.RaptorAlgorithm;
import ch.naviqore.raptor.TimeType;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Raptor algorithm implementation
 */
@Log4j2
class Raptor implements RaptorAlgorithm {

    @Getter(AccessLevel.PACKAGE)
    private final Lookup lookup;

    @Getter(AccessLevel.PACKAGE)
    private final StopContext stopContext;

    @Getter(AccessLevel.PACKAGE)
    private final RouteTraversal routeTraversal;

    private final InputValidator validator;

    Raptor(Lookup lookup, StopContext stopContext, RouteTraversal routeTraversal) {
        this.lookup = lookup;
        this.stopContext = stopContext;
        this.routeTraversal = routeTraversal;
        validator = new InputValidator(lookup.stops());
    }

    private static Map<String, Integer> mapLocalDateTimeToUnixTimestamp(Map<String, LocalDateTime> sourceStops) {
        return sourceStops.entrySet()
                .stream()
                .map(e -> Map.entry(e.getKey(), (int) e.getValue().toEpochSecond(ZoneOffset.UTC)))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Override
    public List<Connection> routeEarliestArrival(Map<String, LocalDateTime> departureStops,
                                                 Map<String, Integer> arrivalStops, QueryConfig config) {
        InputValidator.checkNonNullStops(departureStops, "Departure");
        InputValidator.checkNonNullStops(arrivalStops, "Arrival");

        log.info("Routing earliest arrival from {} to {} departing at {}", departureStops.keySet(),
                arrivalStops.keySet(), departureStops.values().stream().toList());

        return getConnections(departureStops, arrivalStops, TimeType.DEPARTURE, config);
    }

    @Override
    public List<Connection> routeLatestDeparture(Map<String, Integer> departureStops,
                                                 Map<String, LocalDateTime> arrivalStops, QueryConfig config) {
        InputValidator.checkNonNullStops(departureStops, "Departure");
        InputValidator.checkNonNullStops(arrivalStops, "Arrival");

        log.info("Routing latest departure from {} to {} arriving at {}", departureStops.keySet(),
                arrivalStops.keySet(), arrivalStops.values().stream().toList());

        return getConnections(arrivalStops, departureStops, TimeType.ARRIVAL, config);
    }

    @Override
    public Map<String, Connection> routeIsolines(Map<String, LocalDateTime> sourceStops, TimeType timeType,
                                                 QueryConfig config) {
        InputValidator.checkNonNullStops(sourceStops, "Source");

        log.info("Routing isolines from {} with {}", sourceStops.keySet(), timeType);
        Map<Integer, Integer> validatedSourceStopIdx = validator.validateStopsAndGetIndices(
                mapLocalDateTimeToUnixTimestamp(sourceStops));

        int[] sourceStopIndices = validatedSourceStopIdx.keySet().stream().mapToInt(Integer::intValue).toArray();
        int[] refStopTimes = validatedSourceStopIdx.values().stream().mapToInt(Integer::intValue).toArray();
        List<Objective.Label[]> bestLabelsPerRound = spawnFromStop(sourceStopIndices, new int[]{}, refStopTimes,
                new int[]{}, config, timeType);

        return new LabelPostprocessor(this, timeType).reconstructIsolines(bestLabelsPerRound);
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
        Map<String, Integer> sourceStopsSecondsOfDay = mapLocalDateTimeToUnixTimestamp(sourceStops);
        Map<Integer, Integer> validatedSourceStops = validator.validateStopsAndGetIndices(sourceStopsSecondsOfDay);
        Map<Integer, Integer> validatedTargetStops = validator.validateStopsAndGetIndices(targetStops);
        InputValidator.validateStopPermutations(sourceStopsSecondsOfDay, targetStops);

        int[] sourceStopIndices = validatedSourceStops.keySet().stream().mapToInt(Integer::intValue).toArray();
        int[] sourceTimes = validatedSourceStops.values().stream().mapToInt(Integer::intValue).toArray();
        int[] targetStopIndices = validatedTargetStops.keySet().stream().mapToInt(Integer::intValue).toArray();
        int[] walkingDurationsToTarget = validatedTargetStops.values().stream().mapToInt(Integer::intValue).toArray();

        List<Objective.Label[]> bestLabelsPerRound = spawnFromStop(sourceStopIndices, targetStopIndices, sourceTimes,
                walkingDurationsToTarget, config, timeType);

        return new LabelPostprocessor(this, timeType).reconstructParetoOptimalSolutions(bestLabelsPerRound,
                validatedTargetStops);
    }

    // if targetStopIdx is not empty, then the search will stop when target stop cannot be pareto optimized
    private List<Objective.Label[]> spawnFromStop(int[] sourceStopIndices, int[] targetStopIndices, int[] sourceTimes,
                                                  int[] walkingDurationsToTarget, QueryConfig config,
                                                  TimeType timeType) {
        // set up new query objective, footpath relaxer and route scanner
        Objective objective = new Objective(stopContext.stops().length, sourceStopIndices, targetStopIndices,
                sourceTimes, walkingDurationsToTarget, config, timeType);
        FootpathRelaxer footpathRelaxer = new FootpathRelaxer(this, objective);
        RouteScanner routeScanner = new RouteScanner(this, objective);

        // initially relax all source stops and add the newly improved stops by relaxation to the marked stops
        Set<Integer> markedStops = objective.initialize();
        markedStops.addAll(footpathRelaxer.relaxInitial(sourceStopIndices));
        markedStops = objective.removeSubOptimalLabelsForRound(0, markedStops);

        // continue with further rounds as long as there are new marked stops
        int round = 1;
        while (!markedStops.isEmpty() && (round - 1) <= config.getMaximumTransferNumber()) {
            // add label layer for new round
            objective.addNewRound();

            // scan all routs and mark stops that have improved
            Set<Integer> markedStopsNext = routeScanner.scan(round, markedStops);

            // relax footpaths for all newly marked stops
            markedStopsNext.addAll(footpathRelaxer.relax(round, markedStopsNext));

            // prepare next round
            markedStops = objective.removeSubOptimalLabelsForRound(round, markedStopsNext);
            round++;
        }

        return objective.getBestLabelsPerRound();
    }

    /**
     * Validate inputs to raptor.
     */
    @RequiredArgsConstructor
    private static class InputValidator {
        private static final int MIN_SOURCE_STOP_TIMESTAMP = 0;
        private static final int MIN_WALKING_TIME_TO_TARGET = 0;

        private final Map<String, Integer> stopsToIdx;

        private static void checkNonNullStops(Map<String, ?> stops, String labelSource) {
            if (stops == null) {
                throw new IllegalArgumentException(String.format("%s stops must not be null.", labelSource));
            }
        }

        private static void validateStopPermutations(Map<String, Integer> sourceStops,
                                                     Map<String, Integer> targetStops) {
            sourceStops.values().forEach(InputValidator::validateSourceStopTimestamps);
            targetStops.values().forEach(InputValidator::validateWalkingTimeToTarget);

            // ensure departure and arrival stops are not the same
            Set<String> intersection = sourceStops.keySet();
            intersection.retainAll(targetStops.keySet());
            if (!intersection.isEmpty()) {
                throw new IllegalArgumentException("Source and target stop IDs must not be the same.");
            }
        }

        private static void validateSourceStopTimestamps(int timestamp) {
            if (timestamp < MIN_SOURCE_STOP_TIMESTAMP) {
                throw new IllegalArgumentException(
                        "Source stop timestamp must be greater or equal to " + MIN_SOURCE_STOP_TIMESTAMP + " seconds.");
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
                    validateSourceStopTimestamps(time);
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
