package ch.naviqore.service.gtfs.raptor.routing;

import ch.naviqore.raptor.RaptorAlgorithm;
import ch.naviqore.service.Connection;
import ch.naviqore.service.Stop;
import ch.naviqore.service.TimeType;
import ch.naviqore.service.config.ConnectionQueryConfig;
import ch.naviqore.service.exception.ConnectionRoutingException;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * An isoline query from a transit stop.
 */
class IsolineStopSource extends IsolineQueryTemplate<Stop> {

    IsolineStopSource(LocalDateTime time, TimeType timeType, ConnectionQueryConfig queryConfig, RoutingQueryUtils utils,
                      Stop source) {
        super(time, timeType, queryConfig, utils, source, true);
    }

    @Override
    protected Map<String, LocalDateTime> prepareSourceStops(Stop source) {
        return utils.getAllChildStopsFromStop(source, time);
    }

    @Override
    protected Map<Stop, Connection> handleInvalidStopException(RaptorAlgorithm.InvalidStopException exception,
                                                               Stop source) throws ConnectionRoutingException {
        return new IsolineGeoSource(time, timeType, queryConfig, utils, source.getCoordinate()).run();
    }

    @Override
    protected Connection postprocessDepartureConnection(Stop source, ch.naviqore.raptor.Connection connection) {
        return utils.composeConnection(connection);
    }

    @Override
    protected Connection postprocessArrivalConnection(Stop source, ch.naviqore.raptor.Connection connection) {
        return utils.composeConnection(connection);
    }

}
