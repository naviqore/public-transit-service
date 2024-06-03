package ch.naviqore.service.impl;

import ch.naviqore.gtfs.schedule.GtfsScheduleReader;
import ch.naviqore.gtfs.schedule.model.GtfsSchedule;
import ch.naviqore.raptor.Raptor;
import ch.naviqore.service.*;
import ch.naviqore.service.config.ConnectionQueryConfig;
import ch.naviqore.service.config.ServiceConfig;
import ch.naviqore.service.exception.RouteNotFoundException;
import ch.naviqore.service.exception.StopNotFoundException;
import ch.naviqore.service.exception.TripNotActiveException;
import ch.naviqore.service.exception.TripNotFoundException;
import ch.naviqore.service.impl.convert.GtfsToRaptorConverter;
import ch.naviqore.service.impl.transfer.SameStationTransferGenerator;
import ch.naviqore.service.impl.transfer.TransferGenerator;
import ch.naviqore.service.impl.transfer.WalkTransferGenerator;
import ch.naviqore.service.walk.BeeLineWalkCalculator;
import ch.naviqore.service.walk.WalkCalculator;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static ch.naviqore.service.impl.TypeMapper.createWalk;
import static ch.naviqore.service.impl.TypeMapper.map;

@Log4j2
public class PublicTransitServiceImpl implements PublicTransitService {

    private final ServiceConfig config;
    private final WalkCalculator walkCalculator;
    private final GtfsSchedule schedule;
    private final KDTree<ch.naviqore.gtfs.schedule.model.Stop> spatialStopIndex;
    private final SearchIndex<ch.naviqore.gtfs.schedule.model.Stop> stopSearchIndex;
    private final List<TransferGenerator.Transfer> minimumTimeTransfers;

    public PublicTransitServiceImpl(ServiceConfig config) {
        this.config = config;
        this.walkCalculator = Initializer.initializeWalkCalculator(config);
        schedule = Initializer.readGtfsSchedule(config.getGtfsUrl());
        stopSearchIndex = Initializer.generateStopSearchIndex(schedule);
        spatialStopIndex = Initializer.generateSpatialStopIndex(schedule);
        minimumTimeTransfers = Initializer.generateMinimumTimeTransfers(schedule, walkCalculator, spatialStopIndex,
                config);
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

        WalkCalculator.Walk toSourceWalk = walkCalculator.calculateWalk(source, sourceStop.getCoordinate());
        WalkCalculator.Walk toTargetWalk = walkCalculator.calculateWalk(target, targetStop.getCoordinate());

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
        List<ch.naviqore.raptor.Connection> connections = raptor.routeEarliestArrival(sourceStop.getId(),
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
        log.warn("Updating static schedule not implemented yet, would fetch from {}", config.getGtfsUrl());
    }

    private static class Initializer {

        private static WalkCalculator initializeWalkCalculator(ServiceConfig config) {
            return switch (config.getWalkCalculatorType()) {
                case ServiceConfig.WalkCalculatorType.BEE_LINE_DISTANCE ->
                        new BeeLineWalkCalculator(config.getWalkingSpeed());
            };
        }

        private static GtfsSchedule readGtfsSchedule(String gtfsFilePath) {
            // TODO: Download file if needed
            try {
                return new GtfsScheduleReader().read(gtfsFilePath);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        private static SearchIndex<ch.naviqore.gtfs.schedule.model.Stop> generateStopSearchIndex(
                GtfsSchedule schedule) {
            SearchIndex<ch.naviqore.gtfs.schedule.model.Stop> index = new SearchIndex<>();
            schedule.getStops().values().forEach(stop -> index.add(stop.getName(), stop));

            return index;
        }

        private static KDTree<ch.naviqore.gtfs.schedule.model.Stop> generateSpatialStopIndex(GtfsSchedule schedule) {
            return new KDTreeBuilder<ch.naviqore.gtfs.schedule.model.Stop>().addLocations(schedule.getStops().values())
                    .build();
        }

        private static List<TransferGenerator.Transfer> generateMinimumTimeTransfers(GtfsSchedule schedule,
                                                                                     WalkCalculator walkCalculator,
                                                                                     KDTree<ch.naviqore.gtfs.schedule.model.Stop> spatialStopIndex,
                                                                                     ServiceConfig config) {

            SameStationTransferGenerator sameStationGenerator = new SameStationTransferGenerator(
                    config.getMinimumTransferTime());
            WalkTransferGenerator walkGenerator = new WalkTransferGenerator(walkCalculator,
                    config.getMinimumTransferTime(), config.getMaxWalkingDistance(), spatialStopIndex);

            // TODO: Whats happening here, ca we write this in a more readable way? Maybe separate both generators und use
            //  a CompletableFuture instead if a parallel stream? Or explain it with some comments... =)
            List<TransferGenerator.Transfer> generatedTransfers = List.of(sameStationGenerator, walkGenerator)
                    .parallelStream()
                    .flatMap(generator -> generator.generateTransfers(schedule).stream())
                    .filter(transfer -> transfer.from().getTransfers()
                            // TODO: Another stream inside a loop...
                            .stream().noneMatch(t -> t.getToStop().equals(transfer.to())))
                    .toList();

            // TODO: Are we trying to get unique transfers here? Seems kind of inefficient having a stream inside a loop,
            //  maybe use a hashmap?
            List<TransferGenerator.Transfer> minimumTimeTransfers = new ArrayList<>();
            for (TransferGenerator.Transfer transfer : generatedTransfers) {
                if (minimumTimeTransfers.stream()
                        .noneMatch(t -> t.from().equals(transfer.from()) && t.to().equals(transfer.to()))) {
                    minimumTimeTransfers.add(transfer);
                }
            }

            // TODO: Suggestion - but i am not sure if it is still correct as I do not fully understand what we want to
            //  achieve here :=)
        /*
        // Helper method to process transfers for a generator
        private List<TransferGenerator.Transfer> processTransfers(TransferGenerator generator, Schedule schedule) {
            return generator.generateTransfers(schedule).stream()
                .filter(transfer -> transfer.from().getTransfers()
                    .stream().noneMatch(t -> t.getToStop().equals(transfer.to())))
                .collect(Collectors.toList());
        }

        public List<TransferGenerator.Transfer> getUniqueTransfers(Schedule schedule) throws ExecutionException, InterruptedException {
            SameStationTransferGenerator sameStationGenerator = new SameStationTransferGenerator(config.getMinimumTransferTime());
            WalkTransferGenerator walkGenerator = new WalkTransferGenerator(
                walkCalculator, config.getMinimumTransferTime(), config.getMaxWalkingDistance(), spatialStopIndex);

            // Use CompletableFuture to handle asynchronous generation
            CompletableFuture<List<TransferGenerator.Transfer>> futureSameStation = CompletableFuture.supplyAsync(() -> processTransfers(sameStationGenerator, schedule));
            CompletableFuture<List<TransferGenerator.Transfer>> futureWalk = CompletableFuture.supplyAsync(() -> processTransfers(walkGenerator, schedule));

            // Wait for both futures to complete and combine their results
            List<TransferGenerator.Transfer> combinedTransfers = new ArrayList<>();
            combinedTransfers.addAll(futureSameStation.get());
            combinedTransfers.addAll(futureWalk.get());

            // Use a HashMap to filter out duplicates based on from and to properties
            Map<Pair<String, String>, TransferGenerator.Transfer> uniqueTransfers = new HashMap<>();
            for (TransferGenerator.Transfer transfer : combinedTransfers) {
                Pair<String, String> key = new Pair<>(transfer.from(), transfer.to());
                uniqueTransfers.putIfAbsent(key, transfer);
            }

            return new ArrayList<>(uniqueTransfers.values());
        }
        */

            return minimumTimeTransfers;
        }

    }

}
