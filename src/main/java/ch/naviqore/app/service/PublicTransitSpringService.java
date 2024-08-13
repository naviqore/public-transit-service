package ch.naviqore.app.service;

import ch.naviqore.app.infrastructure.GtfsScheduleFile;
import ch.naviqore.app.infrastructure.GtfsScheduleUrl;
import ch.naviqore.service.*;
import ch.naviqore.service.config.ConnectionQueryConfig;
import ch.naviqore.service.config.ServiceConfig;
import ch.naviqore.service.exception.RouteNotFoundException;
import ch.naviqore.service.exception.StopNotFoundException;
import ch.naviqore.service.exception.TripNotActiveException;
import ch.naviqore.service.exception.TripNotFoundException;
import ch.naviqore.service.repo.GtfsScheduleRepository;
import ch.naviqore.utils.spatial.GeoCoordinate;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
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
        // no need to synchronize; the service operates on the old delegate until its reference is set to the new one
        delegate = createDelegate();
        log.info("Successfully updated public transit service with static GTFS");
    }

    private PublicTransitService createDelegate() {
        return new PublicTransitServiceFactory(config,
                InputValidator.getRepository(config.getGtfsStaticUri())).create();
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
    public List<Connection> getConnections(Stop source, GeoCoordinate target, LocalDateTime time, TimeType timeType,
                                           ConnectionQueryConfig config) {
        return delegate.getConnections(source, target, time, timeType, config);
    }

    @Override
    public List<Connection> getConnections(GeoCoordinate source, Stop target, LocalDateTime time, TimeType timeType,
                                           ConnectionQueryConfig config) {
        return delegate.getConnections(source, target, time, timeType, config);
    }

    @Override
    public Map<Stop, Connection> getIsoLines(GeoCoordinate source, LocalDateTime time, TimeType timeType,
                                             ConnectionQueryConfig config) {
        return delegate.getIsoLines(source, time, timeType, config);
    }

    @Override
    public Map<Stop, Connection> getIsoLines(Stop source, LocalDateTime time, TimeType timeType,
                                             ConnectionQueryConfig config) {
        return delegate.getIsoLines(source, time, timeType, config);
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

    private static class InputValidator {

        private static final List<String> ALLOWED_SCHEMES = Arrays.asList("http", "https");

        private static GtfsScheduleRepository getRepository(String gtfsStaticUrl) {
            if (isLocalFile(gtfsStaticUrl)) {
                return new GtfsScheduleFile(gtfsStaticUrl);
            } else if (isValidUrl(gtfsStaticUrl)) {
                return new GtfsScheduleUrl(gtfsStaticUrl);
            } else {
                throw new IllegalArgumentException("Invalid GTFS static URI value: " + gtfsStaticUrl);
            }
        }

        private static boolean isLocalFile(String path) {
            File file = new File(path);
            return file.exists() && file.isFile();
        }

        private static boolean isValidUrl(String urlString) {
            try {
                URI uri = new URI(urlString);
                String scheme = uri.getScheme();
                return scheme != null && ALLOWED_SCHEMES.contains(scheme);
            } catch (URISyntaxException e) {
                return false;
            }
        }

    }

}