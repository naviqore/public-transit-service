package org.naviqore.service.gtfs.raptor.routing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.naviqore.raptor.RaptorAlgorithm;
import org.naviqore.service.Connection;
import org.naviqore.service.TimeType;
import org.naviqore.service.config.ConnectionQueryConfig;
import org.naviqore.service.exception.ConnectionRoutingException;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.naviqore.service.TimeType.DEPARTURE;

/**
 * Template for executing a connection query between a source and a target location, encapsulating common logic and
 * providing entry points for customization.
 *
 * @param <S> Type of the source location.
 * @param <T> Type of the target location.
 */
@RequiredArgsConstructor
@Slf4j
abstract class ConnectionQueryTemplate<S, T> {

    protected final OffsetDateTime time;
    protected final TimeType timeType;
    protected final ConnectionQueryConfig queryConfig;
    protected final RoutingQueryUtils utils;

    protected final S source;
    protected final T target;

    private final boolean allowSourceTransfers;
    private final boolean allowTargetTransfers;

    protected abstract Map<String, OffsetDateTime> prepareSourceStops(S source);

    protected abstract Map<String, Integer> prepareTargetStops(T target);

    /**
     * Handle case when there are no departures from or to requested stops.
     * <p>
     * Note: Strategy could be to try walking to other stops first.
     */
    protected abstract List<Connection> handleInvalidStopException(RaptorAlgorithm.InvalidStopException exception,
                                                                   S source,
                                                                   T target) throws ConnectionRoutingException;

    protected abstract Connection postprocessDepartureConnection(S source, org.naviqore.raptor.Connection connection,
                                                                 T target);

    protected abstract Connection postprocessArrivalConnection(S source, org.naviqore.raptor.Connection connection,
                                                               T target);

    protected abstract ConnectionQueryTemplate<T, S> swap(S source, T target);

    protected abstract ConnectionQueryTemplate<S, T> copyAt(OffsetDateTime time);

    /**
     * Executes the routing query over the configured time window.
     * <p>
     * The query is repeatedly executed while advancing (DEPARTURE) or rewinding (ARRIVAL) the query time until the
     * window limit is reached or no more results are found.
     * <p>
     * Warning: Do not call this method outside the routing facade, use the process method directly instead. Otherwise,
     * swapping could occur twice.
     */
    List<Connection> run() throws ConnectionRoutingException {
        List<Connection> connections = new ArrayList<>();
        OffsetDateTime currentTime = time;
        OffsetDateTime windowLimit = computeWindowLimit();

        do {
            List<Connection> results = executeRoutingAt(currentTime);

            results = filterByTimeWindowIfNeeded(results, windowLimit);
            if (results.isEmpty()) {
                break;
            }

            currentTime = advanceQueryTime(results);
            connections.addAll(results);

        } while (isWithinTimeWindow(currentTime, windowLimit));

        return sortConnectionsBasedOnTimeType(connections);
    }

    /**
     * Computes the boundary of the routing time window based on the query type.
     */
    private OffsetDateTime computeWindowLimit() {
        return switch (timeType) {
            case DEPARTURE -> time.plusSeconds(queryConfig.getTimeWindowDuration());
            case ARRIVAL -> time.minusSeconds(queryConfig.getTimeWindowDuration());
        };
    }

    /**
     * Executes a single routing query at the given time.
     * <p>
     * For ARRIVAL queries, the source and target are swapped to route in reverse time.
     */
    private List<Connection> executeRoutingAt(OffsetDateTime queryTime) throws ConnectionRoutingException {
        return switch (timeType) {
            case DEPARTURE -> copyAt(queryTime).process();
            case ARRIVAL -> copyAt(queryTime).swap(source, target).process();
        };
    }

    /**
     * Filters routing results to ensure they lie within the configured time window, if a window duration is defined.
     */
    private List<Connection> filterByTimeWindowIfNeeded(List<Connection> results, OffsetDateTime windowLimit) {
        if (queryConfig.getTimeWindowDuration() <= 0) {
            return results;
        }
        return removeConnectionsOutsideOfTimeWindow(results, windowLimit);
    }

    /**
     * Advances (or rewinds) the query time based on the last batch of results.
     * <p>
     * DEPARTURE queries move forward from the earliest departure. ARRIVAL queries move backward from the latest
     * arrival.
     */
    private OffsetDateTime advanceQueryTime(List<Connection> results) {
        return switch (timeType) {
            case DEPARTURE -> results.stream()
                    .map(Connection::getDepartureTime)
                    .min(OffsetDateTime::compareTo)
                    .orElseThrow()
                    .plusSeconds(1);
            case ARRIVAL -> results.stream()
                    .map(Connection::getArrivalTime)
                    .max(OffsetDateTime::compareTo)
                    .orElseThrow()
                    .minusSeconds(1);
        };
    }

    /**
     * Checks whether the current query time is still within the routing time window.
     */
    private boolean isWithinTimeWindow(OffsetDateTime currentTime, OffsetDateTime windowLimit) {
        return switch (timeType) {
            case DEPARTURE -> currentTime.isBefore(windowLimit);
            case ARRIVAL -> currentTime.isAfter(windowLimit);
        };
    }

    /**
     * Filters out connections that fall outside the configured time window.
     * <p>
     * For DEPARTURE queries, only connections departing <em>before</em> the window limit are retained. For ARRIVAL
     * queries, only connections arriving <em>after</em> the window limit are retained.
     * <p>
     * This method is used to ensure that accumulated results remain within the active time window when iterating over
     * multiple routing runs.
     *
     * @param connections the list of connections to filter
     * @param windowLimit the boundary of the time window
     * @return a list of connections that lie within the time window
     */
    private List<Connection> removeConnectionsOutsideOfTimeWindow(List<Connection> connections,
                                                                  OffsetDateTime windowLimit) {
        return connections.stream()
                .filter(c -> timeType == DEPARTURE ? c.getDepartureTime().isBefore(windowLimit) : c.getArrivalTime()
                        .isAfter(windowLimit))
                .toList();
    }

    List<Connection> process() throws ConnectionRoutingException {
        Map<String, OffsetDateTime> sourceStops = prepareSourceStops(source);
        Map<String, Integer> targetStops = prepareTargetStops(target);

        // no source stop or target stop is within walkable distance, and therefore no connections are available
        if (sourceStops.isEmpty() || targetStops.isEmpty()) {
            return List.of();
        }

        // query connection from raptor
        List<org.naviqore.raptor.Connection> connections;
        try {
            connections = utils.routeConnections(sourceStops, targetStops, timeType, queryConfig, allowSourceTransfers,
                    allowTargetTransfers);
        } catch (RaptorAlgorithm.InvalidStopException e) {
            log.debug("{}: {}", e.getClass().getSimpleName(), e.getMessage());
            return handleInvalidStopException(e, source, target);
        } catch (IllegalArgumentException e) {
            throw new ConnectionRoutingException(e);
        }

        // assemble connection results
        List<Connection> result = new ArrayList<>(connections.size());
        for (org.naviqore.raptor.Connection raptorConnection : connections) {

            Connection serviceConnection = switch (timeType) {
                case ARRIVAL -> postprocessArrivalConnection(source, raptorConnection, target);
                case DEPARTURE -> postprocessDepartureConnection(source, raptorConnection, target);
            };

            if (utils.isBelowMaximumTravelTime(serviceConnection, queryConfig)) {
                result.add(serviceConnection);
            }
        }

        return result;
    }

    private List<Connection> sortConnectionsBasedOnTimeType(List<Connection> connections) {
        return switch (timeType) {
            case DEPARTURE -> connections.stream().sorted(Comparator.comparing(Connection::getDepartureTime)).toList();
            case ARRIVAL ->
                    connections.stream().sorted(Comparator.comparing(Connection::getArrivalTime).reversed()).toList();
        };
    }

}
