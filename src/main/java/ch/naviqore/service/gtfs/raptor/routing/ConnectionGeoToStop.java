package ch.naviqore.service.gtfs.raptor.routing;

import ch.naviqore.service.Connection;
import ch.naviqore.service.Stop;
import ch.naviqore.service.TimeType;
import ch.naviqore.service.Walk;
import ch.naviqore.service.config.ConnectionQueryConfig;
import ch.naviqore.utils.spatial.GeoCoordinate;

import java.time.LocalDateTime;
import java.util.Map;

class ConnectionGeoToStop extends ConnectionQueryTemplate<GeoCoordinate, Stop> {

    ConnectionGeoToStop(LocalDateTime time, TimeType timeType, ConnectionQueryConfig queryConfig,
                        RoutingQueryUtils utils, GeoCoordinate source, Stop target) {
        super(time, timeType, queryConfig, utils, source, target);
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
    protected Connection postprocessConnection(GeoCoordinate source, ch.naviqore.raptor.Connection connection,
                                               Stop target) {
        LocalDateTime departureTime = connection.getDepartureTime();
        Walk firstMile = utils.createLastWalk(source, connection.getFromStopId(), departureTime);
        Walk lastMile = utils.createLastWalk(source, connection.getFromStopId(), departureTime);

        // TODO: Handle case where firstMile is not null and first leg is a transfer --> use walkCalculator

        return utils.composeConnection(firstMile, connection, lastMile);
    }

    @Override
    protected ConnectionQueryTemplate<Stop, GeoCoordinate> swap(GeoCoordinate source, Stop target) {
        return new ConnectionStopToGeo(time, timeType, queryConfig, utils, target, source);
    }
}