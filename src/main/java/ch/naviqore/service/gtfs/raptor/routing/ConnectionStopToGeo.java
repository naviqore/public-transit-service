package ch.naviqore.service.gtfs.raptor.routing;

import ch.naviqore.raptor.RaptorAlgorithm;
import ch.naviqore.service.Connection;
import ch.naviqore.service.Stop;
import ch.naviqore.service.TimeType;
import ch.naviqore.service.Walk;
import ch.naviqore.service.config.ConnectionQueryConfig;
import ch.naviqore.service.exception.ConnectionRoutingException;
import ch.naviqore.utils.spatial.GeoCoordinate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * A connection query between a transit stop and a coordinate.
 */
class ConnectionStopToGeo extends ConnectionQueryTemplate<Stop, GeoCoordinate> {

    ConnectionStopToGeo(LocalDateTime time, TimeType timeType, ConnectionQueryConfig queryConfig,
                        RoutingQueryUtils utils, Stop source, GeoCoordinate target) {
        super(time, timeType, queryConfig, utils, source, target);
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
    protected Connection postprocessConnection(Stop source, ch.naviqore.raptor.Connection connection,
                                               GeoCoordinate target) {
        LocalDateTime arrivalTime = connection.getArrivalTime();
        Walk lastMile = utils.createLastWalk(target, connection.getToStopId(), arrivalTime);

        // TODO: Merge two walk legs

        return utils.composeConnection(connection, lastMile);
    }

    @Override
    protected ConnectionQueryTemplate<GeoCoordinate, Stop> swap(Stop source, GeoCoordinate target) {
        return new ConnectionGeoToStop(time, timeType, queryConfig, utils, target, source);
    }
}
