package ch.naviqore.service.gtfs.raptor.routing;

import ch.naviqore.raptor.RaptorAlgorithm;
import ch.naviqore.service.Connection;
import ch.naviqore.service.Stop;
import ch.naviqore.service.TimeType;
import ch.naviqore.service.config.ConnectionQueryConfig;
import ch.naviqore.service.exception.ConnectionRoutingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Template for executing an isoline query from a source location, encapsulating common logic and providing entry points
 * for customization.
 *
 * @param <T> Type of the source location.
 */
@Slf4j
@RequiredArgsConstructor
abstract class IsolineQueryTemplate<T> {

    protected final LocalDateTime time;
    protected final TimeType timeType;
    protected final ConnectionQueryConfig queryConfig;
    protected final RoutingQueryUtils utils;

    private final T source;

    private final boolean allowSourceTransfers;

    protected abstract Map<String, LocalDateTime> prepareSourceStops(T source);

    protected abstract Map<Stop, Connection> handleInvalidStopException(RaptorAlgorithm.InvalidStopException exception,
                                                                        T source) throws ConnectionRoutingException;

    protected abstract Connection postprocessDepartureConnection(T source, ch.naviqore.raptor.Connection connection);

    protected abstract Connection postprocessArrivalConnection(T source, ch.naviqore.raptor.Connection connection);

    Map<Stop, Connection> run() throws ConnectionRoutingException {
        Map<String, LocalDateTime> sourceStops = prepareSourceStops(source);

        // no source stop is within walkable distance, and therefore no isolines are available
        if (sourceStops.isEmpty()) {
            return Map.of();
        }

        // query isolines from raptor
        Map<String, ch.naviqore.raptor.Connection> isolines;
        try {
            isolines = utils.createIsolines(sourceStops, timeType, queryConfig, allowSourceTransfers);
        } catch (RaptorAlgorithm.InvalidStopException e) {
            log.debug("{}: {}", e.getClass().getSimpleName(), e.getMessage());
            return handleInvalidStopException(e, source);
        } catch (IllegalArgumentException e) {
            throw new ConnectionRoutingException(e);
        }

        // assemble isoline results
        Map<Stop, Connection> result = new HashMap<>();
        for (Map.Entry<String, ch.naviqore.raptor.Connection> entry : isolines.entrySet()) {
            ch.naviqore.raptor.Connection connection = entry.getValue();
            Stop stop = utils.getStopById(entry.getKey());

            Connection serviceConnection = switch (timeType) {
                case ARRIVAL -> postprocessArrivalConnection(source, connection);
                case DEPARTURE -> postprocessDepartureConnection(source, connection);
            };

            if (utils.isBelowMaximumTravelTime(serviceConnection, queryConfig)) {
                result.put(stop, serviceConnection);
            }
        }

        return result;
    }

}
