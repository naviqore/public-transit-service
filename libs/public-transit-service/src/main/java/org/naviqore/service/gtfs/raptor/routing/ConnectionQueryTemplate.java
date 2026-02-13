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
     * Warning: Do not call this method outside the routing facade, use the process method directly instead. Otherwise,
     * swapping could occur twice.
     */
    List<Connection> run() throws ConnectionRoutingException {
        List<Connection> connections = new ArrayList<>();
        OffsetDateTime currentTime = time;
        OffsetDateTime windowLimit = switch (timeType) {
            case DEPARTURE -> time.plusSeconds(queryConfig.getTimeWindowDuration());
            case ARRIVAL -> time.minusSeconds(queryConfig.getTimeWindowDuration());
        };

        // loop through time window
        do {
            List<Connection> results = switch (timeType) {
                case DEPARTURE -> copyAt(currentTime).process();
                // swap source and target if time type is arrival, which means routing in the reverse time dimension
                case ARRIVAL -> copyAt(currentTime).swap(source, target).process();
            };

            if (results.isEmpty()) {
                break;
            }

            // min oder max departure time
            currentTime = switch (timeType) {
                case DEPARTURE -> results.stream()
                        .map(Connection::getDepartureTime)
                        .min(OffsetDateTime::compareTo)
                        .get()
                        .plusSeconds(1);
                case ARRIVAL -> results.stream()
                        .map(Connection::getArrivalTime)
                        .max(OffsetDateTime::compareTo)
                        .get()
                        .minusSeconds(1);
            };

            connections.addAll(results);

        } while (switch (timeType) {
            case DEPARTURE -> currentTime.isBefore(windowLimit);
            case ARRIVAL -> currentTime.isAfter(windowLimit);
        });

        return connections;
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

        // sort connections based on time type
        result.sort(switch (timeType) {
            case DEPARTURE -> Comparator.comparing(Connection::getDepartureTime);
            case ARRIVAL -> Comparator.comparing(Connection::getArrivalTime).reversed();
        });

        return result;
    }

}
