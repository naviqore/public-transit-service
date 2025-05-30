package org.naviqore.service.gtfs.raptor.routing;

import org.jetbrains.annotations.Nullable;
import org.naviqore.raptor.RaptorAlgorithm;
import org.naviqore.service.Connection;
import org.naviqore.service.Leg;
import org.naviqore.service.Stop;
import org.naviqore.service.TimeType;
import org.naviqore.service.config.ConnectionQueryConfig;
import org.naviqore.utils.spatial.GeoCoordinate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * A connection query between two coordinates.
 */
class ConnectionGeoToGeo extends ConnectionQueryTemplate<GeoCoordinate, GeoCoordinate> {

    @Nullable
    private final Stop sourceStop;

    @Nullable
    private final Stop targetStop;

    /**
     * A connection between two locations. Needs a first mile and last mile walk.
     */
    ConnectionGeoToGeo(LocalDateTime time, TimeType timeType, ConnectionQueryConfig queryConfig,
                       RoutingQueryUtils utils, GeoCoordinate source, GeoCoordinate target) {
        super(time, timeType, queryConfig, utils, source, target, false, false);
        sourceStop = null;
        targetStop = null;
    }

    /**
     * No departures on the source stop, try to walk to another stop.
     */
    ConnectionGeoToGeo(LocalDateTime time, TimeType timeType, ConnectionQueryConfig queryConfig,
                       RoutingQueryUtils utils, Stop source, GeoCoordinate target) {
        super(time, timeType, queryConfig, utils, source.getCoordinate(), target, false, false);
        sourceStop = source;
        targetStop = null;
    }

    /**
     * No departures on the target stop, try to walk to another stop.
     */
    ConnectionGeoToGeo(LocalDateTime time, TimeType timeType, ConnectionQueryConfig queryConfig,
                       RoutingQueryUtils utils, GeoCoordinate source, Stop target) {
        super(time, timeType, queryConfig, utils, source, target.getCoordinate(), false, false);
        sourceStop = null;
        targetStop = target;
    }

    /**
     * No departures on either the source or the target stop. Try to walk on both ends to another stop.
     */
    ConnectionGeoToGeo(LocalDateTime time, TimeType timeType, ConnectionQueryConfig queryConfig,
                       RoutingQueryUtils utils, Stop source, Stop target) {
        super(time, timeType, queryConfig, utils, source.getCoordinate(), target.getCoordinate(), false, false);
        sourceStop = source;
        targetStop = target;
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
    protected Connection postprocessDepartureConnection(GeoCoordinate source, org.naviqore.raptor.Connection connection,
                                                        GeoCoordinate target) {
        // create first mile; this can either be a walk from the last stop in the RAPTOR connection to a coordinate or
        // in the special cases (see constructors), a walk from the last stop to another stop, which has no public
        // transit departures and therefore does not exist in the RAPTOR router.
        return getConnection(target, connection, source, sourceStop, targetStop);
    }

    @Override
    protected Connection postprocessArrivalConnection(GeoCoordinate source, org.naviqore.raptor.Connection connection,
                                                      GeoCoordinate target) {
        // switch the departure case, since we are going back in time in arrival
        return getConnection(source, connection, target, targetStop, sourceStop);
    }

    private Connection getConnection(GeoCoordinate source, org.naviqore.raptor.Connection connection,
                                     GeoCoordinate target, Stop targetStop, Stop sourceStop) {
        LocalDateTime departureTime = connection.getDepartureTime();
        Leg firstMile = targetStop == null ? utils.createFirstWalk(target, connection.getFromStopId(),
                departureTime) : utils.createFirstWalkTransfer(targetStop, connection.getFromStopId(), departureTime);

        LocalDateTime arrivalTime = connection.getArrivalTime();
        Leg lastMile = sourceStop == null ? utils.createLastWalk(source, connection.getToStopId(),
                arrivalTime) : utils.createLastWalkTransfer(sourceStop, connection.getToStopId(), arrivalTime);

        return utils.composeConnection(firstMile, connection, lastMile);
    }

    @Override
    protected ConnectionQueryTemplate<GeoCoordinate, GeoCoordinate> swap(GeoCoordinate source, GeoCoordinate target) {
        return new ConnectionGeoToGeo(time, timeType, queryConfig, utils, target, source);
    }
}
