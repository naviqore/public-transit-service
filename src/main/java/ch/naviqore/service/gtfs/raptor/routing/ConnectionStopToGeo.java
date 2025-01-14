package ch.naviqore.service.gtfs.raptor.routing;

import ch.naviqore.service.Connection;
import ch.naviqore.service.Stop;
import ch.naviqore.service.TimeType;
import ch.naviqore.service.Walk;
import ch.naviqore.service.config.ConnectionQueryConfig;
import ch.naviqore.utils.spatial.GeoCoordinate;

import java.time.LocalDateTime;
import java.util.Map;

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
    protected Connection postprocessConnection(Stop source, ch.naviqore.raptor.Connection connection,
                                               GeoCoordinate target) {
        LocalDateTime arrivalTime = connection.getArrivalTime();
        Walk lastMile = utils.createLastWalk(target, connection.getToStopId(), arrivalTime);

        return utils.composeConnection(connection, lastMile);
    }

    @Override
    protected ConnectionQueryTemplate<GeoCoordinate, Stop> swap(Stop source, GeoCoordinate target) {
        return new ConnectionGeoToStop(time, timeType, queryConfig, utils, target, source);
    }
}
