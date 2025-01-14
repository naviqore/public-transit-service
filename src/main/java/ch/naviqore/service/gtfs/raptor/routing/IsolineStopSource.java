package ch.naviqore.service.gtfs.raptor.routing;

import ch.naviqore.gtfs.schedule.model.GtfsSchedule;
import ch.naviqore.service.Connection;
import ch.naviqore.service.Stop;
import ch.naviqore.service.TimeType;
import ch.naviqore.service.config.ConnectionQueryConfig;

import java.time.LocalDateTime;
import java.util.Map;

class IsolineStopSource extends IsolineQueryTemplate<Stop> {

    IsolineStopSource(LocalDateTime time, TimeType timeType, ConnectionQueryConfig queryConfig, RoutingQueryUtils utils,
                      GtfsSchedule schedule, Stop source) {
        super(time, timeType, queryConfig, utils, schedule, source);
    }

    @Override
    protected Map<String, LocalDateTime> prepareSourceStops(Stop source) {
        return utils.getAllChildStopsFromStop(source, time);
    }

    @Override
    protected Connection postprocessConnection(Stop source, ch.naviqore.raptor.Connection connection) {
        return utils.composeConnection(connection);
    }
}
