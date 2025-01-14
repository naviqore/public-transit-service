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

@RequiredArgsConstructor
@Slf4j
abstract class ConnectionQueryTemplate<S, T> {

    protected final LocalDateTime time;
    protected final TimeType timeType;
    protected final ConnectionQueryConfig queryConfig;
    protected final RoutingQueryUtils utils;

    private final S source;
    private final T target;

    protected abstract Map<String, LocalDateTime> prepareSourceStops(S source);

    protected abstract Map<String, Integer> prepareTargetStops(T target);

    protected abstract Connection postprocessConnection(S source, ch.naviqore.raptor.Connection connection, T target);

    protected abstract ConnectionQueryTemplate<T, S> swap(S source, T target);

    List<Connection> run() throws ConnectionRoutingException {
        // TODO: / COMMENT: This is dangerous because if you run the query multiple times (e.g. when routing from
        //  location instead of stop due to InvalidStopException, the swap might occur twice resulting in incorrect
        //  results.

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
            connections = utils.routeConnections(sourceStops, targetStops, timeType, queryConfig);
        } catch (RaptorAlgorithm.InvalidStopException e) {
            log.debug("{}: {}", e.getClass().getSimpleName(), e.getMessage());
            // TODO: Implement abstract handleInvalidStopException method?
            return List.of();
        } catch (IllegalArgumentException e) {
            throw new ConnectionRoutingException(e);
        }

        // assemble connection results
        List<Connection> result = new ArrayList<>();
        for (ch.naviqore.raptor.Connection raptorConnection : connections) {

            Connection serviceConnection = postprocessConnection(source, raptorConnection, target);

            if (utils.isBelowMaximumTravelTime(serviceConnection, queryConfig)) {
                result.add(serviceConnection);
            }
        }

        return result;
    }

}
