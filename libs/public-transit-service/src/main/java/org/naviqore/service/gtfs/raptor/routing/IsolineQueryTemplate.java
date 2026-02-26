package org.naviqore.service.gtfs.raptor.routing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.naviqore.raptor.RaptorAlgorithm;
import org.naviqore.service.Connection;
import org.naviqore.service.Stop;
import org.naviqore.service.TimeType;
import org.naviqore.service.config.ConnectionQueryConfig;
import org.naviqore.service.exception.ConnectionRoutingException;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import static org.naviqore.service.TimeType.ARRIVAL;
import static org.naviqore.service.TimeType.DEPARTURE;

/**
 * Template for executing an isoline query from a source location, encapsulating common logic and providing entry points
 * for customization.
 *
 * @param <T> Type of the source location.
 */
@Slf4j
@RequiredArgsConstructor
abstract class IsolineQueryTemplate<T> {

    protected final OffsetDateTime time;
    protected final TimeType timeType;
    protected final ConnectionQueryConfig queryConfig;
    protected final RoutingQueryUtils utils;

    private final T source;

    private final boolean allowSourceTransfers;

    protected abstract Map<String, OffsetDateTime> prepareSourceStops(T source);

    protected abstract Map<Stop, Connection> handleInvalidStopException(RaptorAlgorithm.InvalidStopException exception,
                                                                        T source) throws ConnectionRoutingException;

    protected abstract Connection postprocessDepartureConnection(T source, org.naviqore.raptor.Connection connection);

    protected abstract Connection postprocessArrivalConnection(T source, org.naviqore.raptor.Connection connection);

    Map<Stop, Connection> run() throws ConnectionRoutingException {
        Map<String, OffsetDateTime> sourceStops = prepareSourceStops(source);

        // no source stop is within walkable distance, and therefore no isolines are available
        if (sourceStops.isEmpty()) {
            return Map.of();
        }

        Map<String, org.naviqore.raptor.Connection> isolines;

        try {
            if (queryConfig.getTimeWindowDuration() > 0) {
                isolines = runForShortestDurationTime(sourceStops);
            } else {
                isolines = runForEarliestArrivalTime(sourceStops);
            }
        } catch (RaptorAlgorithm.InvalidStopException e) {
            log.debug("{}: {}", e.getClass().getSimpleName(), e.getMessage());
            return handleInvalidStopException(e, source);
        } catch (IllegalArgumentException e) {
            throw new ConnectionRoutingException(e);
        }

        // convert to service connections and collect results in a list to avoid intermediate hashing overhead
        List<Map.Entry<Stop, Connection>> results = new ArrayList<>(isolines.size());
        for (Map.Entry<String, org.naviqore.raptor.Connection> entry : isolines.entrySet()) {
            org.naviqore.raptor.Connection raptorConnection = entry.getValue();
            Stop stop = utils.getStopById(entry.getKey());

            Connection serviceConnection = switch (timeType) {
                case ARRIVAL -> postprocessArrivalConnection(source, raptorConnection);
                case DEPARTURE -> postprocessDepartureConnection(source, raptorConnection);
            };

            if (utils.isBelowMaximumTravelTime(serviceConnection, queryConfig)) {
                results.add(Map.entry(stop, serviceConnection));
            }
        }

        // sort connections based on time type
        Comparator<Map.Entry<Stop, Connection>> comparator = switch (timeType) {
            // by arrival time ASC; earlier arrival at destination = closer
            case DEPARTURE -> Comparator.comparing(e -> e.getValue().getArrivalTime());
            // by departure time DESC; later departure from origin = closer
            case ARRIVAL -> Comparator.comparing(e -> e.getValue().getDepartureTime(), Comparator.reverseOrder());
        };
        // use stop name as tie-breaker for deterministic results
        results.sort(comparator.thenComparing(e -> e.getKey().getName()));

        // assemble isoline result
        Map<Stop, Connection> sortedResult = LinkedHashMap.newLinkedHashMap(results.size());
        for (Map.Entry<Stop, Connection> entry : results) {
            sortedResult.put(entry.getKey(), entry.getValue());
        }

        return sortedResult;
    }

    /**
     * Executes a single earliest-arrival (or latest-departure) isoline routing query.
     * <p>
     * This delegates directly to the underlying routing utility without applying any time window or duration
     * minimization logic.
     *
     * @param sourceStops mapping of source stop IDs to their query times
     * @return isolines as computed by the routing algorithm
     */
    private Map<String, org.naviqore.raptor.Connection> runForEarliestArrivalTime(
            Map<String, OffsetDateTime> sourceStops) {
        return utils.routeIsolines(sourceStops, timeType, queryConfig, allowSourceTransfers);
    }

    /**
     * Computes isolines by minimizing total travel duration within a configurable time window.
     * <p>
     * The algorithm repeatedly shifts the source departure/arrival times in discrete increments, runs an
     * earliest-arrival routing query for each shifted time, and keeps the fastest connection per stop across all
     * iterations.
     * <p>
     * Travel duration includes both:
     * <ul>
     *   <li>the routing duration returned by the RAPTOR algorithm, and</li>
     *   <li>the initial offset between the reference time and the source stop time.</li>
     * </ul>
     *
     * @param sourceStops mapping of source stop IDs to their initial times
     * @return a map of stop IDs to their shortest-duration connections within the time window
     */
    private Map<String, org.naviqore.raptor.Connection> runForShortestDurationTime(
            Map<String, OffsetDateTime> sourceStops) {

        // timeType specific operations
        BiFunction<OffsetDateTime, Duration, OffsetDateTime> timeIncrementor = switch (timeType) {
            case DEPARTURE -> OffsetDateTime::plus;
            case ARRIVAL -> OffsetDateTime::minus;
        };

        // initialize variables
        Map<String, org.naviqore.raptor.Connection> shortestTravelTimeIsolines = new HashMap<>();
        Map<String, Duration> costPerSourceStop = calculateCostsPerSourceStop(time, sourceStops);
        Duration nextIncrement = Duration.ZERO;
        OffsetDateTime currentTime = time;
        OffsetDateTime windowLimit = timeIncrementor.apply(time,
                Duration.ofSeconds(queryConfig.getTimeWindowDuration()));

        while (currentTimeIsRelevant(currentTime, windowLimit)) {
            sourceStops = incrementSourceStopTimes(sourceStops, nextIncrement, timeIncrementor);
            Map<String, org.naviqore.raptor.Connection> newIsolines = runForEarliestArrivalTime(sourceStops);
            updateShortestTravelTimeIsolines(shortestTravelTimeIsolines, newIsolines, costPerSourceStop, windowLimit);
            nextIncrement = getNextTimeIncrement(sourceStops, newIsolines);
            if (nextIncrement == null) {
                break;
            }
            currentTime = timeIncrementor.apply(currentTime, nextIncrement);
        }

        return shortestTravelTimeIsolines;
    }

    /**
     * Calculates the initial time offset cost for each source stop relative to a reference time.
     * <p>
     * This represents the walking or access time to reach a source stop before (ARRIVAL) or after (DEPARTURE) the
     * reference time and is later added to the routing duration.
     *
     * @param referenceTime the reference query time
     * @param sourceStops   mapping of source stop IDs to their times
     * @return a map of source stop IDs to their absolute time offset cost
     */
    private Map<String, Duration> calculateCostsPerSourceStop(OffsetDateTime referenceTime,
                                                              Map<String, OffsetDateTime> sourceStops) {
        return sourceStops.entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        entry -> Duration.between(entry.getValue(), referenceTime).abs()));
    }

    /**
     * Determines the next time increment for shifting source stop times.
     * <p>
     * The increment is chosen such that the next routing iteration starts just after the earliest relevant trip time
     * found in the current iteration, ensuring progress while avoiding redundant queries.
     *
     * @param sourceStopTimes current source stop times
     * @param isolines        isolines returned by the latest routing query
     * @return the smallest time increment needed to advance the search, or {@code null} if none exists
     */
    private Duration getNextTimeIncrement(Map<String, OffsetDateTime> sourceStopTimes,
                                          Map<String, org.naviqore.raptor.Connection> isolines) {
        return isolines.values().stream().map(connection -> {
            OffsetDateTime sourceTime = sourceStopTimes.get(getSourceStopIdFromConnection(connection));
            OffsetDateTime tripTime = getTripTimeFromConnection(connection);
            return Duration.between(sourceTime, tripTime).abs().plusSeconds(1);
        }).min(Duration::compareTo).orElse(null);
    }

    /**
     * Updates the per-stop shortest-travel-time isolines with newly computed connections.
     * <p>
     * For each stop contained in {@code earliestArrivalIsolines}, this method decides whether the newly computed
     * connection should replace the currently stored best connection. A replacement occurs if:
     * <ul>
     *   <li>no connection has been stored yet for the stop, or</li>
     *   <li>the new connection has a strictly shorter total travel time (RAPTOR duration
     *       plus source stop access cost),</li>
     * </ul>
     * <em>and</em> the connection's relevant trip time (departure or arrival, depending on
     * the query type) lies within the configured time window.
     * <p>
     * The method mutates {@code shortestTravelTimeIsolines} in place.
     *
     * @param shortestTravelTimeIsolines the current best-known (shortest travel time) connection per stop
     * @param earliestArrivalIsolines    newly computed connections for the current iteration
     * @param costPerSourceStop          precomputed access-time offsets per source stop
     * @param windowLimit                boundary of the valid time window for the query
     */
    private void updateShortestTravelTimeIsolines(
            Map<String, org.naviqore.raptor.Connection> shortestTravelTimeIsolines,
            Map<String, org.naviqore.raptor.Connection> earliestArrivalIsolines,
            Map<String, Duration> costPerSourceStop, OffsetDateTime windowLimit) {
        earliestArrivalIsolines.forEach((stopId, newConnection) -> {
            var bestConnectionSoFar = shortestTravelTimeIsolines.get(stopId);
            if ((bestConnectionSoFar == null || newConnectionIsFaster(newConnection, bestConnectionSoFar,
                    costPerSourceStop)) && newConnectionDepatureTimeIsRelevant(newConnection, windowLimit)) {
                shortestTravelTimeIsolines.put(stopId, newConnection);
            }
        });
    }

    /**
     * Determines whether a newly computed connection is relevant with respect to the configured time window.
     * <p>
     * The relevant timestamp is extracted from the connection based on the query time type:
     * <ul>
     *   <li>for {@code DEPARTURE} queries, the departure time of the first leg</li>
     *   <li>for {@code ARRIVAL} queries, the arrival time of the last leg</li>
     * </ul>
     * The connection is considered relevant if this timestamp lies on the valid side of
     * the provided window limit.
     *
     * @param newConnection the newly computed RAPTOR connection
     * @param windowLimit   the boundary of the query time window
     * @return {@code true} if the connection should still be considered, {@code false} otherwise
     */
    private boolean newConnectionDepatureTimeIsRelevant(org.naviqore.raptor.Connection newConnection,
                                                        OffsetDateTime windowLimit) {
        return currentTimeIsRelevant(getTripTimeFromConnection(newConnection), windowLimit);
    }

    /**
     * Compares two connections and determines whether the new connection represents a shorter total travel time.
     * <p>
     * Total travel time is defined as the RAPTOR-reported duration plus the source stop access cost.
     *
     * @param newConnection       the newly computed connection
     * @param referenceConnection the previously best-known connection
     * @param costPerSourceStop   source stop time offset costs
     * @return {@code true} if the new connection is faster, {@code false} otherwise
     */
    private boolean newConnectionIsFaster(org.naviqore.raptor.Connection newConnection,
                                          org.naviqore.raptor.Connection referenceConnection,
                                          Map<String, Duration> costPerSourceStop) {
        Duration newTravelTime = Duration.ofSeconds(newConnection.getDurationInSeconds())
                .plus(costPerSourceStop.get(getSourceStopIdFromConnection(newConnection)));
        Duration previousBestTravelTime = Duration.ofSeconds(referenceConnection.getDurationInSeconds())
                .plus(costPerSourceStop.get(getSourceStopIdFromConnection(referenceConnection)));
        return newTravelTime.compareTo(previousBestTravelTime) < 0;
    }

    /**
     * Extracts the relevant trip time from a connection based on the query time type.
     * <p>
     * For DEPARTURE queries, this is the departure time of the first leg. For ARRIVAL queries, this is the arrival time
     * of the last leg.
     *
     * @param connection the RAPTOR connection
     * @return the relevant trip time
     */
    private OffsetDateTime getTripTimeFromConnection(org.naviqore.raptor.Connection connection) {
        return timeType == DEPARTURE ? connection.getLegs().getFirst().getDepartureTime() : connection.getLegs()
                .getLast()
                .getArrivalTime();
    }

    /**
     * Determines the source stop ID associated with a connection.
     * <p>
     * For DEPARTURE queries, this is the {@code fromStopId} of the first leg. For ARRIVAL queries, this is the
     * {@code toStopId} of the last leg.
     *
     * @param connection the RAPTOR connection
     * @return the source stop ID
     */
    private String getSourceStopIdFromConnection(org.naviqore.raptor.Connection connection) {
        return timeType == DEPARTURE ? connection.getLegs().getFirst().getFromStopId() : connection.getLegs()
                .getLast()
                .getToStopId();
    }

    /**
     * Shifts all source stop times by a given increment.
     * <p>
     * The direction of the shift (forward or backward in time) depends on the provided time incrementor.
     *
     * @param sourceStopTimes mapping of source stop IDs to their current times
     * @param increment       the time increment to apply
     * @param timeIncrementor function defining how time is shifted
     * @return a new map with updated source stop times
     */
    private Map<String, OffsetDateTime> incrementSourceStopTimes(Map<String, OffsetDateTime> sourceStopTimes,
                                                                 Duration increment,
                                                                 BiFunction<OffsetDateTime, Duration, OffsetDateTime> timeIncrementor) {
        return sourceStopTimes.entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        entry -> timeIncrementor.apply(entry.getValue(), increment)));
    }

    /**
     * Checks whether the current query time is still within the configured time window.
     * <p>
     * For DEPARTURE queries, the current time must be before the window limit. For ARRIVAL queries, the current time
     * must be after the window limit.
     *
     * @param currentTime the current iteration time
     * @param windowLimit the boundary of the time window
     * @return {@code true} if the current time should still be processed
     */
    private boolean currentTimeIsRelevant(OffsetDateTime currentTime, OffsetDateTime windowLimit) {
        return timeType == DEPARTURE && currentTime.isBefore(windowLimit) || timeType == ARRIVAL && currentTime.isAfter(
                windowLimit);
    }

}
