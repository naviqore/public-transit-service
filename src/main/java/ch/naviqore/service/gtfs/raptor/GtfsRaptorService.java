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
import lombok.RequiredArgsConstructor;
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
        return new StopToStop(schedule.getStops().get(source.getId()), schedule.getStops().get(target.getId()), time,
                timeType, config).run();
    }

    @Override
    public List<Connection> getConnections(GeoCoordinate source, GeoCoordinate target, LocalDateTime time,
                                           TimeType timeType,
                                           ConnectionQueryConfig config) throws ConnectionRoutingException {
        return new GeoToGeo(source, target, time, timeType, config).run();
    }

    @Override
    public List<Connection> getConnections(Stop source, GeoCoordinate target, LocalDateTime time, TimeType timeType,
                                           ConnectionQueryConfig config) throws ConnectionRoutingException {
        return new StopToGeo(schedule.getStops().get(source.getId()), target, time, timeType, config).run();
    }

    @Override
    public List<Connection> getConnections(GeoCoordinate source, Stop target, LocalDateTime time, TimeType timeType,
                                           ConnectionQueryConfig config) throws ConnectionRoutingException {
        return new GeoToStop(source, schedule.getStops().get(target.getId()), time, timeType, config).run();
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
        return new GeoSource(source, time, timeType, config).run();
    }

    @Override
    public Map<Stop, Connection> getIsolines(Stop source, LocalDateTime time, TimeType timeType,
                                             ConnectionQueryConfig config) throws ConnectionRoutingException {
        return new StopSource(source, time, timeType, config).run();
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

    @RequiredArgsConstructor
    abstract class ConnectionQueryTemplate<S, T> {

        protected final S source;
        protected final T target;
        protected final LocalDateTime time;
        protected final TimeType timeType;
        protected final ConnectionQueryConfig config;

        protected abstract Map<String, LocalDateTime> prepareSourceStops(S source);

        protected abstract Map<String, Integer> prepareTargetStops(T target);

        protected abstract Connection postprocessConnection(S source, ch.naviqore.raptor.Connection connection,
                                                            T target);

        protected abstract ConnectionQueryTemplate<T, S> swap();

        protected abstract List<Connection> handleInvalidStopException(
                RaptorAlgorithm.InvalidStopException exception) throws ConnectionRoutingException;

        List<Connection> run() throws ConnectionRoutingException {
            // TODO: / COMMENT: I think this is dangerous because if you run the query multiple times (e.g. when
            // routing from location instead of stop due to InvalidStopException, the swap might occur twice resulting
            // in incorrect results.
            // swap source and target if time type is arrival, which means routing in the reverse time dimension
            if (timeType == TimeType.ARRIVAL) {
                return swap().process();
            }

            return process();
        }

        List<Connection> process() throws ConnectionRoutingException {

            Map<String, LocalDateTime> sourceStops = prepareSourceStops(source);
            Map<String, Integer> targetStops = prepareTargetStops(target);

            // no source stop or target stop is within walkable distance, and therefore no connections are available
            if (sourceStops.isEmpty() || targetStops.isEmpty()) {
                return List.of();
            }

            // query connection from raptor
            List<ch.naviqore.raptor.Connection> connections;
            try {
                if (timeType == TimeType.DEPARTURE) {
                    connections = raptorRouter.routeEarliestArrival(sourceStops, targetStops, TypeMapper.map(config));
                } else {
                    connections = raptorRouter.routeLatestDeparture(targetStops, sourceStops, TypeMapper.map(config));
                }
            } catch (RaptorAlgorithm.InvalidStopException e) {
                return this.handleInvalidStopException(e);
            } catch (IllegalArgumentException e) {
                throw new ConnectionRoutingException(e);
            }

            // assemble connection results
            List<Connection> result = new ArrayList<>();

            for (ch.naviqore.raptor.Connection raptorConnection : connections) {
                Connection serviceConnection = postprocessConnection(source, raptorConnection, target);

                // filter because the raptor algorithm does not consider the first mile and last mile walk time
                if (Duration.between(serviceConnection.getDepartureTime(), serviceConnection.getArrivalTime())
                        .getSeconds() <= config.getMaximumTravelTime()) {
                    result.add(serviceConnection);
                }
            }

            return result;
        }

    }

    private class StopToStop extends ConnectionQueryTemplate<ch.naviqore.gtfs.schedule.model.Stop, ch.naviqore.gtfs.schedule.model.Stop> {

        public StopToStop(ch.naviqore.gtfs.schedule.model.Stop source, ch.naviqore.gtfs.schedule.model.Stop target,
                          LocalDateTime time, TimeType timeType, ConnectionQueryConfig config) {
            super(source, target, time, timeType, config);
        }

        @Override
        protected Map<String, LocalDateTime> prepareSourceStops(ch.naviqore.gtfs.schedule.model.Stop source) {
            return getAllChildStopsFromStop(TypeMapper.map(source), time);
        }

        @Override
        protected Map<String, Integer> prepareTargetStops(ch.naviqore.gtfs.schedule.model.Stop target) {
            return getAllChildStopsFromStop(TypeMapper.map(target));
        }

        @Override
        protected Connection postprocessConnection(ch.naviqore.gtfs.schedule.model.Stop source,
                                                   ch.naviqore.raptor.Connection connection,
                                                   ch.naviqore.gtfs.schedule.model.Stop target) {
            return TypeMapper.map(connection, null, null, schedule);
        }

        @Override
        protected ConnectionQueryTemplate<ch.naviqore.gtfs.schedule.model.Stop, ch.naviqore.gtfs.schedule.model.Stop> swap() {
            return new StopToStop(target, source, time, timeType, config);
        }

        @Override
        protected List<Connection> handleInvalidStopException(
                RaptorAlgorithm.InvalidStopException exception) throws ConnectionRoutingException {
            return new GeoToGeo(source, target, time, timeType, config).process();
        }
    }

    private class StopToGeo extends ConnectionQueryTemplate<ch.naviqore.gtfs.schedule.model.Stop, GeoCoordinate> {

        public StopToGeo(ch.naviqore.gtfs.schedule.model.Stop source, GeoCoordinate target, LocalDateTime time,
                         TimeType timeType, ConnectionQueryConfig config) {
            super(source, target, time, timeType, config);
        }

        @Override
        protected Map<String, LocalDateTime> prepareSourceStops(ch.naviqore.gtfs.schedule.model.Stop source) {
            return getAllChildStopsFromStop(TypeMapper.map(source), time);
        }

        @Override
        protected Map<String, Integer> prepareTargetStops(GeoCoordinate target) {
            return getStopsWithWalkTimeFromLocation(target, config.getMaximumWalkingDuration());
        }

        @Override
        protected Connection postprocessConnection(ch.naviqore.gtfs.schedule.model.Stop source,
                                                   ch.naviqore.raptor.Connection connection, GeoCoordinate target) {
            LocalDateTime arrivalTime = connection.getArrivalTime();
            Walk lastMile = getLastWalk(target, connection.getToStopId(), arrivalTime);

            // TODO: Handle case where lastMile is not null and last leg is a transfer --> use walkCalculator

            return TypeMapper.map(connection, null, lastMile, schedule);
        }

        @Override
        protected ConnectionQueryTemplate<GeoCoordinate, ch.naviqore.gtfs.schedule.model.Stop> swap() {
            return new GeoToStop(target, source, time, timeType, config);
        }

        @Override
        protected List<Connection> handleInvalidStopException(
                RaptorAlgorithm.InvalidStopException exception) throws ConnectionRoutingException {
            return new GeoToGeo(source, target, time, timeType, config).process();
        }
    }

    private class GeoToStop extends ConnectionQueryTemplate<GeoCoordinate, ch.naviqore.gtfs.schedule.model.Stop> {

        public GeoToStop(GeoCoordinate source, ch.naviqore.gtfs.schedule.model.Stop target, LocalDateTime time,
                         TimeType timeType, ConnectionQueryConfig config) {
            super(source, target, time, timeType, config);
        }

        @Override
        protected Map<String, LocalDateTime> prepareSourceStops(GeoCoordinate source) {
            return getStopsWithWalkTimeFromLocation(source, time, config.getMaximumWalkingDuration(), timeType);
        }

        @Override
        protected Map<String, Integer> prepareTargetStops(ch.naviqore.gtfs.schedule.model.Stop target) {
            return getAllChildStopsFromStop(TypeMapper.map(target));
        }

        @Override
        protected Connection postprocessConnection(GeoCoordinate source, ch.naviqore.raptor.Connection connection,
                                                   ch.naviqore.gtfs.schedule.model.Stop target) {
            LocalDateTime departureTime = connection.getDepartureTime();
            Walk firstMile = getFirstWalk(source, connection.getFromStopId(), departureTime);

            // TODO: Handle case where firstMile is not null and first leg is a transfer --> use walkCalculator

            return TypeMapper.map(connection, firstMile, null, schedule);
        }

        @Override
        protected ConnectionQueryTemplate<ch.naviqore.gtfs.schedule.model.Stop, GeoCoordinate> swap() {
            return new StopToGeo(target, source, time, timeType, config);
        }

        @Override
        protected List<Connection> handleInvalidStopException(
                RaptorAlgorithm.InvalidStopException exception) throws ConnectionRoutingException {
            return new GeoToGeo(source, target, time, timeType, config).process();
        }
    }

    private class GeoToGeo extends ConnectionQueryTemplate<GeoCoordinate, GeoCoordinate> {

        private ch.naviqore.gtfs.schedule.model.Stop sourceStop = null;
        private ch.naviqore.gtfs.schedule.model.Stop targetStop = null;

        public GeoToGeo(GeoCoordinate source, GeoCoordinate target, LocalDateTime time, TimeType timeType,
                        ConnectionQueryConfig config) {
            super(source, target, time, timeType, config);
        }

        public GeoToGeo(GeoCoordinate source, ch.naviqore.gtfs.schedule.model.Stop target, LocalDateTime time,
                        TimeType timeType, ConnectionQueryConfig config) {
            super(source, target.getCoordinate(), time, timeType, config);
            this.targetStop = target;
        }

        public GeoToGeo(ch.naviqore.gtfs.schedule.model.Stop source, GeoCoordinate target, LocalDateTime time,
                        TimeType timeType, ConnectionQueryConfig config) {
            super(source.getCoordinate(), target, time, timeType, config);
            this.sourceStop = source;
        }

        public GeoToGeo(ch.naviqore.gtfs.schedule.model.Stop source, ch.naviqore.gtfs.schedule.model.Stop target,
                        LocalDateTime time, TimeType timeType, ConnectionQueryConfig config) {
            super(source.getCoordinate(), target.getCoordinate(), time, timeType, config);
            this.sourceStop = source;
            this.targetStop = target;
        }

        @Override
        protected Map<String, LocalDateTime> prepareSourceStops(GeoCoordinate source) {
            return getStopsWithWalkTimeFromLocation(source, time, config.getMaximumWalkingDuration(), timeType);
        }

        @Override
        protected Map<String, Integer> prepareTargetStops(GeoCoordinate target) {
            return getStopsWithWalkTimeFromLocation(target, config.getMaximumWalkingDuration());
        }

        @Override
        protected Connection postprocessConnection(GeoCoordinate source, ch.naviqore.raptor.Connection connection,
                                                   GeoCoordinate target) {
            LocalDateTime departureTime = connection.getDepartureTime();
            Walk firstMile = getFirstWalk(source, connection.getFromStopId(), departureTime);

            LocalDateTime arrivalTime = connection.getArrivalTime();
            Walk lastMile = getLastWalk(target, connection.getToStopId(), arrivalTime);

            // TODO: Add sourceStop as source for firstMile if sourceStop != null
            // TODO: Add targetStop as target for lastMile if targetStop != null
            // TODO: Handle case where firstMile is not null and first leg is a transfer --> use walkCalculator?
            // TODO: Handle case where lastMile is not null and last leg is a transfer --> use walkCalculator?

            return TypeMapper.map(connection, firstMile, lastMile, schedule);
        }

        @Override
        protected ConnectionQueryTemplate<GeoCoordinate, GeoCoordinate> swap() {
            return new GeoToGeo(target, source, time, timeType, config);
        }

        @Override
        protected List<Connection> handleInvalidStopException(
                RaptorAlgorithm.InvalidStopException exception) {
            log.debug("{}: {}", exception.getClass().getSimpleName(), exception.getMessage());
            return List.of();
        }

    }

    @RequiredArgsConstructor
    abstract class IsolineQueryTemplate<T> {

        private final T source;
        protected final LocalDateTime time;
        protected final TimeType timeType;
        protected final ConnectionQueryConfig config;

        protected abstract Map<String, LocalDateTime> prepareSourceStops(T source);

        protected abstract Connection postprocessConnection(T source, ch.naviqore.raptor.Connection connection);

        Map<Stop, Connection> run() throws ConnectionRoutingException {
            Map<String, LocalDateTime> sourceStops = prepareSourceStops(source);

            // no source stop is within walkable distance, and therefore no isolines are available
            if (sourceStops.isEmpty()) {
                return Map.of();
            }

            Map<String, ch.naviqore.raptor.Connection> isolines;
            try {
                isolines = raptorRouter.routeIsolines(sourceStops, TypeMapper.map(timeType), TypeMapper.map(config));
            } catch (RaptorAlgorithm.InvalidStopException e) {
                // TODO: Implement abstract handleInvalidStopException method?
                log.debug("{}: {}", e.getClass().getSimpleName(), e.getMessage());
                return Map.of();
            } catch (IllegalArgumentException e) {
                throw new ConnectionRoutingException(e);
            }

            Map<Stop, Connection> result = new HashMap<>();
            for (Map.Entry<String, ch.naviqore.raptor.Connection> entry : isolines.entrySet()) {
                ch.naviqore.raptor.Connection connection = entry.getValue();
                Stop stop = TypeMapper.map(schedule.getStops().get(entry.getKey()));

                Connection serviceConnection = postprocessConnection(source, connection);

                // The raptor algorithm does not consider the firstMile walk time, so we need to filter out connections
                // that exceed the maximum travel time here
                if (Duration.between(serviceConnection.getArrivalTime(), serviceConnection.getDepartureTime())
                        .getSeconds() <= config.getMaximumTravelTime()) {
                    result.put(stop, serviceConnection);
                }
            }

            return result;
        }
    }

    // TODO: Add case where source is stop but should be processed as geocoordinate -> see connection query template
    class GeoSource extends IsolineQueryTemplate<GeoCoordinate> {

        public GeoSource(GeoCoordinate source, LocalDateTime time, TimeType timeType, ConnectionQueryConfig config) {
            super(source, time, timeType, config);
        }

        @Override
        protected Map<String, LocalDateTime> prepareSourceStops(GeoCoordinate source) {
            return getStopsWithWalkTimeFromLocation(source, time, config.getMaximumWalkingDuration(), timeType);
        }

        @Override
        protected Connection postprocessConnection(GeoCoordinate source, ch.naviqore.raptor.Connection connection) {
            Walk firstMile = null;
            Walk lastMile = null;
            if (timeType == TimeType.DEPARTURE) {
                firstMile = getFirstWalk(source, connection.getFromStopId(), connection.getDepartureTime());
            } else if (timeType == TimeType.ARRIVAL) {
                lastMile = getLastWalk(source, connection.getFromStopId(), connection.getDepartureTime());
            }

            // TODO: Handle case where firstMile is not null and first leg is a transfer --> use walkCalculator
            // TODO: Handle case where lastMile is not null and last leg is a transfer --> use walkCalculator

            return TypeMapper.map(connection, firstMile, lastMile, schedule);
        }
    }

    class StopSource extends IsolineQueryTemplate<ch.naviqore.service.Stop> {

        public StopSource(Stop source, LocalDateTime time, TimeType timeType, ConnectionQueryConfig config) {
            super(source, time, timeType, config);
        }

        @Override
        protected Map<String, LocalDateTime> prepareSourceStops(Stop source) {
            return getAllChildStopsFromStop(source, time);
        }

        @Override
        protected Connection postprocessConnection(Stop source, ch.naviqore.raptor.Connection connection) {
            return TypeMapper.map(connection, null, null, schedule);
        }
    }
}
