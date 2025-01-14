package ch.naviqore.service.gtfs.raptor.routing;

import ch.naviqore.gtfs.schedule.model.GtfsSchedule;
import ch.naviqore.raptor.RaptorAlgorithm;
import ch.naviqore.service.Connection;
import ch.naviqore.service.Stop;
import ch.naviqore.service.TimeType;
import ch.naviqore.service.config.ConnectionQueryConfig;
import ch.naviqore.service.exception.ConnectionRoutingException;
import ch.naviqore.service.gtfs.raptor.TypeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
abstract class IsolineQueryTemplate<T> {

    protected final LocalDateTime time;
    protected final TimeType timeType;
    protected final ConnectionQueryConfig queryConfig;
    protected final RoutingQueryUtils utils;

    private final GtfsSchedule schedule;
    private final T source;

    protected abstract Map<String, LocalDateTime> prepareSourceStops(T source);

    protected abstract Connection postprocessConnection(T source, ch.naviqore.raptor.Connection connection);

    Map<Stop, Connection> run() throws ConnectionRoutingException {
        Map<String, LocalDateTime> sourceStops = prepareSourceStops(source);

        // no source stop is within walkable distance, and therefore no isolines are available
        if (sourceStops.isEmpty()) {
            return Map.of();
        }

        // query isolines from raptor
        Map<String, ch.naviqore.raptor.Connection> isolines;
        try {
            isolines = utils.createIsolines(sourceStops, timeType, queryConfig);
        } catch (RaptorAlgorithm.InvalidStopException e) {
            log.debug("{}: {}", e.getClass().getSimpleName(), e.getMessage());
            return Map.of();
        } catch (IllegalArgumentException e) {
            throw new ConnectionRoutingException(e);
        }

        // assemble isoline results
        Map<Stop, Connection> result = new HashMap<>();
        for (Map.Entry<String, ch.naviqore.raptor.Connection> entry : isolines.entrySet()) {
            ch.naviqore.raptor.Connection connection = entry.getValue();
            Stop stop = TypeMapper.map(schedule.getStops().get(entry.getKey()));

            Connection serviceConnection = postprocessConnection(source, connection);

            if (utils.isBelowMaximumTravelTime(serviceConnection, queryConfig)) {
                result.put(stop, serviceConnection);
            }
        }

        return result;
    }

}
