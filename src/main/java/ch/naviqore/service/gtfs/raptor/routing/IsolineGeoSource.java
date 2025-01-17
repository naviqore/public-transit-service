package ch.naviqore.service.gtfs.raptor.routing;

import ch.naviqore.raptor.RaptorAlgorithm;
import ch.naviqore.service.Connection;
import ch.naviqore.service.Stop;
import ch.naviqore.service.TimeType;
import ch.naviqore.service.Walk;
import ch.naviqore.service.config.ConnectionQueryConfig;
import ch.naviqore.utils.spatial.GeoCoordinate;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * An isoline query from a coordinate.
 */
class IsolineGeoSource extends IsolineQueryTemplate<GeoCoordinate> {

    IsolineGeoSource(LocalDateTime time, TimeType timeType, ConnectionQueryConfig queryConfig, RoutingQueryUtils utils,
                     GeoCoordinate source) {
        super(time, timeType, queryConfig, utils, source);
    }

    @Override
    protected Map<String, LocalDateTime> prepareSourceStops(GeoCoordinate source) {
        return utils.getStopsWithWalkTimeFromLocation(source, time, timeType, queryConfig);
    }

    @Override
    protected Map<Stop, Connection> handleInvalidStopException(RaptorAlgorithm.InvalidStopException exception,
                                                               GeoCoordinate source) {
        return Map.of();
    }

    @Override
    protected Connection postprocessConnection(GeoCoordinate source, ch.naviqore.raptor.Connection connection) {

        // TODO: Handle case where firstMile is not null and first leg is a transfer --> use walkCalculator
        // TODO: Handle case where lastMile is not null and last leg is a transfer --> use walkCalculator

        // TODO: Add case where source is stop but should be processed as geo-coordinate -> see connection query template

        return switch (timeType) {
            case ARRIVAL -> {
                Walk firstMile = utils.createFirstWalk(source, connection.getFromStopId(),
                        connection.getDepartureTime());
                yield utils.composeConnection(firstMile, connection);
            }
            case DEPARTURE -> {
                Walk lastMile = utils.createLastWalk(source, connection.getFromStopId(), connection.getDepartureTime());
                yield utils.composeConnection(connection, lastMile);
            }
        };
    }
}
