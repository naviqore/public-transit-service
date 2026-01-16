package org.naviqore.service.gtfs.raptor.routing;

import org.naviqore.raptor.RaptorAlgorithm;
import org.naviqore.service.Connection;
import org.naviqore.service.Stop;
import org.naviqore.service.TimeType;
import org.naviqore.service.Walk;
import org.naviqore.service.config.ConnectionQueryConfig;
import org.naviqore.utils.spatial.GeoCoordinate;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * An isoline query from a coordinate.
 */
class IsolineGeoSource extends IsolineQueryTemplate<GeoCoordinate> {

    IsolineGeoSource(OffsetDateTime time, TimeType timeType, ConnectionQueryConfig queryConfig, RoutingQueryUtils utils,
                     GeoCoordinate source) {
        super(time, timeType, queryConfig, utils, source, false);
    }

    @Override
    protected Map<String, OffsetDateTime> prepareSourceStops(GeoCoordinate source) {
        return utils.getStopsWithWalkTimeFromLocation(source, time, timeType, queryConfig);
    }

    @Override
    protected Map<Stop, Connection> handleInvalidStopException(RaptorAlgorithm.InvalidStopException exception,
                                                               GeoCoordinate source) {
        return Map.of();
    }

    @Override
    protected Connection postprocessDepartureConnection(GeoCoordinate source,
                                                        org.naviqore.raptor.Connection connection) {
        Walk firstMile = utils.createFirstWalk(source, connection.getFromStopId(), connection.getDepartureTime());
        return utils.composeConnection(firstMile, connection);
    }

    @Override
    protected Connection postprocessArrivalConnection(GeoCoordinate source, org.naviqore.raptor.Connection connection) {
        Walk lastMile = utils.createLastWalk(source, connection.getToStopId(), connection.getArrivalTime());
        return utils.composeConnection(connection, lastMile);
    }

}
