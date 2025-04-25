package org.naviqore.service.gtfs.raptor.routing;

import org.naviqore.raptor.RaptorAlgorithm;
import org.naviqore.service.Connection;
import org.naviqore.service.Leg;
import org.naviqore.service.Stop;
import org.naviqore.service.TimeType;
import org.naviqore.service.config.ConnectionQueryConfig;
import org.naviqore.service.exception.ConnectionRoutingException;
import org.naviqore.utils.spatial.GeoCoordinate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * A connection query between a coordinate and a transit stop.
 */
class ConnectionGeoToStop extends ConnectionQueryTemplate<GeoCoordinate, Stop> {

    ConnectionGeoToStop(LocalDateTime time, TimeType timeType, ConnectionQueryConfig queryConfig,
                        RoutingQueryUtils utils, GeoCoordinate source, Stop target) {
        super(time, timeType, queryConfig, utils, source, target, false, true);
    }

    @Override
    protected Map<String, LocalDateTime> prepareSourceStops(GeoCoordinate source) {
        return utils.getStopsWithWalkTimeFromLocation(source, time, timeType, queryConfig);
    }

    @Override
    protected Map<String, Integer> prepareTargetStops(Stop target) {
        return utils.getAllChildStopsFromStop(target);
    }

    @Override
    protected List<Connection> handleInvalidStopException(RaptorAlgorithm.InvalidStopException exception,
                                                          GeoCoordinate source,
                                                          Stop target) throws ConnectionRoutingException {
        return new ConnectionGeoToGeo(time, timeType, queryConfig, utils, source, target).process();
    }

    @Override
    protected Connection postprocessDepartureConnection(GeoCoordinate source, org.naviqore.raptor.Connection connection,
                                                        Stop target) {
        LocalDateTime departureTime = connection.getDepartureTime();
        Leg firstMile = utils.createFirstWalk(source, connection.getFromStopId(), departureTime);
        return utils.composeConnection(firstMile, connection);
    }

    @Override
    protected Connection postprocessArrivalConnection(GeoCoordinate source, org.naviqore.raptor.Connection connection,
                                                      Stop target) {
        LocalDateTime arrivalTime = connection.getArrivalTime();
        Leg lastMile = utils.createLastWalk(source, connection.getToStopId(), arrivalTime);
        return utils.composeConnection(connection, lastMile);
    }

    @Override
    protected ConnectionQueryTemplate<Stop, GeoCoordinate> swap(GeoCoordinate source, Stop target) {
        return new ConnectionStopToGeo(time, timeType, queryConfig, utils, target, source);
    }
}
