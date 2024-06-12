package ch.naviqore.service.impl;

import ch.naviqore.gtfs.schedule.model.GtfsSchedule;
import ch.naviqore.gtfs.schedule.type.ServiceDayTime;
import ch.naviqore.raptor.Raptor;
import ch.naviqore.service.*;
import ch.naviqore.service.config.ConnectionQueryConfig;
import ch.naviqore.service.config.ServiceConfig;
import ch.naviqore.service.exception.RouteNotFoundException;
import ch.naviqore.service.exception.StopNotFoundException;
import ch.naviqore.service.exception.TripNotActiveException;
import ch.naviqore.service.exception.TripNotFoundException;
import ch.naviqore.service.impl.convert.GtfsToRaptorConverter;
import ch.naviqore.service.impl.transfer.TransferGenerator;
import ch.naviqore.service.walk.WalkCalculator;
import ch.naviqore.utils.cache.EvictionCache;
import ch.naviqore.utils.search.SearchIndex;
import ch.naviqore.utils.spatial.GeoCoordinate;
import ch.naviqore.utils.spatial.index.KDTree;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.NotImplementedException;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static ch.naviqore.service.impl.TypeMapper.createWalk;
import static ch.naviqore.service.impl.TypeMapper.map;

@Log4j2
public class PublicTransitServiceImpl implements PublicTransitService {

    private final ServiceConfig config;
    private final GtfsSchedule schedule;
    private final KDTree<ch.naviqore.gtfs.schedule.model.Stop> spatialStopIndex;
    private final SearchIndex<ch.naviqore.gtfs.schedule.model.Stop> stopSearchIndex;
    private final WalkCalculator walkCalculator;
    private final List<TransferGenerator.Transfer> additionalTransfers;
    private final RaptorCache cache;

    PublicTransitServiceImpl(ServiceConfig config, GtfsSchedule schedule,
                             KDTree<ch.naviqore.gtfs.schedule.model.Stop> spatialStopIndex,
                             SearchIndex<ch.naviqore.gtfs.schedule.model.Stop> stopSearchIndex,
                             WalkCalculator walkCalculator, List<TransferGenerator.Transfer> additionalTransfers) {
        this.config = config;
        this.schedule = schedule;
        this.spatialStopIndex = spatialStopIndex;
        this.stopSearchIndex = stopSearchIndex;
        this.walkCalculator = walkCalculator;
        this.additionalTransfers = List.copyOf(additionalTransfers);

        // initialize raptor instances cache
        cache = new RaptorCache(config.getCacheSize(),
                EvictionCache.Strategy.valueOf(config.getCacheEvictionStrategy().name()));
    }

    private static void notYetImplementedCheck(TimeType timeType) {
        if (timeType == TimeType.ARRIVAL) {
            // TODO: Implement in raptor
            throw new NotImplementedException();
        }
    }

    @Override
    public List<Stop> getStops(String like, SearchType searchType) {
        return stopSearchIndex.search(like.toLowerCase(), map(searchType)).stream().map(TypeMapper::map).toList();
    }

    @Override
    public Optional<Stop> getNearestStop(GeoCoordinate location) {
        log.debug("Get nearest stop to {}", location);
        ch.naviqore.gtfs.schedule.model.Stop stop = spatialStopIndex.nearestNeighbour(location);
        // if nearest stop, which could be null, is a child stop, return parent stop
        if (stop != null && stop.getParent().isPresent() && !stop.getParent().get().equals(stop)) {
            stop = stop.getParent().get();
        }

        return Optional.ofNullable(map(stop));
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
                .map(stopTime -> map(stopTime, from.toLocalDate()))
                .sorted(Comparator.comparing(StopTime::getDepartureTime))
                .filter(stopTime -> until == null || stopTime.getDepartureTime().isBefore(until))
                .limit(limit)
                .toList();
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
    public List<Connection> getConnections(Stop source, Stop target, LocalDateTime time, TimeType timeType,
                                           ConnectionQueryConfig config) {
        return getConnections(schedule.getStops().get(source.getId()), null, schedule.getStops().get(target.getId()),
                null, time, timeType, config);
    }

    @Override
    public List<Connection> getConnections(GeoCoordinate source, GeoCoordinate target, LocalDateTime time,
                                           TimeType timeType, ConnectionQueryConfig config) {
        return getConnections(null, source, null, target, time, timeType, config);
    }

    @Override
    public List<Connection> getConnections(Stop source, GeoCoordinate target, LocalDateTime time, TimeType timeType,
                                           ConnectionQueryConfig config) {
        return getConnections(schedule.getStops().get(source.getId()), null, null, target, time, timeType, config);
    }

    @Override
    public List<Connection> getConnections(GeoCoordinate source, Stop target, LocalDateTime time, TimeType timeType,
                                           ConnectionQueryConfig config) {
        return getConnections(null, source, schedule.getStops().get(target.getId()), null, time, timeType, config);
    }

    private List<Connection> getConnections(@Nullable ch.naviqore.gtfs.schedule.model.Stop sourceStop,
                                            @Nullable GeoCoordinate sourceLocation,
                                            @Nullable ch.naviqore.gtfs.schedule.model.Stop targetStop,
                                            @Nullable GeoCoordinate targetLocation, LocalDateTime time,
                                            TimeType timeType, ConnectionQueryConfig config) {
        notYetImplementedCheck(timeType);
        int departureTime = time.toLocalTime().toSecondOfDay();
        Map<String, Integer> sourceStops;
        Map<String, Integer> targetStops;

        if (sourceStop != null) {
            sourceStops = getAllChildStopsFromStop(map(sourceStop), departureTime);
        } else if (sourceLocation != null) {
            sourceStops = getStopsWithWalkTimeFromLocation(sourceLocation, departureTime,
                    config.getMaximumWalkingDuration());
        } else {
            throw new IllegalArgumentException("Either sourceStop or sourceLocation must be provided.");
        }

        if (targetStop != null) {
            targetStops = getAllChildStopsFromStop(map(targetStop));
        } else if (targetLocation != null) {
            targetStops = getStopsWithWalkTimeFromLocation(targetLocation, config.getMaximumWalkingDuration());
        } else {
            throw new IllegalArgumentException("Either targetStop or targetLocation must be provided.");
        }

        // In this case either no source stop or target stop is within walkable distance
        if (sourceStops.isEmpty() || targetStops.isEmpty()) {
            return List.of();
        }

        // query connection from raptor
        Raptor raptor = cache.getRaptor(time.toLocalDate());
        List<ch.naviqore.raptor.Connection> connections = raptor.routeEarliestArrival(sourceStops, targetStops,
                map(config));

        List<Connection> result = new ArrayList<>();

        for (ch.naviqore.raptor.Connection connection : connections) {
            Walk firstMile = null;
            Walk lastMile = null;

            if (sourceStop == null) {
                firstMile = getFirstWalk(sourceLocation, connection.getFromStopId(), time, sourceStops);
            }
            if (targetStop == null) {
                LocalDateTime arrivalTime = time.toLocalDate()
                        .atTime(new ServiceDayTime(connection.getArrivalTime()).toLocalTime());
                lastMile = getLastWalk(targetLocation, connection.getToStopId(), arrivalTime, targetStops);
            }

            Connection serviceConnection = map(connection, firstMile, lastMile, time.toLocalDate(), schedule);

            // Filter needed because the raptor algorithm does not consider the firstMile and lastMile walk time
            if (Duration.between(serviceConnection.getDepartureTime(), serviceConnection.getArrivalTime())
                    .getSeconds() <= config.getMaximumTravelTime()) {
                result.add(serviceConnection);
            }
        }

        return result;
    }

    public Map<String, Integer> getStopsWithWalkTimeFromLocation(GeoCoordinate location, int maxWalkDuration) {
        return getStopsWithWalkTimeFromLocation(location, 0, maxWalkDuration);
    }

    public Map<String, Integer> getStopsWithWalkTimeFromLocation(GeoCoordinate location, int startTimeInSeconds,
                                                                 int maxWalkDuration) {
        // TODO: Make configurable
        int maxSearchRadius = 500;
        List<ch.naviqore.gtfs.schedule.model.Stop> nearestStops = new ArrayList<>(
                spatialStopIndex.rangeSearch(location, maxSearchRadius));

        if (nearestStops.isEmpty()) {
            nearestStops.add(spatialStopIndex.nearestNeighbour(location));
        }

        Map<String, Integer> stopsWithWalkTime = new HashMap<>();
        for (ch.naviqore.gtfs.schedule.model.Stop stop : nearestStops) {
            int walkDuration = walkCalculator.calculateWalk(location, stop.getCoordinate()).duration();
            if (walkDuration <= maxWalkDuration) {
                stopsWithWalkTime.put(stop.getId(), startTimeInSeconds + walkDuration);
            }
        }
        return stopsWithWalkTime;
    }

    public Map<String, Integer> getAllChildStopsFromStop(Stop stop) {
        return getAllChildStopsFromStop(stop, 0);
    }

    public Map<String, Integer> getAllChildStopsFromStop(Stop stop, int startTimeInSeconds) {
        List<String> stopIds = getAllStopIdsForStop(stop);
        Map<String, Integer> stopsWithWalkTime = new HashMap<>();
        for (String stopId : stopIds) {
            stopsWithWalkTime.put(stopId, startTimeInSeconds);
        }
        return stopsWithWalkTime;
    }

    @Override
    public Map<Stop, Connection> getIsolines(GeoCoordinate source, LocalDateTime departureTime,
                                             ConnectionQueryConfig config) {
        Map<String, Integer> sourceStops = getStopsWithWalkTimeFromLocation(source,
                departureTime.toLocalTime().toSecondOfDay(), config.getMaximumWalkingDuration());

        Raptor raptor = cache.getRaptor(departureTime.toLocalDate());

        return mapToStopConnectionMap(raptor.getIsoLines(sourceStops, map(config)), sourceStops, source, departureTime,
                config);
    }

    @Override
    public Map<Stop, Connection> getIsolines(Stop source, LocalDateTime departureTime, ConnectionQueryConfig config) {
        Map<String, Integer> sourceStops = getAllChildStopsFromStop(source,
                departureTime.toLocalTime().toSecondOfDay());

        Raptor raptor = cache.getRaptor(departureTime.toLocalDate());

        return mapToStopConnectionMap(raptor.getIsoLines(sourceStops, map(config)), sourceStops, null, departureTime,
                config);
    }

    private Map<Stop, Connection> mapToStopConnectionMap(Map<String, ch.naviqore.raptor.Connection> isoLines,
                                                         Map<String, Integer> sourceStops,
                                                         @Nullable GeoCoordinate source, LocalDateTime departureTime,
                                                         ConnectionQueryConfig config) {
        Map<Stop, Connection> result = new HashMap<>();

        for (Map.Entry<String, ch.naviqore.raptor.Connection> entry : isoLines.entrySet()) {
            ch.naviqore.raptor.Connection connection = entry.getValue();
            Walk firstMile = null;

            if (source != null) {
                firstMile = getFirstWalk(source, connection.getFromStopId(), departureTime, sourceStops);
            }

            Stop stop = map(schedule.getStops().get(entry.getKey()));
            Connection serviceConnection = map(connection, firstMile, null, departureTime.toLocalDate(), schedule);

            // The raptor algorithm does not consider the firstMile walk time, so we need to filter out connections
            // that exceed the maximum travel time here
            if (Duration.between(serviceConnection.getArrivalTime(), serviceConnection.getDepartureTime())
                    .getSeconds() <= config.getMaximumTravelTime()) {
                result.put(stop, serviceConnection);
            }
        }

        return result;
    }

    private @Nullable Walk getFirstWalk(GeoCoordinate source, String firstStopId, LocalDateTime departureTime,
                                        Map<String, Integer> sourceStops) {
        ch.naviqore.gtfs.schedule.model.Stop firstStop = schedule.getStops().get(firstStopId);
        int firstWalkDuration = sourceStops.get(firstStopId) - departureTime.toLocalTime().toSecondOfDay();

        if (firstWalkDuration > config.getWalkingDurationMinimum()) {
            int distance = (int) source.distanceTo(firstStop.getCoordinate());
            return createWalk(distance, firstWalkDuration, WalkType.FIRST_MILE, departureTime,
                    departureTime.plusSeconds(firstWalkDuration), source, firstStop.getCoordinate(), map(firstStop));
        }
        return null;
    }

    private @Nullable Walk getLastWalk(GeoCoordinate target, String lastStopId, LocalDateTime arrivalTime,
                                       Map<String, Integer> targetStops) {
        ch.naviqore.gtfs.schedule.model.Stop lastStop = schedule.getStops().get(lastStopId);
        int lastWalkDuration = targetStops.get(lastStopId);

        if (lastWalkDuration > config.getWalkingDurationMinimum()) {
            int distance = (int) target.distanceTo(lastStop.getCoordinate());
            return createWalk(distance, lastWalkDuration, WalkType.LAST_MILE, arrivalTime,
                    arrivalTime.plusSeconds(lastWalkDuration), lastStop.getCoordinate(), target, map(lastStop));
        }
        return null;
    }

    @Override
    public Stop getStopById(String stopId) throws StopNotFoundException {
        ch.naviqore.gtfs.schedule.model.Stop stop = schedule.getStops().get(stopId);

        if (stop == null) {
            throw new StopNotFoundException(stopId);
        }

        return map(stop);
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

        return map(trip, date);
    }

    @Override
    public Route getRouteById(String routeId) throws RouteNotFoundException {
        ch.naviqore.gtfs.schedule.model.Route route = schedule.getRoutes().get(routeId);

        if (route == null) {
            throw new RouteNotFoundException(routeId);
        }

        return map(route);
    }

    @Override
    public void updateStaticSchedule() {
        // TODO: Update method to pull new transit schedule from URL.
        //  Also handle case: Path and URL provided, URL only, discussion needed, which cases make sense.
        log.warn("Updating static schedule not implemented yet ({})", config.getGtfsStaticUrl());

        // clear the raptor cache, since new the cached instances are now outdated
        cache.clear();
    }

    /**
     * Caches for active services (= GTFS calendars) per date and raptor instances.
     * <p>
     * TODO: Not always create a new raptor, use mask on stop times based on active trips.
     */
    private class RaptorCache {

        private final EvictionCache<Set<ch.naviqore.gtfs.schedule.model.Calendar>, Raptor> raptorCache;
        private final EvictionCache<LocalDate, Set<ch.naviqore.gtfs.schedule.model.Calendar>> activeServices;

        /**
         * @param cacheSize the maximum number of Raptor instances to be cached.
         * @param strategy  the cache eviction strategy.
         */
        RaptorCache(int cacheSize, EvictionCache.Strategy strategy) {
            raptorCache = new EvictionCache<>(cacheSize, strategy);
            activeServices = new EvictionCache<>(Math.min(365, cacheSize * 20), strategy);
        }

        // get cached or create and cache new raptor instance, based on the active calendars on a date
        private Raptor getRaptor(LocalDate date) {
            Set<ch.naviqore.gtfs.schedule.model.Calendar> activeServices = this.activeServices.computeIfAbsent(date,
                    () -> getActiveServices(date));
            return raptorCache.computeIfAbsent(activeServices,
                    () -> new GtfsToRaptorConverter(schedule, additionalTransfers,
                            config.getTransferTimeSameStopDefault()).convert(date));
        }

        // get all active calendars form the gtfs for given date, serves as key for caching raptor instances
        private Set<ch.naviqore.gtfs.schedule.model.Calendar> getActiveServices(LocalDate date) {
            return schedule.getCalendars()
                    .values()
                    .stream()
                    .filter(calendar -> calendar.isServiceAvailable(date))
                    .collect(Collectors.toSet());
        }

        // clear the cache, needs to be called when the GTFS schedule changes
        private void clear() {
            activeServices.clear();
            raptorCache.clear();
        }

    }
}
