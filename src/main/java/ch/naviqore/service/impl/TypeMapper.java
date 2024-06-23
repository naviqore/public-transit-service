package ch.naviqore.service.impl;

import ch.naviqore.gtfs.schedule.model.GtfsSchedule;
import ch.naviqore.gtfs.schedule.type.ServiceDayTime;
import ch.naviqore.raptor.QueryConfig;
import ch.naviqore.service.*;
import ch.naviqore.service.config.ConnectionQueryConfig;
import ch.naviqore.utils.search.SearchIndex;
import ch.naviqore.utils.spatial.GeoCoordinate;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
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

        for (ch.naviqore.raptor.Leg leg : connection.getLegs()) {
            legs.add(map(leg, date, schedule));
        }

        if (lastMile != null) {
            legs.addLast(lastMile);
        }

        return new ConnectionImpl(legs);
    }

    public static Leg map(ch.naviqore.raptor.Leg leg, LocalDate date, GtfsSchedule schedule) {
        int duration = (int) Duration.between(leg.getDepartureTime(), leg.getArrivalTime()).toSeconds();
        Stop sourceStop = map(schedule.getStops().get(leg.getFromStopId()));
        Stop targetStop = map(schedule.getStops().get(leg.getToStopId()));
        int distance = (int) Math.round(sourceStop.getLocation().distanceTo(targetStop.getLocation()));

        return switch (leg.getType()) {
            case WALK_TRANSFER ->
                    new TransferImpl(distance, duration, leg.getDepartureTime(), leg.getArrivalTime(), sourceStop,
                            targetStop);
            case ROUTE -> createPublicTransitLeg(leg, date, schedule, distance);
        };
    }

    public static QueryConfig map(ConnectionQueryConfig config) {
        return new QueryConfig(config.getMaximumWalkingDuration(), config.getMinimumTransferDuration(),
                config.getMaximumTransferNumber(), config.getMaximumTravelTime());
    }

    public static ch.naviqore.raptor.TimeType map(TimeType timeType) {
        return switch (timeType) {
            case DEPARTURE -> ch.naviqore.raptor.TimeType.DEPARTURE;
            case ARRIVAL -> ch.naviqore.raptor.TimeType.ARRIVAL;
        };
    }

    private static Leg createPublicTransitLeg(ch.naviqore.raptor.Leg leg, LocalDate date, GtfsSchedule schedule,
                                              int distance) {
        int duration = (int) Duration.between(leg.getDepartureTime(), leg.getArrivalTime()).toSeconds();
        ch.naviqore.gtfs.schedule.model.Trip gtfsTrip = schedule.getTrips().get(leg.getTripId());
        Trip trip = map(gtfsTrip, date);

        assert gtfsTrip.getStopTimes().size() == trip.getStopTimes()
                .size() : "GTFS trip and trip implementation in service must have the same number of stop times.";

        // find departure and arrival stop time in stop sequence of trip
        StopTime departure = null;
        StopTime arrival = null;
        for (int i = 0; i < gtfsTrip.getStopTimes().size(); i++) {
            var gtfsStopTime = gtfsTrip.getStopTimes().get(i);
            // if the from stop id and the departure time matches, set the departure stop time
            if (gtfsStopTime.stop().getId().equals(leg.getFromStopId()) && gtfsStopTime.departure()
                    .getTotalSeconds() == getSecondsOfDay(leg.getDepartureTime(), date)) {
                departure = trip.getStopTimes().get(i);
                continue;
            }

            // if the to stop id and the arrival time matches, set the arrival stop time
            if (gtfsStopTime.stop().getId().equals(leg.getToStopId()) && gtfsStopTime.arrival()
                    .getTotalSeconds() == getSecondsOfDay(leg.getArrivalTime(), date)) {
                arrival = trip.getStopTimes().get(i);
                break;
            }
        }

        assert departure != null : "Departure stop time cannot be null";
        assert arrival != null : "Arrival stop time cannot be null";

        return new PublicTransitLegImpl(distance, duration, trip, departure, arrival);
    }

    private static int getSecondsOfDay(LocalDateTime time, LocalDate refDay) {
        return (int) Duration.between(refDay.atStartOfDay(), time).toSeconds();
    }
}
