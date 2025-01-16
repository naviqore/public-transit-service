package ch.naviqore.service.gtfs.raptor.routing;

import ch.naviqore.gtfs.schedule.model.GtfsSchedule;
import ch.naviqore.raptor.RaptorAlgorithm;
import ch.naviqore.service.Connection;
import ch.naviqore.service.Stop;
import ch.naviqore.service.TimeType;
import ch.naviqore.service.config.ConnectionQueryConfig;
import ch.naviqore.service.config.ServiceConfig;
import ch.naviqore.service.exception.ConnectionRoutingException;
import ch.naviqore.service.walk.WalkCalculator;
import ch.naviqore.utils.spatial.GeoCoordinate;
import ch.naviqore.utils.spatial.index.KDTree;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Facade to simplify the execution of different routing queries for connections (stop-to-stop, geo-to-geo, stop-to-geo
 * and geo-to-stop) and isolines (stop-source and geo-source). Hides the complexity of the preparation and execution of
 * the underlying RAPTOR routing calls and result construction for the service layer.
 */
// TODO: Add test cases for the manual schedule (GtfsToRaptorTestSchedule --> update coordinates to enable feasible walks).
//  Note: Tests are currently passing even if the swap logic is omitted.
//  - Test arrival/departure time types.
//  - Test connection cases.
//  - Test isoline cases.
public class RoutingQueryFacade {

    private final GtfsSchedule schedule;
    private final RoutingQueryUtils utils;

    public RoutingQueryFacade(ServiceConfig config, GtfsSchedule schedule,
                              KDTree<ch.naviqore.gtfs.schedule.model.Stop> spatialStopIndex,
                              WalkCalculator walkCalculator, RaptorAlgorithm raptor) {
        this.schedule = schedule;
        this.utils = new RoutingQueryUtils(config, schedule, spatialStopIndex, walkCalculator, raptor);
    }

    public List<Connection> queryConnections(LocalDateTime time, TimeType timeType, ConnectionQueryConfig queryConfig,
                                             Stop source, Stop target) throws ConnectionRoutingException {
        return new ConnectionStopToStop(time, timeType, queryConfig, utils, source, target).run();
    }

    public List<Connection> queryConnections(LocalDateTime time, TimeType timeType, ConnectionQueryConfig queryConfig,
                                             Stop source, GeoCoordinate target) throws ConnectionRoutingException {
        return new ConnectionStopToGeo(time, timeType, queryConfig, utils, source, target).run();
    }

    public List<Connection> queryConnections(LocalDateTime time, TimeType timeType, ConnectionQueryConfig queryConfig,
                                             GeoCoordinate source, Stop target) throws ConnectionRoutingException {
        return new ConnectionGeoToStop(time, timeType, queryConfig, utils, source, target).run();
    }

    public List<Connection> queryConnections(LocalDateTime time, TimeType timeType, ConnectionQueryConfig queryConfig,
                                             GeoCoordinate source,
                                             GeoCoordinate target) throws ConnectionRoutingException {
        return new ConnectionGeoToGeo(time, timeType, queryConfig, utils, source, target).run();
    }

    public Map<Stop, Connection> queryIsolines(LocalDateTime time, TimeType timeType, ConnectionQueryConfig queryConfig,
                                               Stop source) throws ConnectionRoutingException {
        return new IsolineStopSource(time, timeType, queryConfig, utils, schedule, source).run();
    }

    public Map<Stop, Connection> queryIsolines(LocalDateTime time, TimeType timeType, ConnectionQueryConfig queryConfig,
                                               GeoCoordinate source) throws ConnectionRoutingException {
        return new IsolineGeoSource(time, timeType, queryConfig, utils, schedule, source).run();
    }
}
