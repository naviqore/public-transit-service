package ch.naviqore.service.gtfs.raptor;

import ch.naviqore.gtfs.schedule.model.GtfsSchedule;
import ch.naviqore.raptor.RaptorAlgorithm;
import ch.naviqore.raptor.router.RaptorRouter;
import ch.naviqore.service.*;
import ch.naviqore.service.config.ConnectionQueryConfig;
import ch.naviqore.service.config.ServiceConfig;
import ch.naviqore.service.exception.*;
import ch.naviqore.service.walk.WalkCalculator;
import ch.naviqore.utils.search.SearchIndex;
import ch.naviqore.utils.spatial.GeoCoordinate;
import ch.naviqore.utils.spatial.index.KDTree;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class GtfsRaptorService implements PublicTransitService {

    private final ServiceConfig config;
    private final GtfsSchedule schedule;
    @Getter
    private final Validity validity;
    private final KDTree<ch.naviqore.gtfs.schedule.model.Stop> spatialStopIndex;
    private final SearchIndex<ch.naviqore.gtfs.schedule.model.Stop> stopSearchIndex;
    private final WalkCalculator walkCalculator;
    private final RaptorRouter raptorRouter;

    GtfsRaptorService(ServiceConfig config, GtfsSchedule schedule,
                      KDTree<ch.naviqore.gtfs.schedule.model.Stop> spatialStopIndex,
                      SearchIndex<ch.naviqore.gtfs.schedule.model.Stop> stopSearchIndex, WalkCalculator walkCalculator,
                      RaptorRouter raptorRouter) {
        this.config = config;
        this.schedule = schedule;
        this.validity = new GtfsRaptorValidity(schedule);
        this.spatialStopIndex = spatialStopIndex;
        this.stopSearchIndex = stopSearchIndex;
        this.walkCalculator = walkCalculator;
        this.raptorRouter = raptorRouter;
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
        log.debug("Get nearest stop to {}", location);
        ch.naviqore.gtfs.schedule.model.Stop stop = spatialStopIndex.nearestNeighbour(location);

        // if nearest stop, which could be null, is a child stop, return parent stop
        if (stop != null && stop.getParent().isPresent() && !stop.getParent().get().equals(stop)) {
            stop = stop.getParent().get();
        }

        return Optional.ofNullable(TypeMapper.map(stop));
    }

    @Override
    public List<Stop> getNearestStops(GeoCoordinate location, int radius, int limit) {
        log.debug("Get nearest {} stops to {} in radius {}", limit, location, radius);

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
        List<String> stopIds = getAllStopIdsForStop(stop);
        return stopIds.stream()
                .flatMap(stopId -> schedule.getNextDepartures(stopId, from, limit).stream())
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

    // returns all stops to be included in search
    private List<String> getAllStopIdsForStop(Stop stop) {
        ch.naviqore.gtfs.schedule.model.Stop scheduleStop = schedule.getStops().get(stop.getId());

        if (scheduleStop.getChildren().isEmpty()) {
            // child stop; return itself
            return List.of(stop.getId());
        } else {
            // parent stop; return all children and itself (departures on parent are possible)
            List<String> stopIds = new ArrayList<>();
            stopIds.add(scheduleStop.getId());
            scheduleStop.getChildren().forEach(child -> stopIds.add(child.getId()));

            return stopIds;
        }
    }

    @Override
    public RoutingFeatures getRoutingFeatures() {
        return new RoutingFeatures(true, true, true, true, schedule.hasTripAccessibilityInformation(),
                schedule.hasTripBikeInformation(), true);
    }

    @Override
    public List<Connection> getConnections(Stop source, Stop target, LocalDateTime time, TimeType timeType,
                                           ConnectionQueryConfig config) throws ConnectionRoutingException {
        return getConnections(schedule.getStops().get(source.getId()), null, schedule.getStops().get(target.getId()),
                null, time, timeType, config);
    }

    @Override
    public List<Connection> getConnections(GeoCoordinate source, GeoCoordinate target, LocalDateTime time,
                                           TimeType timeType,
                                           ConnectionQueryConfig config) throws ConnectionRoutingException {
        return getConnections(null, source, null, target, time, timeType, config);
    }

    @Override
    public List<Connection> getConnections(Stop source, GeoCoordinate target, LocalDateTime time, TimeType timeType,
                                           ConnectionQueryConfig config) throws ConnectionRoutingException {
        return getConnections(schedule.getStops().get(source.getId()), null, null, target, time, timeType, config);
    }

    @Override
    public List<Connection> getConnections(GeoCoordinate source, Stop target, LocalDateTime time, TimeType timeType,
                                           ConnectionQueryConfig config) throws ConnectionRoutingException {
        return getConnections(null, source, schedule.getStops().get(target.getId()), null, time, timeType, config);
    }

    private List<Connection> getConnections(@Nullable ch.naviqore.gtfs.schedule.model.Stop departureStop,
                                            @Nullable GeoCoordinate departureLocation,
                                            @Nullable ch.naviqore.gtfs.schedule.model.Stop arrivalStop,
                                            @Nullable GeoCoordinate arrivalLocation, LocalDateTime time,
                                            TimeType timeType,
                                            ConnectionQueryConfig config) throws ConnectionRoutingException {

        boolean isDeparture = timeType == TimeType.DEPARTURE;
        ch.naviqore.gtfs.schedule.model.Stop sourceStop = isDeparture ? departureStop : arrivalStop;
        GeoCoordinate sourceLocation = isDeparture ? departureLocation : arrivalLocation;
        ch.naviqore.gtfs.schedule.model.Stop targetStop = isDeparture ? arrivalStop : departureStop;
        GeoCoordinate targetLocation = isDeparture ? arrivalLocation : departureLocation;

        Map<String, LocalDateTime> sourceStops;
        Map<String, Integer> targetStops;

        if (sourceStop != null) {
            sourceStops = getAllChildStopsFromStop(TypeMapper.map(sourceStop), time);
        } else if (sourceLocation != null) {
            sourceStops = getStopsWithWalkTimeFromLocation(sourceLocation, time, config.getMaximumWalkingDuration(),
                    timeType);
        } else {
            throw new IllegalArgumentException("Either sourceStop or sourceLocation must be provided.");
        }

        if (targetStop != null) {
            targetStops = getAllChildStopsFromStop(TypeMapper.map(targetStop));
        } else if (targetLocation != null) {
            targetStops = getStopsWithWalkTimeFromLocation(targetLocation, config.getMaximumWalkingDuration());
        } else {
            throw new IllegalArgumentException("Either targetStop or targetLocation must be provided.");
        }

        // no source stop or target stop is within walkable distance, and therefore no connections are available
        if (sourceStops.isEmpty() || targetStops.isEmpty()) {
            return List.of();
        }

        // query connection from raptor
        List<ch.naviqore.raptor.Connection> connections;
        try {
            if (isDeparture) {
                connections = raptorRouter.routeEarliestArrival(sourceStops, targetStops, TypeMapper.map(config));
            } else {
                connections = raptorRouter.routeLatestDeparture(targetStops, sourceStops, TypeMapper.map(config));
            }
        } catch (RaptorAlgorithm.InvalidStopException e) {
            // TODO: try location based routing instead
            log.debug("{}: {}", e.getClass().getSimpleName(), e.getMessage());
            return List.of();
        } catch (IllegalArgumentException e) {
            throw new ConnectionRoutingException(e);
        }

        // assemble connection results
        List<Connection> result = new ArrayList<>();

        for (ch.naviqore.raptor.Connection connection : connections) {
            Walk firstMile = null;
            Walk lastMile = null;

            if (sourceStop == null) {
                LocalDateTime departureTime = connection.getDepartureTime();
                firstMile = getFirstWalk(sourceLocation, connection.getFromStopId(), departureTime);
            }
            if (targetStop == null) {
                LocalDateTime arrivalTime = connection.getArrivalTime();
                lastMile = getLastWalk(targetLocation, connection.getToStopId(), arrivalTime);
            }

            Connection serviceConnection = TypeMapper.map(connection, firstMile, lastMile, schedule);

            // Filter needed because the raptor algorithm does not consider the firstMile and lastMile walk time
            if (Duration.between(serviceConnection.getDepartureTime(), serviceConnection.getArrivalTime())
                    .getSeconds() <= config.getMaximumTravelTime()) {
                result.add(serviceConnection);
            }
        }

        return result;
    }

    private Map<String, LocalDateTime> getStopsWithWalkTimeFromLocation(GeoCoordinate location, LocalDateTime startTime,
                                                                        int maxWalkDuration, TimeType timeType) {
        Map<String, Integer> stopsWithWalkTime = getStopsWithWalkTimeFromLocation(location, maxWalkDuration);
        return stopsWithWalkTime.entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        entry -> timeType == TimeType.DEPARTURE ? startTime.plusSeconds(
                                entry.getValue()) : startTime.minusSeconds(entry.getValue())));

    }

    private Map<String, Integer> getStopsWithWalkTimeFromLocation(GeoCoordinate location, int maxWalkDuration) {
        List<ch.naviqore.gtfs.schedule.model.Stop> nearestStops = new ArrayList<>(
                spatialStopIndex.rangeSearch(location, config.getWalkingSearchRadius()));

        if (nearestStops.isEmpty()) {
            nearestStops.add(spatialStopIndex.nearestNeighbour(location));
        }

        Map<String, Integer> stopsWithWalkTime = new HashMap<>();
        for (ch.naviqore.gtfs.schedule.model.Stop stop : nearestStops) {
            int walkDuration = walkCalculator.calculateWalk(location, stop.getCoordinate()).duration();
            if (walkDuration <= maxWalkDuration) {
                stopsWithWalkTime.put(stop.getId(), walkDuration);
            }
        }

        return stopsWithWalkTime;
    }

    private Map<String, LocalDateTime> getAllChildStopsFromStop(Stop stop, LocalDateTime time) {
        List<String> stopIds = getAllStopIdsForStop(stop);
        Map<String, LocalDateTime> stopWithDateTime = new HashMap<>();
        for (String stopId : stopIds) {
            stopWithDateTime.put(stopId, time);
        }

        return stopWithDateTime;
    }

    private Map<String, Integer> getAllChildStopsFromStop(Stop stop) {
        List<String> stopIds = getAllStopIdsForStop(stop);
        Map<String, Integer> stopsWithWalkTime = new HashMap<>();
        for (String stopId : stopIds) {
            stopsWithWalkTime.put(stopId, 0);
        }

        return stopsWithWalkTime;
    }

    @Override
    public Map<Stop, Connection> getIsolines(GeoCoordinate source, LocalDateTime time, TimeType timeType,
                                             ConnectionQueryConfig config) throws ConnectionRoutingException {
        Map<String, LocalDateTime> sourceStops = getStopsWithWalkTimeFromLocation(source, time,
                config.getMaximumWalkingDuration(), timeType);

        // no source stop is within walkable distance, and therefore no isolines are available
        if (sourceStops.isEmpty()) {
            return Map.of();
        }

        try {
            return mapToStopConnectionMap(
                    raptorRouter.routeIsolines(sourceStops, TypeMapper.map(timeType), TypeMapper.map(config)), source,
                    config, timeType);
        } catch (RaptorAlgorithm.InvalidStopException e) {
            log.debug("{}: {}", e.getClass().getSimpleName(), e.getMessage());
            return Map.of();
        } catch (IllegalArgumentException e) {
            throw new ConnectionRoutingException(e);
        }
    }

    @Override
    public Map<Stop, Connection> getIsolines(Stop source, LocalDateTime time, TimeType timeType,
                                             ConnectionQueryConfig config) throws ConnectionRoutingException {
        Map<String, LocalDateTime> sourceStops = getAllChildStopsFromStop(source, time);

        try {
            return mapToStopConnectionMap(
                    raptorRouter.routeIsolines(sourceStops, TypeMapper.map(timeType), TypeMapper.map(config)), null,
                    config, timeType);
        } catch (RaptorAlgorithm.InvalidStopException e) {
            // TODO: Try location based iso line routing?
            log.debug("{}: {}", e.getClass().getSimpleName(), e.getMessage());
            return Map.of();
        } catch (IllegalArgumentException e) {
            throw new ConnectionRoutingException(e);
        }
    }

    private Map<Stop, Connection> mapToStopConnectionMap(Map<String, ch.naviqore.raptor.Connection> isoLines,
                                                         @Nullable GeoCoordinate source, ConnectionQueryConfig config,
                                                         TimeType timeType) {
        Map<Stop, Connection> result = new HashMap<>();

        for (Map.Entry<String, ch.naviqore.raptor.Connection> entry : isoLines.entrySet()) {
            ch.naviqore.raptor.Connection connection = entry.getValue();

            Walk firstMile = null;
            Walk lastMile = null;
            if (timeType == TimeType.DEPARTURE && source != null) {
                firstMile = getFirstWalk(source, connection.getFromStopId(), connection.getDepartureTime());
            } else if (timeType == TimeType.ARRIVAL && source != null) {
                lastMile = getLastWalk(source, connection.getFromStopId(), connection.getDepartureTime());
            }

            Stop stop = TypeMapper.map(schedule.getStops().get(entry.getKey()));
            Connection serviceConnection = TypeMapper.map(connection, firstMile, lastMile, schedule);

            // The raptor algorithm does not consider the firstMile walk time, so we need to filter out connections
            // that exceed the maximum travel time here
            if (Duration.between(serviceConnection.getArrivalTime(), serviceConnection.getDepartureTime())
                    .getSeconds() <= config.getMaximumTravelTime()) {
                result.put(stop, serviceConnection);
            }
        }

        return result;
    }

    private @Nullable Walk getFirstWalk(GeoCoordinate source, String firstStopId, LocalDateTime departureTime) {
        ch.naviqore.gtfs.schedule.model.Stop firstStop = schedule.getStops().get(firstStopId);
        WalkCalculator.Walk firstWalk = walkCalculator.calculateWalk(source, firstStop.getCoordinate());
        int firstWalkDuration = firstWalk.duration() + config.getTransferTimeAccessEgress();

        if (firstWalkDuration > config.getWalkingDurationMinimum()) {
            return TypeMapper.createWalk(firstWalk.distance(), firstWalkDuration, WalkType.FIRST_MILE,
                    departureTime.minusSeconds(firstWalkDuration), departureTime, source, firstStop.getCoordinate(),
                    TypeMapper.map(firstStop));
        }

        return null;
    }

    private @Nullable Walk getLastWalk(GeoCoordinate target, String lastStopId, LocalDateTime arrivalTime) {
        ch.naviqore.gtfs.schedule.model.Stop lastStop = schedule.getStops().get(lastStopId);
        WalkCalculator.Walk lastWalk = walkCalculator.calculateWalk(target, lastStop.getCoordinate());
        int lastWalkDuration = lastWalk.duration() + config.getTransferTimeAccessEgress();

        if (lastWalkDuration > config.getWalkingDurationMinimum()) {
            return TypeMapper.createWalk(lastWalk.distance(), lastWalkDuration, WalkType.LAST_MILE, arrivalTime,
                    arrivalTime.plusSeconds(lastWalkDuration), lastStop.getCoordinate(), target,
                    TypeMapper.map(lastStop));
        }

        return null;
    }

}
