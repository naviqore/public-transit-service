package ch.naviqore.service.gtfs.raptor.routing;

import ch.naviqore.raptor.RaptorAlgorithm;
import ch.naviqore.service.Connection;
import ch.naviqore.service.Leg;
import ch.naviqore.service.Stop;
import ch.naviqore.service.TimeType;
import ch.naviqore.service.config.ConnectionQueryConfig;
import ch.naviqore.utils.spatial.GeoCoordinate;
import org.jetbrains.annotations.Nullable;

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
        super(time, timeType, queryConfig, utils, source, target);
        sourceStop = null;
        targetStop = null;
    }

    /**
     * No departures on the source stop, try to walk to another stop.
     */
    ConnectionGeoToGeo(LocalDateTime time, TimeType timeType, ConnectionQueryConfig queryConfig,
                       RoutingQueryUtils utils, Stop source, GeoCoordinate target) {
        super(time, timeType, queryConfig, utils, source.getCoordinate(), target);
        sourceStop = source;
        targetStop = null;
    }

    /**
     * No departures on the target stop, try to walk to another stop.
     */
    ConnectionGeoToGeo(LocalDateTime time, TimeType timeType, ConnectionQueryConfig queryConfig,
                       RoutingQueryUtils utils, GeoCoordinate source, Stop target) {
        super(time, timeType, queryConfig, utils, source, target.getCoordinate());
        sourceStop = null;
        targetStop = target;
    }

    /**
     * No departures on either the source or the target stop. Try to walk on both ends to another stop.
     */
    ConnectionGeoToGeo(LocalDateTime time, TimeType timeType, ConnectionQueryConfig queryConfig,
                       RoutingQueryUtils utils, Stop source, Stop target) {
        super(time, timeType, queryConfig, utils, source.getCoordinate(), target.getCoordinate());
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
    protected Connection postprocessConnection(GeoCoordinate source, ch.naviqore.raptor.Connection connection,
                                               GeoCoordinate target) {

        // TODO: Merge two walk legs --> walkCalculator?

        return switch (timeType) {
            case DEPARTURE -> {
                // create first mile; this can either be a walk from the last stop in the RAPTOR connection to a coordinate or
                // in the special cases (see constructors), a walk from the last stop to another stop, which has no public
                // transit departures and therefore does not exist in the RAPTOR router.
                LocalDateTime departureTime = connection.getDepartureTime();
                Leg firstMile = sourceStop == null ? utils.createFirstWalk(source, connection.getFromStopId(),
                        departureTime) : utils.createFirstWalkTransfer(sourceStop, connection.getFromStopId(),
                        departureTime);

                // create last mile; same options as above...
                LocalDateTime arrivalTime = connection.getArrivalTime();
                Leg lastMile = targetStop == null ? utils.createLastWalk(target, connection.getToStopId(),
                        arrivalTime) : utils.createLastWalkTransfer(targetStop, connection.getToStopId(), arrivalTime);

                yield utils.composeConnection(firstMile, connection, lastMile);
            }
            case ARRIVAL -> {
                // switch the departure case, since we are going back in time
                LocalDateTime departureTime = connection.getDepartureTime();
                Leg firstMile = targetStop == null ? utils.createFirstWalk(target, connection.getFromStopId(),
                        departureTime) : utils.createFirstWalkTransfer(targetStop, connection.getFromStopId(),
                        departureTime);

                LocalDateTime arrivalTime = connection.getArrivalTime();
                Leg lastMile = sourceStop == null ? utils.createLastWalk(source, connection.getToStopId(),
                        arrivalTime) : utils.createLastWalkTransfer(sourceStop, connection.getToStopId(), arrivalTime);

                yield utils.composeConnection(firstMile, connection, lastMile);
            }
        };
    }

    @Override
    protected ConnectionQueryTemplate<GeoCoordinate, GeoCoordinate> swap(GeoCoordinate source, GeoCoordinate target) {
        return new ConnectionGeoToGeo(time, timeType, queryConfig, utils, target, source);
    }
}
