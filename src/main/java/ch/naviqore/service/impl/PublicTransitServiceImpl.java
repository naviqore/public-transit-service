package ch.naviqore.service.impl;

import ch.naviqore.gtfs.schedule.GtfsScheduleReader;
import ch.naviqore.gtfs.schedule.model.GtfsSchedule;
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
    private List<MinimumTimeTransfer> minimumTimeTransfers;

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

    public Map<String, List<ch.naviqore.gtfs.schedule.model.Stop>> groupStopsByParent() {
        Map <String, List<ch.naviqore.gtfs.schedule.model.Stop>> parentStopIds = new HashMap<>();
        for (ch.naviqore.gtfs.schedule.model.Stop stop : schedule.getStops().values()) {
            String id = stop.getParentId() == null ? stop.getId() : stop.getParentId();
            if (!parentStopIds.containsKey(id)) {
                parentStopIds.put(id, new ArrayList<>());
            }
            parentStopIds.get(id).add(stop);
        }
        return parentStopIds;
    }

    private static GtfsSchedule readGtfsSchedule(String gtfsFilePath) {
        // TODO: Download file if needed
        try {
            return new GtfsScheduleReader().read(gtfsFilePath);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static SearchIndex<ch.naviqore.gtfs.schedule.model.Stop> generateStopSearchIndex(GtfsSchedule schedule, Set<String> parentStops) {
        SearchIndex<ch.naviqore.gtfs.schedule.model.Stop> index = new SearchIndex<>();
        parentStops.forEach(stopId -> index.add(stopId, schedule.getStops().get(stopId)));
        return index;
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

    @Override
    public List<Stop> getStops(String like, SearchType searchType) {
        return stopSearchIndex.search(like, map(searchType)).stream().map(TypeMapper::map).toList();
    }

    @Override
    public Optional<Stop> getNearestStop(GeoCoordinate location) {
        log.debug("Get nearest stop to {}", location);
        return Optional.ofNullable(map(spatialStopIndex.nearestNeighbour(location)));
    }

    @Override
    public List<Stop> getNearestStops(GeoCoordinate location, int radius, int limit) {
        log.debug("Get nearest {} stops to {} in radius {}", limit, location, radius);
        return spatialStopIndex.rangeSearch(location, radius).stream().map(TypeMapper::map).limit(limit).toList();
    }

    @Override
    public List<StopTime> getNextDepartures(Stop stop, LocalDateTime from, @Nullable LocalDateTime until, int limit) {
        return schedule.getNextDepartures(stop.getId(), from, limit)
                .stream()
                .map(stopTime -> map(stopTime, from.toLocalDate()))
                .filter(stopTime -> until == null || stopTime.getDepartureTime().isBefore(until))
                .toList();
    }

    @Override
    public List<Connection> getConnections(Stop source, Stop target, LocalDateTime time, TimeType timeType,
                                           ConnectionQueryConfig config) {
        notYetImplementedCheck(timeType);

        log.debug("Get connections from stop {} to stop {} {} at {}", source, target,
                timeType == TimeType.ARRIVAL ? "arriving" : "departing", time);

        // get public transit stops from schedule
        ch.naviqore.gtfs.schedule.model.Stop sourceStop = schedule.getStops().get(source.getId());
        ch.naviqore.gtfs.schedule.model.Stop targetStop = schedule.getStops().get(target.getId());

        return getConnections(sourceStop, targetStop, time, null, null);
    }

    @Override
    public List<Connection> getConnections(GeoCoordinate source, GeoCoordinate target, LocalDateTime time,
                                           TimeType timeType, ConnectionQueryConfig config) {
        notYetImplementedCheck(timeType);

        log.debug("Get connections from location {} to location {} {} at {}", source, target,
                timeType == TimeType.ARRIVAL ? "arriving" : "departing", time);

        // get nearest public transit stops
        ch.naviqore.gtfs.schedule.model.Stop sourceStop = spatialStopIndex.nearestNeighbour(source);
        ch.naviqore.gtfs.schedule.model.Stop targetStop = spatialStopIndex.nearestNeighbour(target);

        // TODO: Make CutOff configurable
        int minWalkDistanceCutoff = 200;

        ch.naviqore.service.impl.walkcalculator.Walk toSourceWalk = walkCalculator.calculateWalk(source,
                sourceStop.getCoordinate());
        ch.naviqore.service.impl.walkcalculator.Walk toTargetWalk = walkCalculator.calculateWalk(target,
                targetStop.getCoordinate());

        Walk firstMile = null;
        Walk lastMile = null;

        if (toSourceWalk.distance() >= minWalkDistanceCutoff) {
            LocalDateTime walkDepartureTime = time.minusSeconds(0); // easiest way to copy
            time = walkDepartureTime.plusSeconds(toSourceWalk.duration());
            firstMile = createWalk(toSourceWalk.distance(), toSourceWalk.duration(), WalkType.FIRST_MILE,
                    walkDepartureTime, time, source, sourceStop.getCoordinate(), map(sourceStop));
        }

        if (toTargetWalk.distance() >= minWalkDistanceCutoff) {
            lastMile = createWalk(toTargetWalk.distance(), toTargetWalk.duration(), WalkType.LAST_MILE, null, null,
                    targetStop.getCoordinate(), target, map(targetStop));
        }

        return getConnections(sourceStop, targetStop, time, firstMile, lastMile);
    }

    private @NotNull List<Connection> getConnections(ch.naviqore.gtfs.schedule.model.Stop sourceStop,
                                                     ch.naviqore.gtfs.schedule.model.Stop targetStop,
                                                     LocalDateTime time, @Nullable Walk firstMile,
                                                     @Nullable Walk lastMile) {
        int departureTime = time.toLocalTime().toSecondOfDay();

        // TODO: Not always create a new raptor, use mask on stop times based on active trips
        Raptor raptor = new GtfsToRaptorConverter(schedule, minimumTimeTransfers).convert(time.toLocalDate());

        // query connection from raptor
        List<ch.naviqore.raptor.model.Connection> connections = raptor.routeEarliestArrival(sourceStop.getId(),
                targetStop.getId(), departureTime);

        // map to connection and generate first and last mile walk
        return connections.stream()
                .map(connection -> map(connection, firstMile, lastMile, time.toLocalDate(), schedule))
                .toList();
    }

    @Override
    public Map<Stop, Connection> getIsolines(GeoCoordinate source, LocalDateTime departureTime,
                                             ConnectionQueryConfig config) {
        throw new NotImplementedException();
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
