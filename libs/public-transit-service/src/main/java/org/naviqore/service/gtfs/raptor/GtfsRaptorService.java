package org.naviqore.service.gtfs.raptor;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.naviqore.raptor.RaptorAlgorithm;
import org.naviqore.service.*;
import org.naviqore.service.config.ConnectionQueryConfig;
import org.naviqore.service.config.ServiceConfig;
import org.naviqore.service.exception.*;
import org.naviqore.service.gtfs.raptor.routing.RoutingQueryFacade;
import org.naviqore.service.spi.ScheduledTrip;
import org.naviqore.service.spi.TransitSchedule;
import org.naviqore.service.walk.WalkCalculator;
import org.naviqore.utils.search.SearchIndex;
import org.naviqore.utils.search.SearchIndexBuilder;
import org.naviqore.utils.spatial.GeoCoordinate;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * Public transit service on top of a {@link TransitSchedule} and a RAPTOR router built from the same schedule.
 */
@Slf4j
public class GtfsRaptorService implements PublicTransitService {

    private final TransitSchedule schedule;
    @Getter
    private final Validity validity;
    private final SearchIndex<Stop> stopSearchIndex;
    private final List<Stop> stops;

    private final GtfsStopScopeResolver resolver;
    private final RoutingQueryFacade routing;

    public GtfsRaptorService(ServiceConfig serviceConfig, TransitSchedule schedule, RaptorAlgorithm raptor) {
        this(serviceConfig, schedule, GtfsRaptorServiceInitializer.initializeWalkCalculator(serviceConfig), raptor);
    }

    GtfsRaptorService(ServiceConfig serviceConfig, TransitSchedule schedule, WalkCalculator walkCalculator,
                      RaptorAlgorithm raptor) {
        this.schedule = schedule;
        this.stopSearchIndex = createStopSearchIndex(schedule);
        // the schedule is immutable after initialization, so the candidates for random selection can be derived once;
        // they are sorted by id since the iteration order of the immutable schedule map varies between JVM runs, which
        // would otherwise make the selection non-reproducible even for a seeded random instance
        this.stops = schedule.getStops().stream().sorted(Comparator.comparing(Stop::getId)).toList();

        this.validity = schedule.getValidity();
        this.resolver = new GtfsStopScopeResolver(schedule, serviceConfig.getWalkSearchRadius());
        this.routing = new RoutingQueryFacade(serviceConfig, schedule, walkCalculator, raptor);
    }

    private static SearchIndex<Stop> createStopSearchIndex(TransitSchedule schedule) {
        SearchIndexBuilder<Stop> builder = SearchIndex.builder();

        // only add parent stops and stops without a parent
        for (Stop stop : schedule.getStops()) {
            if (schedule.getParent(stop.getId()).isEmpty()) {
                builder.add(stop.getName().toLowerCase(), stop);
            }
        }

        return builder.build();
    }

    @Override
    public boolean hasAccessibilityInformation() {
        return schedule.hasAccessibilityInformation();
    }

    @Override
    public boolean hasBikeInformation() {
        return schedule.hasBikeInformation();
    }

    @Override
    public boolean hasTravelModeInformation() {
        // GTFS requires routes to have a mode, so this is always true
        return true;
    }

    @Override
    public List<Stop> getStops(String like, SearchType searchType, StopSortStrategy stopSortStrategy) {
        log.info("Searching for stops matching '{}', with search type '{}' and sort strategy '{}'", like, searchType,
                stopSortStrategy);

        return stopSearchIndex.search(like.toLowerCase(), TypeMapper.map(searchType))
                .stream()
                .sorted(stopSortStrategy.getComparator(like, Stop::getName))
                .toList();
    }

    @Override
    public Optional<Stop> getNearestStop(GeoCoordinate location) {
        log.info("Searching for nearest stop to location '({}, {})'", location.getFirstComponent(),
                location.getSecondComponent());

        Stop stop = schedule.getNearestStop(location);

        // if nearest stop, which could be null, is a child stop, return parent stop
        Optional<Stop> parent = stop == null ? Optional.empty() : schedule.getParent(stop.getId());
        if (parent.isPresent() && !parent.get().equals(stop)) {
            stop = parent.get();
        }

        return Optional.ofNullable(stop);
    }

    @Override
    public List<Stop> getNearestStops(GeoCoordinate location, int radius) {
        log.info("Searching for nearest stops to location '({}, {})', within radius '{} m'",
                location.getFirstComponent(), location.getSecondComponent(), radius);

        List<Stop> stops = new ArrayList<>();

        for (Stop stop : schedule.getStopsWithinRadius(location, radius)) {
            // if nearest stop is a child stop, return parent stop
            Optional<Stop> parent = schedule.getParent(stop.getId());
            if (parent.isPresent() && !parent.get().equals(stop)) {
                stop = parent.get();
            }
            // avoid adding same parent stop multiple times
            if (!stops.contains(stop)) {
                stops.add(stop);
            }

        }

        return stops;
    }

    @Override
    public List<StopTime> getStopTimes(Stop stop, OffsetDateTime from, OffsetDateTime until, TimeType timeType,
                                       StopScope scope) {
        log.info("Querying {} for stop '{}' with scope '{}', from '{}', until '{}'",
                timeType == TimeType.DEPARTURE ? "departures" : "arrivals", stop.getId(), scope, from, until);

        return resolver.resolve(stop, scope)
                .stream()
                .flatMap(id -> schedule.getStopTimes(id, from, until, timeType).stream())
                .map(stopTime -> TypeMapper.map(stopTime, from.toLocalDate(), schedule))
                .sorted(switch (timeType) {
                    case DEPARTURE -> Comparator.comparing(StopTime::getDepartureTime);
                    case ARRIVAL -> Comparator.comparing(StopTime::getArrivalTime);
                })
                .toList();
    }

    @Override
    public Stop getRandomStop(Random random) {
        return stops.get(random.nextInt(stops.size()));
    }

    @Override
    public Stop getStopById(String stopId) throws StopNotFoundException {
        Stop stop = schedule.getStop(stopId);

        if (stop == null) {
            throw new StopNotFoundException(stopId);
        }

        return stop;
    }

    @Override
    public Trip getTripById(String tripId, LocalDate date) throws TripNotFoundException, TripNotActiveException {
        ScheduledTrip trip = schedule.getTrip(tripId);

        if (trip == null) {
            throw new TripNotFoundException(tripId);
        }

        if (!schedule.isTripActive(tripId, date)) {
            throw new TripNotActiveException(tripId, date);
        }

        return TypeMapper.map(trip, date, schedule);
    }

    @Override
    public Route getRouteById(String routeId) throws RouteNotFoundException {
        Route route = schedule.getRoute(routeId);

        if (route == null) {
            throw new RouteNotFoundException(routeId);
        }

        return route;
    }

    @Override
    public RoutingFeatures getRoutingFeatures() {
        return new RoutingFeatures(true, true, true, true, schedule.hasAccessibilityInformation(),
                schedule.hasBikeInformation(), true);
    }

    @Override
    public List<Connection> getConnections(Stop source, Stop target, OffsetDateTime time, TimeType timeType,
                                           ConnectionQueryConfig config) throws ConnectionRoutingException {
        log.info("Routing connections from stop '{}' to stop '{}', time '{}', time type '{}'", source.getId(),
                target.getId(), time, timeType);

        return routing.queryConnections(time, timeType, config, source, target);
    }

    @Override
    public List<Connection> getConnections(GeoCoordinate source, GeoCoordinate target, OffsetDateTime time,
                                           TimeType timeType,
                                           ConnectionQueryConfig config) throws ConnectionRoutingException {
        log.info("Routing connections from location '({}, {})' to location '({}, {})', time '{}', time type '{}'",
                source.getFirstComponent(), source.getSecondComponent(), target.getFirstComponent(),
                target.getSecondComponent(), time, timeType);

        return routing.queryConnections(time, timeType, config, source, target);
    }

    @Override
    public List<Connection> getConnections(Stop source, GeoCoordinate target, OffsetDateTime time, TimeType timeType,
                                           ConnectionQueryConfig config) throws ConnectionRoutingException {
        log.info("Routing connections from stop '{}' to location '({}, {})', time '{}', time type '{}'", source.getId(),
                target.getFirstComponent(), target.getSecondComponent(), time, timeType);

        return routing.queryConnections(time, timeType, config, source, target);
    }

    @Override
    public List<Connection> getConnections(GeoCoordinate source, Stop target, OffsetDateTime time, TimeType timeType,
                                           ConnectionQueryConfig config) throws ConnectionRoutingException {
        log.info("Routing connections from location '({}, {})' to stop '{}', time '{}', time type '{}'",
                source.getFirstComponent(), source.getSecondComponent(), target.getId(), time, timeType);

        return routing.queryConnections(time, timeType, config, source, target);
    }

    @Override
    public Map<Stop, Connection> getIsolines(GeoCoordinate source, OffsetDateTime time, TimeType timeType,
                                             ConnectionQueryConfig config) throws ConnectionRoutingException {
        log.info("Routing isolines from location '({}, {})', time '{}', time type '{}'", source.getFirstComponent(),
                source.getSecondComponent(), time, timeType);

        return routing.queryIsolines(time, timeType, config, source);
    }

    @Override
    public Map<Stop, Connection> getIsolines(Stop source, OffsetDateTime time, TimeType timeType,
                                             ConnectionQueryConfig config) throws ConnectionRoutingException {
        log.info("Routing isolines from stop '{}', time '{}', time type '{}'", source.getId(), time, timeType);

        return routing.queryIsolines(time, timeType, config, source);
    }

}
