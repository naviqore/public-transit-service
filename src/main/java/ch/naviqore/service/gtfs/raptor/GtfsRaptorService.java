package ch.naviqore.service.gtfs.raptor;

import ch.naviqore.gtfs.schedule.model.GtfsSchedule;
import ch.naviqore.raptor.router.RaptorRouter;
import ch.naviqore.service.*;
import ch.naviqore.service.config.ConnectionQueryConfig;
import ch.naviqore.service.config.ServiceConfig;
import ch.naviqore.service.exception.*;
import ch.naviqore.service.gtfs.raptor.routing.RoutingQueryFacade;
import ch.naviqore.service.walk.WalkCalculator;
import ch.naviqore.utils.search.SearchIndex;
import ch.naviqore.utils.spatial.GeoCoordinate;
import ch.naviqore.utils.spatial.index.KDTree;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
public class GtfsRaptorService implements PublicTransitService {

    private final GtfsSchedule schedule;
    @Getter
    private final Validity validity;
    private final KDTree<ch.naviqore.gtfs.schedule.model.Stop> spatialStopIndex;
    private final SearchIndex<ch.naviqore.gtfs.schedule.model.Stop> stopSearchIndex;

    private final RoutingQueryFacade routing;

    GtfsRaptorService(ServiceConfig serviceConfig, GtfsSchedule schedule,
                      KDTree<ch.naviqore.gtfs.schedule.model.Stop> spatialStopIndex,
                      SearchIndex<ch.naviqore.gtfs.schedule.model.Stop> stopSearchIndex, WalkCalculator walkCalculator,
                      RaptorRouter raptorRouter) {
        this.schedule = schedule;
        this.spatialStopIndex = spatialStopIndex;
        this.stopSearchIndex = stopSearchIndex;

        this.validity = new GtfsRaptorValidity(schedule);
        this.routing = new RoutingQueryFacade(serviceConfig, schedule, spatialStopIndex, walkCalculator, raptorRouter);
    }

    @Override
    public boolean hasAccessibilityInformation() {
        return schedule.hasTripAccessibilityInformation();
    }

    @Override
    public boolean hasBikeInformation() {
        return schedule.hasTripBikeInformation();
    }

    @Override
    public boolean hasTravelModeInformation() {
        // GTFS requires routes to have a mode, so this is always true
        return true;
    }

    @Override
    public List<Stop> getStops(String like, SearchType searchType) {
        return stopSearchIndex.search(like.toLowerCase(), TypeMapper.map(searchType))
                .stream()
                .sorted(Comparator.comparing(ch.naviqore.gtfs.schedule.model.Stop::getName))
                .map(TypeMapper::map)
                .toList();
    }

    @Override
    public Optional<Stop> getNearestStop(GeoCoordinate location) {
        ch.naviqore.gtfs.schedule.model.Stop stop = spatialStopIndex.nearestNeighbour(location);

        // if nearest stop, which could be null, is a child stop, return parent stop
        if (stop != null && stop.getParent().isPresent() && !stop.getParent().get().equals(stop)) {
            stop = stop.getParent().get();
        }

        return Optional.ofNullable(TypeMapper.map(stop));
    }

    @Override
    public List<Stop> getNearestStops(GeoCoordinate location, int radius, int limit) {
        List<ch.naviqore.gtfs.schedule.model.Stop> stops = new ArrayList<>();

        for (ch.naviqore.gtfs.schedule.model.Stop stop : spatialStopIndex.rangeSearch(location, radius)) {
            // if nearest stop is a child stop, return parent stop
            if (stop.getParent().isPresent() && !stop.getParent().get().equals(stop)) {
                stop = stop.getParent().get();
            }
            // avoid adding same parent stop multiple times
            if (!stops.contains(stop)) {
                stops.add(stop);
            }

        }

        return stops.stream().map(TypeMapper::map).limit(limit).toList();
    }

    @Override
    public List<StopTime> getNextDepartures(Stop stop, LocalDateTime from, @Nullable LocalDateTime until, int limit) {
        return schedule.getRelatedStops(stop.getId())
                .stream()
                .flatMap(scheduleStop -> schedule.getNextDepartures(scheduleStop.getId(), from, limit).stream())
                .map(stopTime -> TypeMapper.map(stopTime, from.toLocalDate()))
                .sorted(Comparator.comparing(StopTime::getDepartureTime))
                .filter(stopTime -> until == null || stopTime.getDepartureTime().isBefore(until))
                .limit(limit)
                .toList();
    }

    @Override
    public Stop getStopById(String stopId) throws StopNotFoundException {
        ch.naviqore.gtfs.schedule.model.Stop stop = schedule.getStops().get(stopId);

        if (stop == null) {
            throw new StopNotFoundException(stopId);
        }

        return TypeMapper.map(stop);
    }

    @Override
    public Trip getTripById(String tripId, LocalDate date) throws TripNotFoundException, TripNotActiveException {
        ch.naviqore.gtfs.schedule.model.Trip trip = schedule.getTrips().get(tripId);

        if (trip == null) {
            throw new TripNotFoundException(tripId);
        }

        if (!trip.getCalendar().isServiceAvailable(date)) {
            throw new TripNotActiveException(tripId, date);
        }

        return TypeMapper.map(trip, date);
    }

    @Override
    public Route getRouteById(String routeId) throws RouteNotFoundException {
        ch.naviqore.gtfs.schedule.model.Route route = schedule.getRoutes().get(routeId);

        if (route == null) {
            throw new RouteNotFoundException(routeId);
        }

        return TypeMapper.map(route);
    }

    @Override
    public RoutingFeatures getRoutingFeatures() {
        return new RoutingFeatures(true, true, true, true, schedule.hasTripAccessibilityInformation(),
                schedule.hasTripBikeInformation(), true);
    }

    @Override
    public List<Connection> getConnections(Stop source, Stop target, LocalDateTime time, TimeType timeType,
                                           ConnectionQueryConfig config) throws ConnectionRoutingException {
        return routing.queryConnections(time, timeType, config, source, target);
    }

    @Override
    public List<Connection> getConnections(GeoCoordinate source, GeoCoordinate target, LocalDateTime time,
                                           TimeType timeType,
                                           ConnectionQueryConfig config) throws ConnectionRoutingException {
        return routing.queryConnections(time, timeType, config, source, target);
    }

    @Override
    public List<Connection> getConnections(Stop source, GeoCoordinate target, LocalDateTime time, TimeType timeType,
                                           ConnectionQueryConfig config) throws ConnectionRoutingException {
        return routing.queryConnections(time, timeType, config, source, target);
    }

    @Override
    public List<Connection> getConnections(GeoCoordinate source, Stop target, LocalDateTime time, TimeType timeType,
                                           ConnectionQueryConfig config) throws ConnectionRoutingException {
        return routing.queryConnections(time, timeType, config, source, target);
    }

    @Override
    public Map<Stop, Connection> getIsolines(GeoCoordinate source, LocalDateTime time, TimeType timeType,
                                             ConnectionQueryConfig config) throws ConnectionRoutingException {
        return routing.queryIsolines(time, timeType, config, source);
    }

    @Override
    public Map<Stop, Connection> getIsolines(Stop source, LocalDateTime time, TimeType timeType,
                                             ConnectionQueryConfig config) throws ConnectionRoutingException {
        return routing.queryIsolines(time, timeType, config, source);
    }

}
