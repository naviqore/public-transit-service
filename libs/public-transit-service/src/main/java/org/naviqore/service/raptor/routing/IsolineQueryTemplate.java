package org.naviqore.service.raptor.routing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.naviqore.raptor.RaptorAlgorithm;
import org.naviqore.service.Connection;
import org.naviqore.service.Stop;
import org.naviqore.service.TimeType;
import org.naviqore.service.config.ConnectionQueryConfig;
import org.naviqore.service.exception.ConnectionRoutingException;
import org.naviqore.service.raptor.TypeMapper;

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

    /**
     * The trips the connections of this query ride, mapped once each instead of once per ride.
     */
    protected final TypeMapper.Trips trips = new TypeMapper.Trips();

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

        // query the earliest arrival, or with a time window the shortest travel time, to each stop
        Map<String, org.naviqore.raptor.Connection> isolines;
        try {
            isolines = utils.routeIsolines(sourceStops, timeType, queryConfig, allowSourceTransfers);
        } catch (RaptorAlgorithm.InvalidStopException e) {
            log.debug("{}: {}", e.getClass().getSimpleName(), e.getMessage());
            return handleInvalidStopException(e, source);
        } catch (IllegalArgumentException e) {
            throw new ConnectionRoutingException(e);
        }

        // convert to service connections and collect results in a list to avoid intermediate hashing overhead
        List<ReachedStop> results = new ArrayList<>(isolines.size());
        for (Map.Entry<String, org.naviqore.raptor.Connection> entry : isolines.entrySet()) {
            org.naviqore.raptor.Connection raptorConnection = entry.getValue();
            Stop stop = utils.getStopById(entry.getKey());

            Connection serviceConnection = switch (timeType) {
                case ARRIVAL -> postprocessArrivalConnection(source, raptorConnection);
                case DEPARTURE -> postprocessDepartureConnection(source, raptorConnection);
            };

            if (utils.isBelowMaximumTravelTime(serviceConnection, queryConfig)) {
                results.add(ReachedStop.of(stop, serviceConnection, timeType));
            }
        }

        // sort connections based on time type
        Comparator<ReachedStop> byTime = Comparator.comparingLong(ReachedStop::epochNanos)
                .thenComparingInt(ReachedStop::offsetSeconds);
        Comparator<ReachedStop> comparator = switch (timeType) {
            // by arrival time ASC; earlier arrival at destination = closer
            case DEPARTURE -> byTime;
            // by departure time DESC; later departure from origin = closer
            case ARRIVAL -> byTime.reversed();
        };
        // use stop name as tie-breaker for deterministic results
        results.sort(comparator.thenComparing(ReachedStop::name));

        // assemble isoline result
        Map<Stop, Connection> sortedResult = LinkedHashMap.newLinkedHashMap(results.size());
        for (ReachedStop result : results) {
            sortedResult.put(result.stop(), result.connection());
        }

        return sortedResult;
    }

    /**
     * A stop of the result with what it is sorted by, read once from its connection instead of in every comparison: the
     * time that makes it closer, as an instant and an offset, which order times as {@link OffsetDateTime} does, and the
     * name of the stop.
     */
    private record ReachedStop(Stop stop, Connection connection, long epochNanos, int offsetSeconds, String name) {

        static ReachedStop of(Stop stop, Connection connection, TimeType timeType) {
            OffsetDateTime time = switch (timeType) {
                case DEPARTURE -> connection.getArrivalTime();
                case ARRIVAL -> connection.getDepartureTime();
            };
            return new ReachedStop(stop, connection, time.toEpochSecond() * 1_000_000_000L + time.getNano(),
                    time.getOffset().getTotalSeconds(), stop.getName());
        }
    }

}
