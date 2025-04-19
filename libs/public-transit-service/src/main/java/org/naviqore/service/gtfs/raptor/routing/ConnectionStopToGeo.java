package org.naviqore.service.gtfs.raptor.routing;

import org.naviqore.raptor.RaptorAlgorithm;
import org.naviqore.service.*;
import org.naviqore.service.config.ConnectionQueryConfig;
import org.naviqore.service.exception.ConnectionRoutingException;
import org.naviqore.utils.spatial.GeoCoordinate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * A connection query between a transit stop and a coordinate.
 */
class ConnectionStopToGeo extends ConnectionQueryTemplate<Stop, GeoCoordinate> {

    ConnectionStopToGeo(LocalDateTime time, TimeType timeType, ConnectionQueryConfig queryConfig,
                        RoutingQueryUtils utils, Stop source, GeoCoordinate target) {
        super(time, timeType, queryConfig, utils, source, target, true, false);
    }

    @Override
    protected Map<String, LocalDateTime> prepareSourceStops(Stop source) {
        return utils.getAllChildStopsFromStop(source, time);
    }

    @Override
    protected Map<String, Integer> prepareTargetStops(GeoCoordinate target) {
        return utils.getStopsWithWalkTimeFromLocation(target, queryConfig);
    }

    @Override
    protected List<Connection> handleInvalidStopException(RaptorAlgorithm.InvalidStopException exception, Stop source,
                                                          GeoCoordinate target) throws ConnectionRoutingException {
        return new ConnectionGeoToGeo(time, timeType, queryConfig, utils, source, target).process();
    }

    @Override
    protected Connection postprocessDepartureConnection(Stop source, org.naviqore.raptor.Connection connection,
                                                        GeoCoordinate target) {
        LocalDateTime arrivalTime = connection.getArrivalTime();
        Walk lastMile = utils.createLastWalk(target, connection.getToStopId(), arrivalTime);
        return utils.composeConnection(connection, lastMile);
    }

    @Override
    protected Connection postprocessArrivalConnection(Stop source, org.naviqore.raptor.Connection connection,
                                                      GeoCoordinate target) {
        LocalDateTime departureTime = connection.getDepartureTime();
        Leg firstMile = utils.createFirstWalk(target, connection.getFromStopId(), departureTime);
        return utils.composeConnection(firstMile, connection);
    }

    @Override
    protected ConnectionQueryTemplate<GeoCoordinate, Stop> swap(Stop source, GeoCoordinate target) {
        return new ConnectionGeoToStop(time, timeType, queryConfig, utils, target, source);
    }
}
