package ch.naviqore.service.gtfs.raptor.routing;

import ch.naviqore.raptor.RaptorAlgorithm;
import ch.naviqore.service.Connection;
import ch.naviqore.service.TimeType;
import ch.naviqore.service.Walk;
import ch.naviqore.service.config.ConnectionQueryConfig;
import ch.naviqore.utils.spatial.GeoCoordinate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

class ConnectionGeoToGeo extends ConnectionQueryTemplate<GeoCoordinate, GeoCoordinate> {

    ConnectionGeoToGeo(LocalDateTime time, TimeType timeType, ConnectionQueryConfig queryConfig,
                       RoutingQueryUtils utils, GeoCoordinate source, GeoCoordinate target) {
        super(time, timeType, queryConfig, utils, source, target);
    }

    @Override
    protected Map<String, LocalDateTime> prepareSourceStops(GeoCoordinate source) {
        return utils.getStopsWithWalkTimeFromLocation(source, time, timeType, queryConfig);
    }

    @Override
    protected Map<String, Integer> prepareTargetStops(GeoCoordinate target) {
        return utils.getStopsWithWalkTimeFromLocation(target, queryConfig);
    }

    @Override
    protected List<Connection> handleInvalidStopException(RaptorAlgorithm.InvalidStopException exception,
                                                          GeoCoordinate source, GeoCoordinate target) {
        return List.of();
    }

    @Override
    protected Connection postprocessConnection(GeoCoordinate source, ch.naviqore.raptor.Connection connection,
                                               GeoCoordinate target) {
        LocalDateTime departureTime = connection.getDepartureTime();
        Walk firstMile = utils.createFirstWalk(source, connection.getFromStopId(), departureTime);

        LocalDateTime arrivalTime = connection.getArrivalTime();
        Walk lastMile = utils.createLastWalk(target, connection.getToStopId(), arrivalTime);

        // TODO: Add sourceStop as source for firstMile if sourceStop != null
        // TODO: Add targetStop as target for lastMile if targetStop != null
        //  COMMENT: I think this is already handled inside 'utils.createFirstWalk' and 'utils.createLastWalk'

        // TODO: Handle case where firstMile is not null and first leg is a transfer --> use walkCalculator?
        // TODO: Handle case where lastMile is not null and last leg is a transfer --> use walkCalculator?
        //  COMMENT: This means we would currently have a walk form our coordinate to the next stop, but then from
        //  there another walk to another stop? Have we considered this case in the old version (non-refactored) version?

        return utils.composeConnection(firstMile, connection, lastMile);
    }

    @Override
    protected ConnectionQueryTemplate<GeoCoordinate, GeoCoordinate> swap(GeoCoordinate source, GeoCoordinate target) {
        return new ConnectionGeoToGeo(time, timeType, queryConfig, utils, target, source);
    }
}
