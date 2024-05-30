package ch.naviqore.service.impl;

import ch.naviqore.gtfs.schedule.GtfsScheduleReader;
import ch.naviqore.gtfs.schedule.model.GtfsSchedule;
import ch.naviqore.raptor.GtfsToRaptorConverter;
import ch.naviqore.raptor.model.Raptor;
import ch.naviqore.service.*;
import ch.naviqore.service.config.ConnectionQueryConfig;
import ch.naviqore.service.exception.RouteNotFoundException;
import ch.naviqore.service.exception.StopNotFoundException;
import ch.naviqore.service.exception.TripNotActiveException;
import ch.naviqore.service.exception.TripNotFoundException;
import ch.naviqore.utils.search.SearchIndex;
import ch.naviqore.utils.spatial.index.KDTree;
import ch.naviqore.utils.spatial.index.KDTreeBuilder;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.NotImplementedException;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static ch.naviqore.service.impl.TypeMapper.map;

@Log4j2
public class PublicTransitServiceImpl implements PublicTransitService {

    private final GtfsSchedule schedule;
    private final KDTree<ch.naviqore.gtfs.schedule.model.Stop> spatialStopIndex;
    private final SearchIndex<ch.naviqore.gtfs.schedule.model.Stop> stopSearchIndex;

    public PublicTransitServiceImpl(String gtfsFilePath) {
        schedule = readGtfsSchedule(gtfsFilePath);
        stopSearchIndex = generateStopSearchIndex(schedule);
        spatialStopIndex = generateSpatialStopIndex(schedule);
    }

    private static GtfsSchedule readGtfsSchedule(String gtfsFilePath) {
        // TODO: Download file if needed
        try {
            return new GtfsScheduleReader().read(gtfsFilePath);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static SearchIndex<ch.naviqore.gtfs.schedule.model.Stop> generateStopSearchIndex(GtfsSchedule schedule) {
        SearchIndex<ch.naviqore.gtfs.schedule.model.Stop> index = new SearchIndex<>();
        schedule.getStops().values().forEach(stop -> index.add(stop.getName(), stop));

        return index;
    }

    private static KDTree<ch.naviqore.gtfs.schedule.model.Stop> generateSpatialStopIndex(GtfsSchedule schedule) {
        return new KDTreeBuilder<ch.naviqore.gtfs.schedule.model.Stop>().addLocations(schedule.getStops().values())
                .build();
    }

    @Override
    public List<Stop> getStops(String like, SearchType searchType) {
        return stopSearchIndex.search(like, map(searchType)).stream().map(TypeMapper::map).toList();
    }

    @Override
    public Optional<Stop> getNearestStop(Location location) {
        log.debug("Get nearest stop to {}", location);
        return Optional.ofNullable(
                map(spatialStopIndex.nearestNeighbour(location.getLatitude(), location.getLongitude())));
    }

    @Override
    public List<Stop> getNearestStops(Location location, int radius, int limit) {
        log.debug("Get nearest {} stops to {} in radius {}", limit, location, radius);
        return spatialStopIndex.rangeSearch(location.getLatitude(), location.getLongitude(), radius)
                .stream()
                .map(TypeMapper::map)
                .limit(limit)
                .toList();
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
    public List<Connection> getConnections(Location source, Location target, LocalDateTime time, TimeType timeType,
                                           ConnectionQueryConfig config) {
        log.debug("Get connections from {} to {} {} at {}", source, target,
                timeType == TimeType.ARRIVAL ? "arriving" : "departing", time);

        if (timeType == TimeType.ARRIVAL) {
            // TODO: Implement in raptor
            throw new NotImplementedException();
        }

        String sourceStopId = schedule.getNearestStop(source.getLatitude(), source.getLongitude()).getId();
        String targetStopId = schedule.getNearestStop(target.getLatitude(), target.getLongitude()).getId();
        int departureTime = time.toLocalTime().toSecondOfDay();

        // TODO: Not always create a new raptor
        Raptor raptor = new GtfsToRaptorConverter(schedule).convert(time.toLocalDate());

        List<ch.naviqore.raptor.model.Connection> connections = raptor.routeEarliestArrival(sourceStopId, targetStopId,
                departureTime);

        // TODO also add first and last walk if connection does not start or end at stop
        return connections.stream().map(connection -> map(connection, time.toLocalDate(), schedule)).toList();
    }

    @Override
    public Map<Stop, Connection> isoline(Location source, LocalDateTime departureTime, ConnectionQueryConfig config) {
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
