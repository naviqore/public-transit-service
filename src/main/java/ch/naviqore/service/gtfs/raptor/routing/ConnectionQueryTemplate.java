package ch.naviqore.service.gtfs.raptor.routing;

import ch.naviqore.raptor.RaptorAlgorithm;
import ch.naviqore.service.Connection;
import ch.naviqore.service.TimeType;
import ch.naviqore.service.config.ConnectionQueryConfig;
import ch.naviqore.service.exception.ConnectionRoutingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.ArrayList;
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

    protected final LocalDateTime time;
    protected final TimeType timeType;
    protected final ConnectionQueryConfig queryConfig;
    protected final RoutingQueryUtils utils;

    private final S source;
    private final T target;

    private final boolean allowSourceTransfers;
    private final boolean allowTargetTransfers;

    protected abstract Map<String, LocalDateTime> prepareSourceStops(S source);

    protected abstract Map<String, Integer> prepareTargetStops(T target);

    /**
     * Handle case when there are no departures from or to requested stops.
     * <p>
     * Note: Strategy could be to try walking to other stops first.
     */
    protected abstract List<Connection> handleInvalidStopException(RaptorAlgorithm.InvalidStopException exception,
                                                                   S source,
                                                                   T target) throws ConnectionRoutingException;

    protected abstract Connection postprocessDepartureConnection(S source, ch.naviqore.raptor.Connection connection,
                                                                 T target);

    protected abstract Connection postprocessArrivalConnection(S source, ch.naviqore.raptor.Connection connection,
                                                               T target);

    protected abstract ConnectionQueryTemplate<T, S> swap(S source, T target);

    /**
     * Warning: Do not call this method outside the routing facade, use the process method directly instead. Otherwise,
     * swapping could appear twice.
     */
    List<Connection> run() throws ConnectionRoutingException {
        // swap source and target if time type is arrival, which means routing in the reverse time dimension
        if (timeType == TimeType.ARRIVAL) {
            return swap(source, target).process();
        }

        return process();
    }

    List<Connection> process() throws ConnectionRoutingException {
        Map<String, LocalDateTime> sourceStops = prepareSourceStops(source);
        Map<String, Integer> targetStops = prepareTargetStops(target);

        // no source stop or target stop is within walkable distance, and therefore no connections are available
        if (sourceStops.isEmpty() || targetStops.isEmpty()) {
            return List.of();
        }

        // query connection from raptor
        List<ch.naviqore.raptor.Connection> connections;
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
        List<Connection> result = new ArrayList<>();
        for (ch.naviqore.raptor.Connection raptorConnection : connections) {

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

}
