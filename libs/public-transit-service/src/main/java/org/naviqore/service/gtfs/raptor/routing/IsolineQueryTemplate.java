package org.naviqore.service.gtfs.raptor.routing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.naviqore.raptor.RaptorAlgorithm;
import org.naviqore.service.Connection;
import org.naviqore.service.Stop;
import org.naviqore.service.TimeType;
import org.naviqore.service.config.ConnectionQueryConfig;
import org.naviqore.service.exception.ConnectionRoutingException;

import java.time.OffsetDateTime;
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

        // query isolines from raptor
        Map<String, org.naviqore.raptor.Connection> isolines;
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
        for (Map.Entry<String, org.naviqore.raptor.Connection> entry : isolines.entrySet()) {
            org.naviqore.raptor.Connection connection = entry.getValue();
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
