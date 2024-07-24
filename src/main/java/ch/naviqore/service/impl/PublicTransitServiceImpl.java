package ch.naviqore.service.impl;

import ch.naviqore.gtfs.schedule.model.GtfsSchedule;
import ch.naviqore.raptor.RaptorAlgorithm;
import ch.naviqore.raptor.router.RaptorConfig;
import ch.naviqore.service.*;
import ch.naviqore.service.config.ConnectionQueryConfig;
import ch.naviqore.service.config.ServiceConfig;
import ch.naviqore.service.exception.RouteNotFoundException;
import ch.naviqore.service.exception.StopNotFoundException;
import ch.naviqore.service.exception.TripNotActiveException;
import ch.naviqore.service.exception.TripNotFoundException;
import ch.naviqore.service.impl.convert.GtfsToRaptorConverter;
import ch.naviqore.service.impl.convert.GtfsTripMaskProvider;
import ch.naviqore.service.impl.transfer.TransferGenerator;
import ch.naviqore.service.walk.WalkCalculator;
import ch.naviqore.utils.cache.EvictionCache;
import ch.naviqore.utils.search.SearchIndex;
import ch.naviqore.utils.spatial.GeoCoordinate;
import ch.naviqore.utils.spatial.index.KDTree;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static ch.naviqore.service.impl.TypeMapper.createWalk;
import static ch.naviqore.service.impl.TypeMapper.map;

@Slf4j
public class PublicTransitServiceImpl implements PublicTransitService {

    private final ServiceConfig config;
    private final GtfsSchedule schedule;
    private final KDTree<ch.naviqore.gtfs.schedule.model.Stop> spatialStopIndex;
    private final SearchIndex<ch.naviqore.gtfs.schedule.model.Stop> stopSearchIndex;
    private final WalkCalculator walkCalculator;
    private final RaptorAlgorithm raptorAlgorithm;
    private final GtfsTripMaskProvider tripMaskProvider;

    PublicTransitServiceImpl(ServiceConfig config, GtfsSchedule schedule,
                             KDTree<ch.naviqore.gtfs.schedule.model.Stop> spatialStopIndex,
                             SearchIndex<ch.naviqore.gtfs.schedule.model.Stop> stopSearchIndex,
                             WalkCalculator walkCalculator, List<TransferGenerator.Transfer> additionalTransfers) {
        this.config = config;
        this.schedule = schedule;
        this.spatialStopIndex = spatialStopIndex;
        this.stopSearchIndex = stopSearchIndex;
        this.walkCalculator = walkCalculator;

        EvictionCache.Strategy cacheStrategy = EvictionCache.Strategy.valueOf(config.getCacheEvictionStrategy().name());
        tripMaskProvider = new GtfsTripMaskProvider(schedule, config.getCacheServiceDaySize(), cacheStrategy);

        // build raptor algorithm
        RaptorConfig raptorConfig = new RaptorConfig(config.getRaptorDaysToScan(),
                config.getTransferTimeSameStopDefault(), config.getCacheServiceDaySize(), cacheStrategy,
                tripMaskProvider);
        raptorAlgorithm = new GtfsToRaptorConverter(schedule, raptorConfig).convert();
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

    private List<Connection> getConnections(@Nullable ch.naviqore.gtfs.schedule.model.Stop departureStop,
                                            @Nullable GeoCoordinate departureLocation,
                                            @Nullable ch.naviqore.gtfs.schedule.model.Stop arrivalStop,
                                            @Nullable GeoCoordinate arrivalLocation, LocalDateTime time,
                                            TimeType timeType, ConnectionQueryConfig config) {

        boolean isDeparture = timeType == TimeType.DEPARTURE;
        ch.naviqore.gtfs.schedule.model.Stop sourceStop = isDeparture ? departureStop : arrivalStop;
        GeoCoordinate sourceLocation = isDeparture ? departureLocation : arrivalLocation;
        ch.naviqore.gtfs.schedule.model.Stop targetStop = isDeparture ? arrivalStop : departureStop;
        GeoCoordinate targetLocation = isDeparture ? arrivalLocation : departureLocation;

        Map<String, LocalDateTime> sourceStops;
        Map<String, Integer> targetStops;

        if (sourceStop != null) {
            sourceStops = getAllChildStopsFromStop(map(sourceStop), time);
        } else if (sourceLocation != null) {
            sourceStops = getStopsWithWalkTimeFromLocation(sourceLocation, time, config.getMaximumWalkingDuration(),
                    timeType);
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

        // no source stop or target stop is within walkable distance, and therefore no connections are available
        if (sourceStops.isEmpty() || targetStops.isEmpty()) {
            return List.of();
        }

        // query connection from raptor
        List<ch.naviqore.raptor.Connection> connections;
        if (isDeparture) {
            connections = raptorAlgorithm.routeEarliestArrival(sourceStops, targetStops, map(config));
        } else {
            connections = raptorAlgorithm.routeLatestDeparture(targetStops, sourceStops, map(config));
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

            Connection serviceConnection = map(connection, firstMile, lastMile, schedule);

            // Filter needed because the raptor algorithm does not consider the firstMile and lastMile walk time
            if (Duration.between(serviceConnection.getDepartureTime(), serviceConnection.getArrivalTime())
                    .getSeconds() <= config.getMaximumTravelTime()) {
                result.add(serviceConnection);
            }
        }

        return result;
    }

    public Map<String, LocalDateTime> getStopsWithWalkTimeFromLocation(GeoCoordinate location, LocalDateTime startTime,
                                                                       int maxWalkDuration, TimeType timeType) {
        Map<String, Integer> stopsWithWalkTime = getStopsWithWalkTimeFromLocation(location, maxWalkDuration);
        return stopsWithWalkTime.entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        entry -> timeType == TimeType.DEPARTURE ? startTime.plusSeconds(
                                entry.getValue()) : startTime.minusSeconds(entry.getValue())));

    }

    public Map<String, Integer> getStopsWithWalkTimeFromLocation(GeoCoordinate location, int maxWalkDuration) {
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

    public Map<String, LocalDateTime> getAllChildStopsFromStop(Stop stop, LocalDateTime time) {
        List<String> stopIds = getAllStopIdsForStop(stop);
        Map<String, LocalDateTime> stopWithDateTime = new HashMap<>();
        for (String stopId : stopIds) {
            stopWithDateTime.put(stopId, time);
        }
        return stopWithDateTime;
    }

    public Map<String, Integer> getAllChildStopsFromStop(Stop stop) {
        List<String> stopIds = getAllStopIdsForStop(stop);
        Map<String, Integer> stopsWithWalkTime = new HashMap<>();
        for (String stopId : stopIds) {
            stopsWithWalkTime.put(stopId, 0);
        }
        return stopsWithWalkTime;
    }

    @Override
    public Map<Stop, Connection> getIsoLines(GeoCoordinate source, LocalDateTime time, TimeType timeType,
                                             ConnectionQueryConfig config) {
        Map<String, LocalDateTime> sourceStops = getStopsWithWalkTimeFromLocation(source, time,
                config.getMaximumWalkingDuration(), timeType);

        return mapToStopConnectionMap(raptorAlgorithm.routeIsolines(sourceStops, map(timeType), map(config)), source,
                config, timeType);
    }

    @Override
    public Map<Stop, Connection> getIsoLines(Stop source, LocalDateTime time, TimeType timeType,
                                             ConnectionQueryConfig config) {
        Map<String, LocalDateTime> sourceStops = getAllChildStopsFromStop(source, time);

        return mapToStopConnectionMap(raptorAlgorithm.routeIsolines(sourceStops, map(timeType), map(config)), null,
                config, timeType);
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

            Stop stop = map(schedule.getStops().get(entry.getKey()));
            Connection serviceConnection = map(connection, firstMile, lastMile, schedule);

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
            return createWalk(firstWalk.distance(), firstWalkDuration, WalkType.FIRST_MILE,
                    departureTime.minusSeconds(firstWalkDuration), departureTime, source, firstStop.getCoordinate(),
                    map(firstStop));
        }
        return null;
    }

    private @Nullable Walk getLastWalk(GeoCoordinate target, String lastStopId, LocalDateTime arrivalTime) {
        ch.naviqore.gtfs.schedule.model.Stop lastStop = schedule.getStops().get(lastStopId);
        WalkCalculator.Walk lastWalk = walkCalculator.calculateWalk(target, lastStop.getCoordinate());
        int lastWalkDuration = lastWalk.duration() + config.getTransferTimeAccessEgress();

        if (lastWalkDuration > config.getWalkingDurationMinimum()) {
            return createWalk(lastWalk.distance(), lastWalkDuration, WalkType.LAST_MILE, arrivalTime,
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
        log.warn("Updating static schedule not implemented yet ({})", config.getGtfsStaticUri());

        // clear the trip mask cache, since new the cached instances are now outdated
        tripMaskProvider.clearCache();
    }
}
