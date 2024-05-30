package ch.naviqore.service.impl;

import ch.naviqore.gtfs.schedule.model.GtfsSchedule;
import ch.naviqore.gtfs.schedule.type.ServiceDayTime;
import ch.naviqore.service.*;
import ch.naviqore.utils.search.SearchIndex;
import ch.naviqore.utils.spatial.GeoCoordinate;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@NoArgsConstructor(access = AccessLevel.NONE)
final class TypeMapper {

    public static Stop map(ch.naviqore.gtfs.schedule.model.Stop stop) {
        if (stop == null) {
            return null;
        }

        return new StopImpl(stop.getId(), stop.getName(), map(stop.getCoordinate()));
    }

    public static Location map(GeoCoordinate coordinate) {
        return new Location(coordinate.latitude(), coordinate.longitude());
    }

    public static Route map(ch.naviqore.gtfs.schedule.model.Route route) {
        return new RouteImpl(route.getId(), route.getLongName(), route.getShortName(), route.getType().getDescription(),
                route.getAgency().name());
    }

    public static Trip map(ch.naviqore.gtfs.schedule.model.Trip trip, LocalDate date) {
        return new TripImpl(trip.getId(), trip.getHeadSign(), map(trip.getRoute()),
                trip.getStopTimes().stream().map(stopTime -> map(stopTime, date)).toList());
    }

    public static StopTime map(ch.naviqore.gtfs.schedule.model.StopTime stopTime, LocalDate date) {
        return new StopTimeImpl(map(stopTime.stop()), stopTime.arrival().toLocalDateTime(date),
                stopTime.departure().toLocalDateTime(date));
    }

    public static SearchIndex.SearchStrategy map(SearchType searchType) {
        return switch (searchType) {
            case STARTS_WITH -> SearchIndex.SearchStrategy.STARTS_WITH;
            case ENDS_WITH -> SearchIndex.SearchStrategy.ENDS_WITH;
            case CONTAINS -> SearchIndex.SearchStrategy.CONTAINS;
            case EXACT -> SearchIndex.SearchStrategy.EXACT;
        };
    }

    public static Connection map(ch.naviqore.raptor.model.Connection connection, LocalDate date,
                                 GtfsSchedule schedule) {
        return new ConnectionImpl(connection.getLegs().stream().map(leg -> map(leg, date, schedule)).toList());
    }

    public static Leg map(ch.naviqore.raptor.model.Connection.Leg leg, LocalDate date, GtfsSchedule schedule) {
        // TODO: Distance is needed on Footpaths, distance of trip can be estimated based on trip and beeline distance?
        int distance = 0;
        int duration = leg.arrivalTime() - leg.departureTime();
        Stop sourceStop = map(schedule.getStops().get(leg.fromStopId()));
        Stop targetStop = map(schedule.getStops().get(leg.toStopId()));
        return switch (leg.type()) {
            case FOOTPATH -> new WalkImpl(distance, duration, toLocalDateTime(leg.departureTime(), date),
                    toLocalDateTime(leg.arrivalTime(), date), sourceStop.getLocation(), targetStop.getLocation(),
                    sourceStop, targetStop);
            // TODO: Refactor Raptor and extract interfaces. Put Trip id on leg, then stop time can be found.
            case ROUTE -> new PublicTransitLegImpl(distance, duration, null, null, null);
        };
    }

    private static LocalDateTime toLocalDateTime(int secondsOfDay, LocalDate date) {
        return new ServiceDayTime(secondsOfDay).toLocalDateTime(date);
    }
}
