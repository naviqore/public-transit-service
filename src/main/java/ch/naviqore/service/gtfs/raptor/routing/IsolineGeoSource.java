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
        super(time, timeType, queryConfig, utils, source, false);
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
    protected Connection postprocessDepartureConnection(GeoCoordinate source,
                                                        ch.naviqore.raptor.Connection connection) {
        Walk firstMile = utils.createFirstWalk(source, connection.getFromStopId(), connection.getDepartureTime());
        return utils.composeConnection(firstMile, connection);
    }

    @Override
    protected Connection postprocessArrivalConnection(GeoCoordinate source, ch.naviqore.raptor.Connection connection) {
        Walk lastMile = utils.createLastWalk(source, connection.getToStopId(), connection.getArrivalTime());
        return utils.composeConnection(connection, lastMile);
    }

}
