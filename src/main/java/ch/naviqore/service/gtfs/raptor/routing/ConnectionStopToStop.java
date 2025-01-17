package ch.naviqore.service.gtfs.raptor.routing;

import ch.naviqore.raptor.RaptorAlgorithm;
import ch.naviqore.service.Connection;
import ch.naviqore.service.Stop;
import ch.naviqore.service.TimeType;
import ch.naviqore.service.config.ConnectionQueryConfig;
import ch.naviqore.service.exception.ConnectionRoutingException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * A connection query between two transit stops.
 */
class ConnectionStopToStop extends ConnectionQueryTemplate<Stop, Stop> {

    ConnectionStopToStop(LocalDateTime time, TimeType timeType, ConnectionQueryConfig queryConfig,
                         RoutingQueryUtils utils, Stop source, Stop target) {
        super(time, timeType, queryConfig, utils, source, target);
    }

    @Override
    protected Map<String, LocalDateTime> prepareSourceStops(Stop source) {
        return utils.getAllChildStopsFromStop(source, time);
    }

    @Override
    protected Map<String, Integer> prepareTargetStops(Stop target) {
        return utils.getAllChildStopsFromStop(target);
    }

    @Override
    protected List<Connection> handleInvalidStopException(RaptorAlgorithm.InvalidStopException exception, Stop source,
                                                          Stop target) throws ConnectionRoutingException {
        return new ConnectionGeoToGeo(time, timeType, queryConfig, utils, source, target).process();
    }

    @Override
    protected Connection postprocessConnection(Stop source, ch.naviqore.raptor.Connection connection, Stop target) {
        return utils.composeConnection(connection);
    }

    @Override
    protected ConnectionQueryTemplate<Stop, Stop> swap(Stop source, Stop target) {
        return new ConnectionStopToStop(time, timeType, queryConfig, utils, target, source);
    }
}
