package org.naviqore.service.gtfs.raptor.routing;

import org.naviqore.raptor.RaptorAlgorithm;
import org.naviqore.service.Connection;
import org.naviqore.service.Stop;
import org.naviqore.service.TimeType;
import org.naviqore.service.config.ConnectionQueryConfig;
import org.naviqore.service.exception.ConnectionRoutingException;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * An isoline query from a transit stop.
 */
class IsolineStopSource extends IsolineQueryTemplate<Stop> {

    IsolineStopSource(OffsetDateTime time, TimeType timeType, ConnectionQueryConfig queryConfig,
                      RoutingQueryUtils utils, Stop source) {
        super(time, timeType, queryConfig, utils, source, true);
    }

    @Override
    protected Map<String, OffsetDateTime> prepareSourceStops(Stop source) {
        return utils.getAllChildStopsFromStop(source, time);
    }

    @Override
    protected Map<Stop, Connection> handleInvalidStopException(RaptorAlgorithm.InvalidStopException exception,
                                                               Stop source) throws ConnectionRoutingException {
        return new IsolineGeoSource(time, timeType, queryConfig, utils, source.getCoordinate()).run();
    }

    @Override
    protected Connection postprocessDepartureConnection(Stop source, org.naviqore.raptor.Connection connection) {
        return utils.composeConnection(connection);
    }

    @Override
    protected Connection postprocessArrivalConnection(Stop source, org.naviqore.raptor.Connection connection) {
        return utils.composeConnection(connection);
    }

}
