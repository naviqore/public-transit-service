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
import java.util.*;

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

}
