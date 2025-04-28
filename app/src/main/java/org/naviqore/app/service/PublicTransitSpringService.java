package org.naviqore.app.service;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.naviqore.service.*;
import org.naviqore.service.config.ConnectionQueryConfig;
import org.naviqore.service.config.ServiceConfig;
import org.naviqore.service.exception.*;
import org.naviqore.utils.spatial.GeoCoordinate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class PublicTransitSpringService implements PublicTransitService {

    private final ServiceConfig config;
    private PublicTransitService delegate;

    @Autowired
    public PublicTransitSpringService(ServiceConfigParser parser) {
        log.info("Initializing public transit spring service");
        this.config = parser.getServiceConfig();
        this.delegate = createDelegate();
    }

    @Scheduled(cron = "${gtfs.static.update.cron}")
    public void updateStaticScheduleTask() {
        log.info("Updating public transit service with static GTFS");
        try {
            // no need to synchronize; the service operates on the old delegate until its reference is set to the new one
            delegate = createDelegate();
            log.info("Successfully updated public transit service with static GTFS");
        } catch (Exception e) {
            log.error("Failed to update public transit service", e);
        }
    }

    private PublicTransitService createDelegate() {
        try {
            return new PublicTransitServiceFactory(config).create();
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException("Failed to create public transit service", e);
        }
    }

    @Override
    public Validity getValidity() {
        return delegate.getValidity();
    }

    @Override
    public boolean hasAccessibilityInformation() {
        return delegate.hasAccessibilityInformation();
    }

    @Override
    public boolean hasBikeInformation() {
        return delegate.hasBikeInformation();
    }

    @Override
    public boolean hasTravelModeInformation() {
        return delegate.hasTravelModeInformation();
    }

    @Override
    public List<Stop> getStops(String like, SearchType searchType) {
        return delegate.getStops(like, searchType);
    }

    @Override
    public Optional<Stop> getNearestStop(GeoCoordinate location) {
        return delegate.getNearestStop(location);
    }

    @Override
    public List<Stop> getNearestStops(GeoCoordinate location, int radius, int limit) {
        return delegate.getNearestStops(location, radius, limit);
    }

    @Override
    public List<StopTime> getNextDepartures(Stop stop, LocalDateTime from, @Nullable LocalDateTime until, int limit) {
        return delegate.getNextDepartures(stop, from, until, limit);
    }

    @Override
    public RoutingFeatures getRoutingFeatures() {
        return delegate.getRoutingFeatures();
    }

    @Override
    public List<Connection> getConnections(GeoCoordinate source, GeoCoordinate target, LocalDateTime time,
                                           TimeType timeType,
                                           ConnectionQueryConfig config) throws ConnectionRoutingException {
        return delegate.getConnections(source, target, time, timeType, config);
    }

    @Override
    public List<Connection> getConnections(Stop source, Stop target, LocalDateTime time, TimeType timeType,
                                           ConnectionQueryConfig config) throws ConnectionRoutingException {
        return delegate.getConnections(source, target, time, timeType, config);
    }

    @Override
    public List<Connection> getConnections(Stop source, GeoCoordinate target, LocalDateTime time, TimeType timeType,
                                           ConnectionQueryConfig config) throws ConnectionRoutingException {
        return delegate.getConnections(source, target, time, timeType, config);
    }

    @Override
    public List<Connection> getConnections(GeoCoordinate source, Stop target, LocalDateTime time, TimeType timeType,
                                           ConnectionQueryConfig config) throws ConnectionRoutingException {
        return delegate.getConnections(source, target, time, timeType, config);
    }

    @Override
    public Map<Stop, Connection> getIsolines(GeoCoordinate source, LocalDateTime time, TimeType timeType,
                                             ConnectionQueryConfig config) throws ConnectionRoutingException {
        return delegate.getIsolines(source, time, timeType, config);
    }

    @Override
    public Map<Stop, Connection> getIsolines(Stop source, LocalDateTime time, TimeType timeType,
                                             ConnectionQueryConfig config) throws ConnectionRoutingException {
        return delegate.getIsolines(source, time, timeType, config);
    }

    @Override
    public Stop getStopById(String stopId) throws StopNotFoundException {
        return delegate.getStopById(stopId);
    }

    @Override
    public Trip getTripById(String tripId, LocalDate date) throws TripNotFoundException, TripNotActiveException {
        return delegate.getTripById(tripId, date);
    }

    @Override
    public Route getRouteById(String routeId) throws RouteNotFoundException {
        return delegate.getRouteById(routeId);
    }

}