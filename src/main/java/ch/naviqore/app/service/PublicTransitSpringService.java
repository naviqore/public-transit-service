package ch.naviqore.app.service;

import ch.naviqore.service.*;
import ch.naviqore.service.config.ConnectionQueryConfig;
import ch.naviqore.service.exception.RouteNotFoundException;
import ch.naviqore.service.exception.StopNotFoundException;
import ch.naviqore.service.exception.TripNotActiveException;
import ch.naviqore.service.exception.TripNotFoundException;
import ch.naviqore.utils.spatial.GeoCoordinate;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Log4j2
public class PublicTransitSpringService implements PublicTransitService {

    private final PublicTransitService delegate;
    private final String gtfsUrl;

    public PublicTransitSpringService(@Value("${gtfs.static.url}") String gtfsUrl) {
        log.info("Initializing public transit spring service");
        this.delegate = new PublicTransitServiceFactory(gtfsUrl).create();
        this.gtfsUrl = gtfsUrl;
    }

    @Scheduled(fixedRateString = "${gtfs.static.update.interval.hours} * 3600000")
    public void updateStaticScheduleTask() {
        log.info("Updating static GTFS from: {}", gtfsUrl);
        updateStaticSchedule();
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
    public List<Connection> getConnections(GeoCoordinate source, GeoCoordinate target, LocalDateTime time,
                                           TimeType timeType, ConnectionQueryConfig config) {
        return delegate.getConnections(source, target, time, timeType, config);
    }

    @Override
    public List<Connection> getConnections(Stop source, Stop target, LocalDateTime time, TimeType timeType,
                                           ConnectionQueryConfig config) {
        return delegate.getConnections(source, target, time, timeType, config);
    }

    @Override
    public List<Connection> getConnections(Stop source, GeoCoordinate target, LocalDateTime time,
                                           TimeType timeType, ConnectionQueryConfig config) {
        return delegate.getConnections(source, target, time, timeType, config);
    }

    @Override
    public List<Connection> getConnections(GeoCoordinate source, Stop target, LocalDateTime time, TimeType timeType,
                                           ConnectionQueryConfig config) {
        return delegate.getConnections(source, target, time, timeType, config);
    }

    @Override
    public Map<Stop, Connection> getIsolines(GeoCoordinate source, LocalDateTime departureTime,
                                             ConnectionQueryConfig config) {
        return delegate.getIsolines(source, departureTime, config);
    }

    @Override
    public Map<Stop, Connection> getIsolines(Stop source, LocalDateTime departureTime, ConnectionQueryConfig config) {
        return delegate.getIsolines(source, departureTime, config);
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

    @Override
    public void updateStaticSchedule() {
        delegate.updateStaticSchedule();
    }

}
