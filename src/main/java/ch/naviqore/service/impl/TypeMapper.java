package ch.naviqore.service.impl;

import ch.naviqore.gtfs.schedule.model.GtfsSchedule;
import ch.naviqore.gtfs.schedule.type.ServiceDayTime;
import ch.naviqore.service.*;
import ch.naviqore.utils.search.SearchIndex;
import ch.naviqore.utils.spatial.GeoCoordinate;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@NoArgsConstructor(access = AccessLevel.NONE)
final class TypeMapper {

    public static Stop map(ch.naviqore.gtfs.schedule.model.Stop stop) {
        if (stop == null) {
            return null;
        }

        return new StopImpl(stop.getId(), stop.getName(), stop.getCoordinate());
    }

    public static Route map(ch.naviqore.gtfs.schedule.model.Route route) {
        return new RouteImpl(route.getId(), route.getLongName(), route.getShortName(), route.getType().getDescription(),
                route.getAgency().name());
    }

    public static Trip map(ch.naviqore.gtfs.schedule.model.Trip trip, LocalDate date) {
        // create stop times
        List<StopTimeImpl> stopTimes = trip.getStopTimes()
                .stream()
                .map(stopTime -> new StopTimeImpl(map(stopTime.stop()), stopTime.arrival().toLocalDateTime(date),
                        stopTime.departure().toLocalDateTime(date)))
                .toList();

        // initialize trip, needs a cast to stop times from stop time impl (list)
        TripImpl tripImpl = new TripImpl(trip.getId(), trip.getHeadSign(), map(trip.getRoute()),
                stopTimes.stream().map(stopTime -> (StopTime) stopTime).toList());

        // set trip on stop times impls
        stopTimes.forEach(stopTime -> stopTime.setTrip(tripImpl));

        return tripImpl;
    }

    public static StopTime map(ch.naviqore.gtfs.schedule.model.StopTime stopTime, LocalDate date) {
        return new StopTimeImpl(map(stopTime.stop()), stopTime.arrival().toLocalDateTime(date),
                stopTime.departure().toLocalDateTime(date), map(stopTime.trip(), date));
    }

    public static SearchIndex.SearchStrategy map(SearchType searchType) {
        return switch (searchType) {
            case STARTS_WITH -> SearchIndex.SearchStrategy.STARTS_WITH;
            case ENDS_WITH -> SearchIndex.SearchStrategy.ENDS_WITH;
            case CONTAINS -> SearchIndex.SearchStrategy.CONTAINS;
            case EXACT -> SearchIndex.SearchStrategy.EXACT;
        };
    }

    public static Walk createWalk(int distance, int duration, WalkType walkType, LocalDateTime departureTime,
                                  LocalDateTime arrivalTime, GeoCoordinate sourceLocation, GeoCoordinate targetLocation,
                                  @Nullable Stop stop) {
        return new WalkImpl(distance, duration, walkType, departureTime, arrivalTime, sourceLocation, targetLocation,
                stop);
    }

    public static Connection map(ch.naviqore.raptor.Connection connection, @Nullable Walk firstMile,
                                 @Nullable Walk lastMile, LocalDate date, GtfsSchedule schedule) {
        List<Leg> legs = new ArrayList<>();

        if (firstMile != null) {
            legs.addFirst(firstMile);
        }

        for (ch.naviqore.raptor.Connection.Leg leg : connection.getLegs()) {
            legs.add(map(leg, date, schedule));
        }

        if (lastMile != null) {
            legs.addLast(lastMile);
        }

        return new ConnectionImpl(legs);
    }

    public static Leg map(ch.naviqore.raptor.Connection.Leg leg, LocalDate date, GtfsSchedule schedule) {
        // TODO: Distance is needed on Footpaths, distance of trip can be estimated based on trip and beeline distance?
        int distance = 0;
        int duration = leg.arrivalTime() - leg.departureTime();
        Stop sourceStop = map(schedule.getStops().get(leg.fromStopId()));
        Stop targetStop = map(schedule.getStops().get(leg.toStopId()));
        return switch (leg.type()) {
            case WALK_TRANSFER -> new TransferImpl(distance, duration, toLocalDateTime(leg.departureTime(), date),
                    toLocalDateTime(leg.arrivalTime(), date), sourceStop, targetStop);
            // TODO: Refactor Raptor and extract interfaces. Put Trip id on leg, then stop time can be found.
            case ROUTE -> new PublicTransitLegImpl(distance, duration, null, null, null);
        };
    }

    private static LocalDateTime toLocalDateTime(int secondsOfDay, LocalDate date) {
        return new ServiceDayTime(secondsOfDay).toLocalDateTime(date);
    }
}
