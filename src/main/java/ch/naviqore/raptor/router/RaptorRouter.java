package ch.naviqore.raptor.router;

import ch.naviqore.raptor.Connection;
import ch.naviqore.raptor.QueryConfig;
import ch.naviqore.raptor.RaptorAlgorithm;
import ch.naviqore.raptor.TimeType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Raptor algorithm implementation
 */
@Slf4j
class RaptorRouter implements RaptorAlgorithm, RaptorData {

    @Getter
    private final Lookup lookup;

    @Getter
    private final StopContext stopContext;

    @Getter
    private final RouteTraversal routeTraversal;

    @Getter
    private final RaptorTripMaskProvider raptorTripMaskProvider = null;

    private final InputValidator validator;

    RaptorRouter(Lookup lookup, StopContext stopContext, RouteTraversal routeTraversal) {
        this.lookup = lookup;
        this.stopContext = stopContext;
        this.routeTraversal = routeTraversal;
        validator = new InputValidator(lookup.stops());
    }

    @Override
    public List<Connection> routeEarliestArrival(Map<String, LocalDateTime> departureStops,
                                                 Map<String, Integer> arrivalStops, QueryConfig config) {
        InputValidator.checkNonNullOrEmptyStops(departureStops, "Departure");
        InputValidator.checkNonNullOrEmptyStops(arrivalStops, "Arrival");

        log.info("Routing earliest arrival from {} to {} departing at {}", departureStops.keySet(),
                arrivalStops.keySet(), departureStops.values().stream().toList());

        return getConnections(departureStops, arrivalStops, TimeType.DEPARTURE, config);
    }

    @Override
    public List<Connection> routeLatestDeparture(Map<String, Integer> departureStops,
                                                 Map<String, LocalDateTime> arrivalStops, QueryConfig config) {
        InputValidator.checkNonNullOrEmptyStops(departureStops, "Departure");
        InputValidator.checkNonNullOrEmptyStops(arrivalStops, "Arrival");

        log.info("Routing latest departure from {} to {} arriving at {}", departureStops.keySet(),
                arrivalStops.keySet(), arrivalStops.values().stream().toList());

        return getConnections(arrivalStops, departureStops, TimeType.ARRIVAL, config);
    }

    @Override
    public Map<String, Connection> routeIsolines(Map<String, LocalDateTime> sourceStops, TimeType timeType,
                                                 QueryConfig config) {
        InputValidator.checkNonNullOrEmptyStops(sourceStops, "Source");
        InputValidator.validateSourceStopTimes(sourceStops);

        log.info("Routing isolines from {} with {}", sourceStops.keySet(), timeType);
        LocalDate referenceDate = DateTimeUtils.getReferenceDate(sourceStops, timeType);
        Map<Integer, Integer> validatedSourceStopIdx = validator.validateStopsAndGetIndices(
                DateTimeUtils.mapLocalDateTimeToTimestamp(sourceStops, referenceDate));

        int[] sourceStopIndices = validatedSourceStopIdx.keySet().stream().mapToInt(Integer::intValue).toArray();
        int[] refStopTimes = validatedSourceStopIdx.values().stream().mapToInt(Integer::intValue).toArray();
        List<StopLabelsAndTimes.Label[]> bestLabelsPerRound = new Query(this, sourceStopIndices, new int[]{},
                refStopTimes, new int[]{}, config, timeType).run();

        return new LabelPostprocessor(this, timeType).reconstructIsolines(bestLabelsPerRound, referenceDate);
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
        InputValidator.validateSourceStopTimes(sourceStops);
        LocalDate referenceDate = DateTimeUtils.getReferenceDate(sourceStops, timeType);
        Map<String, Integer> sourceStopsSecondsOfDay = DateTimeUtils.mapLocalDateTimeToTimestamp(sourceStops,
                referenceDate);
        Map<Integer, Integer> validatedSourceStops = validator.validateStopsAndGetIndices(sourceStopsSecondsOfDay);
        Map<Integer, Integer> validatedTargetStops = validator.validateStopsAndGetIndices(targetStops);
        InputValidator.validateStopPermutations(sourceStopsSecondsOfDay, targetStops);

        int[] sourceStopIndices = validatedSourceStops.keySet().stream().mapToInt(Integer::intValue).toArray();
        int[] sourceTimes = validatedSourceStops.values().stream().mapToInt(Integer::intValue).toArray();
        int[] targetStopIndices = validatedTargetStops.keySet().stream().mapToInt(Integer::intValue).toArray();
        int[] walkingDurationsToTarget = validatedTargetStops.values().stream().mapToInt(Integer::intValue).toArray();

        List<StopLabelsAndTimes.Label[]> bestLabelsPerRound = new Query(this, sourceStopIndices, targetStopIndices,
                sourceTimes, walkingDurationsToTarget, config, timeType).run();

        return new LabelPostprocessor(this, timeType).reconstructParetoOptimalSolutions(bestLabelsPerRound,
                validatedTargetStops, referenceDate);
    }

    /**
     * Validate inputs to raptor.
     */
    @RequiredArgsConstructor
    private static class InputValidator {
        private static final int MIN_WALKING_TIME_TO_TARGET = 0;
        private static final int MAX_DIFFERENCE_IN_SOURCE_STOP_TIMES = 24 * 60 * 60;

        private final Map<String, Integer> stopsToIdx;

        private static void checkNonNullOrEmptyStops(Map<String, ?> stops, String labelSource) {
            if (stops == null) {
                throw new IllegalArgumentException(String.format("%s stops must not be null.", labelSource));
            }
            if (stops.isEmpty()) {
                throw new IllegalArgumentException(String.format("%s stops must not be empty.", labelSource));
            }
        }

        private static void validateSourceStopTimes(Map<String, LocalDateTime> sourceStops) {
            // check that no null values are present
            if (sourceStops.values().stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("Source stop times must not be null.");
            }

            // get min and max values
            LocalDateTime min = sourceStops.values().stream().min(LocalDateTime::compareTo).orElseThrow();
            LocalDateTime max = sourceStops.values().stream().max(LocalDateTime::compareTo).orElseThrow();
            if (Duration.between(min, max).getSeconds() > MAX_DIFFERENCE_IN_SOURCE_STOP_TIMES) {
                throw new IllegalArgumentException("Difference between source stop times must be less than 24 hours.");
            }
        }

        private static void validateStopPermutations(Map<String, Integer> sourceStops,
                                                     Map<String, Integer> targetStops) {
            targetStops.values().forEach(InputValidator::validateWalkingTimeToTarget);

            // ensure departure and arrival stops are not the same
            if (!Collections.disjoint(sourceStops.keySet(), targetStops.keySet())) {
                throw new IllegalArgumentException("Source and target stop IDs must not be the same.");
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
