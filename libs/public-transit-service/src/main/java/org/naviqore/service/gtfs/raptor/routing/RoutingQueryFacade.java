package org.naviqore.service.gtfs.raptor.routing;

import org.naviqore.gtfs.schedule.model.GtfsSchedule;
import org.naviqore.raptor.RaptorAlgorithm;
import org.naviqore.service.Connection;
import org.naviqore.service.Stop;
import org.naviqore.service.TimeType;
import org.naviqore.service.config.ConnectionQueryConfig;
import org.naviqore.service.config.ServiceConfig;
import org.naviqore.service.exception.ConnectionRoutingException;
import org.naviqore.service.walk.WalkCalculator;
import org.naviqore.utils.spatial.GeoCoordinate;
import org.naviqore.utils.spatial.index.KDTree;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Facade to simplify the execution of different routing queries for connections (stop-to-stop, geo-to-geo, stop-to-geo
 * and geo-to-stop) and isolines (stop-source and geo-source). Hides the complexity of the preparation and execution of
 * the underlying RAPTOR routing calls and result construction for the service layer.
 */
public class RoutingQueryFacade {

    private final RoutingQueryUtils utils;

    public RoutingQueryFacade(ServiceConfig config, GtfsSchedule schedule,
                              KDTree<org.naviqore.gtfs.schedule.model.Stop> spatialStopIndex,
                              WalkCalculator walkCalculator, RaptorAlgorithm raptor) {
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
        return new IsolineStopSource(time, timeType, queryConfig, utils, source).run();
    }

    public Map<Stop, Connection> queryIsolines(LocalDateTime time, TimeType timeType, ConnectionQueryConfig queryConfig,
                                               GeoCoordinate source) throws ConnectionRoutingException {
        return new IsolineGeoSource(time, timeType, queryConfig, utils, source).run();
    }
}
