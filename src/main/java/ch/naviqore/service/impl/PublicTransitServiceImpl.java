package ch.naviqore.service.impl;

import ch.naviqore.gtfs.schedule.GtfsScheduleReader;
import ch.naviqore.gtfs.schedule.model.GtfsSchedule;
import ch.naviqore.gtfs.schedule.type.ServiceDayTime;
import ch.naviqore.raptor.model.Raptor;
import ch.naviqore.service.*;
import ch.naviqore.service.config.ConnectionQueryConfig;
import ch.naviqore.service.exception.RouteNotFoundException;
import ch.naviqore.service.exception.StopNotFoundException;
import ch.naviqore.service.exception.TripNotActiveException;
import ch.naviqore.service.exception.TripNotFoundException;
import ch.naviqore.service.impl.transfergenerator.MinimumTimeTransfer;
import ch.naviqore.service.impl.transfergenerator.SameStationTransferGenerator;
import ch.naviqore.service.impl.transfergenerator.TransferGenerator;
import ch.naviqore.service.impl.transfergenerator.WalkTransferGenerator;
import ch.naviqore.service.impl.walkcalculator.BeeLineWalkCalculator;
import ch.naviqore.service.impl.walkcalculator.WalkCalculator;
import ch.naviqore.utils.search.SearchIndex;
import ch.naviqore.utils.search.SearchIndexBuilder;
import ch.naviqore.utils.spatial.GeoCoordinate;
import ch.naviqore.utils.spatial.index.KDTree;
import ch.naviqore.utils.spatial.index.KDTreeBuilder;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.NotImplementedException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static ch.naviqore.service.impl.TypeMapper.createWalk;
import static ch.naviqore.service.impl.TypeMapper.map;

@Log4j2
public class PublicTransitServiceImpl implements PublicTransitService {

    private final GtfsSchedule schedule;
    private final Map<String, List<ch.naviqore.gtfs.schedule.model.Stop>> parentStops;
    private final KDTree<ch.naviqore.gtfs.schedule.model.Stop> spatialStopIndex;
    private final SearchIndex<ch.naviqore.gtfs.schedule.model.Stop> stopSearchIndex;
    private final WalkCalculator walkCalculator;
    private final List<MinimumTimeTransfer> minimumTimeTransfers;

    public PublicTransitServiceImpl(String gtfsFilePath) {
        schedule = readGtfsSchedule(gtfsFilePath);
        parentStops = groupStopsByParent();
        stopSearchIndex = generateStopSearchIndex(schedule, parentStops.keySet());
        spatialStopIndex = generateSpatialStopIndex(schedule);

        // TODO: Allow adding removing dynamically
        walkCalculator = new BeeLineWalkCalculator(3500);
        List<TransferGenerator> transferGenerators = List.of(new SameStationTransferGenerator(120),
                new WalkTransferGenerator(walkCalculator, 120, 500, spatialStopIndex));

        minimumTimeTransfers = generateMinimumTimeTransfers(schedule, transferGenerators);
    }

    private static GtfsSchedule readGtfsSchedule(String gtfsFilePath) {
        // TODO: Download file if needed
        try {
            return new GtfsScheduleReader().read(gtfsFilePath);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
    
    private static SearchIndex<ch.naviqore.gtfs.schedule.model.Stop> generateStopSearchIndex(GtfsSchedule schedule,
                                                                                             Set<String> parentStops) {
        SearchIndexBuilder<ch.naviqore.gtfs.schedule.model.Stop> builder = SearchIndex.builder();
        parentStops.forEach(stopId -> builder.add(stopId.toLowerCase(), schedule.getStops().get(stopId)));

        return builder.build();
    }

    private static KDTree<ch.naviqore.gtfs.schedule.model.Stop> generateSpatialStopIndex(GtfsSchedule schedule) {
        return new KDTreeBuilder<ch.naviqore.gtfs.schedule.model.Stop>().addLocations(schedule.getStops().values())
                .build();
    }

    private static List<MinimumTimeTransfer> generateMinimumTimeTransfers(GtfsSchedule schedule,
                                                                          List<TransferGenerator> transferGenerators) {
        List<MinimumTimeTransfer> minimumTimeTransfers = new ArrayList<>();
        List<MinimumTimeTransfer> generatedTransfers = transferGenerators.parallelStream()
                .flatMap(generator -> generator.generateTransfers(schedule).stream())
                .filter(transfer -> transfer.from()
                        .getTransfers()
                        .stream()
                        .noneMatch(t -> t.getToStop().equals(transfer.to())))
                .toList();

        for (MinimumTimeTransfer transfer : generatedTransfers) {
            if (minimumTimeTransfers.stream()
                    .noneMatch(t -> t.from().equals(transfer.from()) && t.to().equals(transfer.to()))) {
                minimumTimeTransfers.add(transfer);
            }
        }

        return minimumTimeTransfers;
    }

    private static void notYetImplementedCheck(TimeType timeType) {
        if (timeType == TimeType.ARRIVAL) {
            // TODO: Implement in raptor
            throw new NotImplementedException();
        }
    }

    public Map<String, List<ch.naviqore.gtfs.schedule.model.Stop>> groupStopsByParent() {
        Map<String, List<ch.naviqore.gtfs.schedule.model.Stop>> parentStopIds = new HashMap<>();
        for (ch.naviqore.gtfs.schedule.model.Stop stop : schedule.getStops().values()) {
            String id = stop.getParentId() == null ? stop.getId() : stop.getParentId();
            if (!parentStopIds.containsKey(id)) {
                parentStopIds.put(id, new ArrayList<>());
            }
            parentStopIds.get(id).add(stop);
        }
        return parentStopIds;
    }

    @Override
    public List<Stop> getStops(String like, SearchType searchType) {
        return stopSearchIndex.search(like.toLowerCase(), map(searchType)).stream().map(TypeMapper::map).toList();
    }

    @Override
    public Optional<Stop> getNearestStop(GeoCoordinate location) {
        log.debug("Get nearest stop to {}", location);
        ch.naviqore.gtfs.schedule.model.Stop stop = spatialStopIndex.nearestNeighbour(location);
        if (stop != null && stop.getParentId() != null && !stop.getParentId().equals(stop.getId())) {
            stop = schedule.getStops().get(stop.getParentId());
        }
        return Optional.ofNullable(map(stop));
    }

    @Override
    public List<Stop> getNearestStops(GeoCoordinate location, int radius, int limit) {
        log.debug("Get nearest {} stops to {} in radius {}", limit, location, radius);

        List<ch.naviqore.gtfs.schedule.model.Stop> stops = new ArrayList<>();

        for (ch.naviqore.gtfs.schedule.model.Stop stop : spatialStopIndex.rangeSearch(location, radius)) {
            if (stop.getParentId() != null && !stop.getParentId().equals(stop.getId())) {
                stop = schedule.getStops().get(stop.getParentId());
            }
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

    private List<String> getAllStopIdsForStop(Stop stop) {
        List<String> stopIds;
        if (parentStops.containsKey(stop.getId())) {
            stopIds = getAllStopIdsForParentStop(stop.getId());
        } else {
            stopIds = List.of(stop.getId());
        }
        return stopIds;
    }

    private List<String> getAllStopIdsForParentStop(String stopId) {
        ch.naviqore.gtfs.schedule.model.Stop gtfsStop = schedule.getStops().get(stopId);
        String parentId = gtfsStop.getParentId() == null ? gtfsStop.getId() : gtfsStop.getParentId();
        return parentStops.get(parentId).stream().map(ch.naviqore.gtfs.schedule.model.Stop::getId).toList();
    }

    @Override
    public List<Connection> getConnections(Stop source, Stop target, LocalDateTime time, TimeType timeType,
                                           ConnectionQueryConfig config) {
        notYetImplementedCheck(timeType);

        log.debug("Get connections from stop {} to stop {} {} at {}", source, target,
                timeType == TimeType.ARRIVAL ? "arriving" : "departing", time);

        return getConnections(schedule.getStops().get(source.getId()), schedule.getStops().get(target.getId()), time);
    }

    @Override
    public List<Connection> getConnections(GeoCoordinate source, GeoCoordinate target, LocalDateTime time,
                                           TimeType timeType, ConnectionQueryConfig config) {
        notYetImplementedCheck(timeType);

        log.debug("Get connections from location {} to location {} {} at {}", source, target,
                timeType == TimeType.ARRIVAL ? "arriving" : "departing", time);

        Map<String, Integer> sourceStops = getStopsWithWalkTimeFromLocation(source, time.toLocalTime().toSecondOfDay());
        Map<String, Integer> targetStops = getStopsWithWalkTimeFromLocation(target);

        // query connection from raptor
        Raptor raptor = new GtfsToRaptorConverter(schedule, minimumTimeTransfers).convert(time.toLocalDate());

        List<ch.naviqore.raptor.model.Connection> connections = raptor.routeEarliestArrival(sourceStops, targetStops);

        List<Connection> result = new ArrayList<>();

        // TODO: Make CutOff configurable, if walk duration is longer than this, add walk to first mile,
        // else it is too short to be notable.
        int minWalkDurationCutoff = 120;

        for (ch.naviqore.raptor.model.Connection connection : connections) {
            Walk firstMile = null;
            Walk lastMile = null;

            ch.naviqore.gtfs.schedule.model.Stop firstStop = schedule.getStops().get(connection.getFromStopId());
            ch.naviqore.gtfs.schedule.model.Stop lastStop = schedule.getStops().get(connection.getToStopId());

            int firstWalkDuration = sourceStops.get(connection.getFromStopId()) - time.toLocalTime().toSecondOfDay();
            int lastWalkDuration = targetStops.get(connection.getToStopId());

            if (firstWalkDuration > minWalkDurationCutoff) {
                int distance = (int) source.distanceTo(firstStop.getCoordinate());
                firstMile = createWalk(distance, firstWalkDuration, WalkType.FIRST_MILE, time,
                        time.plusSeconds(firstWalkDuration), source, firstStop.getCoordinate(), map(firstStop));
            }

            if (lastWalkDuration > minWalkDurationCutoff) {
                int distance = (int) target.distanceTo(lastStop.getCoordinate());
                LocalDateTime stopArrivalTime = new ServiceDayTime(connection.getArrivalTime()).toLocalDateTime(
                        time.toLocalDate());
                lastMile = createWalk(distance, lastWalkDuration, WalkType.LAST_MILE, stopArrivalTime,
                        stopArrivalTime.plusSeconds(lastWalkDuration), lastStop.getCoordinate(), target, map(lastStop));
            }

            result.add(map(connection, firstMile, lastMile, time.toLocalDate(), schedule));
        }

        return result;
    }

    private @NotNull List<Connection> getConnections(ch.naviqore.gtfs.schedule.model.Stop sourceStop,
                                                     ch.naviqore.gtfs.schedule.model.Stop targetStop,
                                                     LocalDateTime time) {

        int departureTime = time.toLocalTime().toSecondOfDay();
        Map<String, Integer> sourceStops = getAllChildStopsFromStop(map(sourceStop), departureTime);
        Map<String, Integer> targetStops = getAllChildStopsFromStop(map(targetStop));

        // TODO: Not always create a new raptor, use mask on stop times based on active trips
        Raptor raptor = new GtfsToRaptorConverter(schedule, minimumTimeTransfers).convert(time.toLocalDate());

        // query connection from raptor
        List<ch.naviqore.raptor.model.Connection> connections = raptor.routeEarliestArrival(sourceStops, targetStops);

        // map to connection and generate first and last mile walk
        return connections.stream()
                .map(connection -> map(connection, null, null, time.toLocalDate(), schedule))
                .toList();
    }

    public Map<String, Integer> getStopsWithWalkTimeFromLocation(GeoCoordinate location) {
        return getStopsWithWalkTimeFromLocation(location, 0);
    }

    public Map<String, Integer> getStopsWithWalkTimeFromLocation(GeoCoordinate location, int startTimeInSeconds) {
        // TODO: Make configurable
        int maxSearchRadius = 500;
        List<ch.naviqore.gtfs.schedule.model.Stop> nearestStops = new ArrayList<>(
                spatialStopIndex.rangeSearch(location, maxSearchRadius));

        if (nearestStops.isEmpty()) {
            nearestStops.add(spatialStopIndex.nearestNeighbour(location));
        }

        Map<String, Integer> stopsWithWalkTime = new HashMap<>();
        for (ch.naviqore.gtfs.schedule.model.Stop stop : nearestStops) {
            stopsWithWalkTime.put(stop.getId(),
                    startTimeInSeconds + walkCalculator.calculateWalk(location, stop.getCoordinate()).duration());
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
            ch.naviqore.gtfs.schedule.model.Stop gtfsStop = schedule.getStops().get(stopId);
            stopsWithWalkTime.put(stopId,
                    startTimeInSeconds + walkCalculator.calculateWalk(stop.getLocation(), gtfsStop.getCoordinate())
                            .duration());
        }
        return stopsWithWalkTime;
    }

    @Override
    public Map<Stop, Connection> getIsolines(GeoCoordinate source, LocalDateTime departureTime,
                                             ConnectionQueryConfig config) {
        Map<String, Integer> sourceStops = getStopsWithWalkTimeFromLocation(source,
                departureTime.toLocalTime().toSecondOfDay());

        // TODO: Not always create a new raptor, use mask on stop times based on active trips
        Raptor raptor = new GtfsToRaptorConverter(schedule, minimumTimeTransfers).convert(departureTime.toLocalDate());

        Map<String, ch.naviqore.raptor.model.Connection> isoLines = raptor.getIsoLines(sourceStops);

        // TODO: Make CutOff configurable, if walk duration is longer than this, add walk to first mile,
        // else it is too short to be notable.
        int minWalkDurationCutoff = 120;
        Map<Stop, Connection> result = new HashMap<>();

        for (Map.Entry<String, ch.naviqore.raptor.model.Connection> entry : isoLines.entrySet()) {
            ch.naviqore.raptor.model.Connection connection = entry.getValue();
            Walk firstMile = null;

            ch.naviqore.gtfs.schedule.model.Stop firstStop = schedule.getStops().get(connection.getFromStopId());
            int firstWalkDuration = sourceStops.get(connection.getFromStopId()) - departureTime.toLocalTime()
                    .toSecondOfDay();

            if (firstWalkDuration > minWalkDurationCutoff) {
                int distance = (int) source.distanceTo(firstStop.getCoordinate());
                firstMile = createWalk(distance, firstWalkDuration, WalkType.FIRST_MILE, departureTime,
                        departureTime.plusSeconds(firstWalkDuration), source, firstStop.getCoordinate(), map(firstStop));
            }

            ch.naviqore.gtfs.schedule.model.Stop stop = schedule.getStops().get(entry.getKey());
            result.put(map(stop), map(connection, firstMile, null, departureTime.toLocalDate(), schedule));
        }

        return result;
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
        log.warn("Updating static schedule not implemented yet");
    }

}
