package org.naviqore.service.gtfs.raptor;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.Nullable;
import org.naviqore.gtfs.schedule.model.GtfsSchedule;
import org.naviqore.gtfs.schedule.type.*;
import org.naviqore.raptor.QueryConfig;
import org.naviqore.service.*;
import org.naviqore.service.config.ConnectionQueryConfig;
import org.naviqore.utils.search.SearchIndex;
import org.naviqore.utils.spatial.GeoCoordinate;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

@NoArgsConstructor(access = AccessLevel.NONE)
public final class TypeMapper {

    private static final int SECONDS_IN_DAY = 86400;

    public static Stop map(org.naviqore.gtfs.schedule.model.Stop stop) {
        if (stop == null) {
            return null;
        }

        return new GtfsRaptorStop(stop.getId(), stop.getName(), stop.getCoordinate());
    }

    public static Route map(org.naviqore.gtfs.schedule.model.Route route) {
        return new GtfsRaptorRoute(route.getId(), route.getLongName(), route.getShortName(), map(route.getType()),
                route.getType().getDescription(), route.getAgency().name());
    }

    public static Trip map(org.naviqore.gtfs.schedule.model.Trip trip, LocalDate date) {
        // create stop times
        List<GtfsRaptorStopTime> stopTimes = trip.getStopTimes()
                .stream()
                .map(stopTime -> new GtfsRaptorStopTime(map(stopTime.stop()), stopTime.arrival().toLocalDateTime(date),
                        stopTime.departure().toLocalDateTime(date)))
                .toList();

        // initialize trip, needs a cast to stop times from stop time impl (list)
        GtfsRaptorTrip gtfsRaptorTrip = new GtfsRaptorTrip(trip.getId(), trip.getHeadSign(), map(trip.getRoute()),
                stopTimes.stream().map(stopTime -> (StopTime) stopTime).toList(),
                trip.getBikesAllowed() == BikeInformation.ALLOWED,
                trip.getWheelchairAccessible() == AccessibilityInformation.ACCESSIBLE);

        // set trip on stop times impls
        stopTimes.forEach(stopTime -> stopTime.setTrip(gtfsRaptorTrip));

        return gtfsRaptorTrip;
    }

    public static StopTime map(org.naviqore.gtfs.schedule.model.StopTime stopTime, LocalDate date) {
        return new GtfsRaptorStopTime(map(stopTime.stop()), stopTime.arrival().toLocalDateTime(date),
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

    public static Connection map(org.naviqore.raptor.Connection connection, @Nullable Leg firstMile,
                                 @Nullable Leg lastMile, GtfsSchedule schedule) {
        List<Leg> legs = new ArrayList<>();

        if (firstMile != null) {
            legs.addFirst(firstMile);
        }

        for (org.naviqore.raptor.Leg leg : connection.getLegs()) {
            legs.add(map(leg, schedule));
        }

        if (lastMile != null) {
            legs.addLast(lastMile);
        }

        return new GtfsRaptorConnection(legs);
    }

    public static Leg map(org.naviqore.raptor.Leg leg, GtfsSchedule schedule) {
        int duration = (int) Duration.between(leg.getDepartureTime(), leg.getArrivalTime()).toSeconds();
        Stop sourceStop = map(schedule.getStops().get(leg.getFromStopId()));
        Stop targetStop = map(schedule.getStops().get(leg.getToStopId()));
        int distance = (int) Math.round(sourceStop.getCoordinate().distanceTo(targetStop.getCoordinate()));

        return switch (leg.getType()) {
            case WALK_TRANSFER ->
                    new GtfsRaptorTransfer(distance, duration, leg.getDepartureTime(), leg.getArrivalTime(), sourceStop,
                            targetStop);
            case ROUTE -> createPublicTransitLeg(leg, schedule, distance);
        };
    }

    public static QueryConfig map(ConnectionQueryConfig config) {
        return new QueryConfig(config.getMaximumWalkingDuration(), config.getMinimumTransferDuration(),
                config.getMaximumTransferNumber(), config.getMaximumTravelTime(), config.isWheelchairAccessible(),
                config.isBikeAllowed(), map(config.getTravelModes()));
    }

    public static org.naviqore.raptor.TimeType map(TimeType timeType) {
        return switch (timeType) {
            case DEPARTURE -> org.naviqore.raptor.TimeType.DEPARTURE;
            case ARRIVAL -> org.naviqore.raptor.TimeType.ARRIVAL;
        };
    }

    public static EnumSet<org.naviqore.raptor.TravelMode> map(EnumSet<TravelMode> travelModes) {
        if (travelModes == null || travelModes.isEmpty()) {
            return EnumSet.allOf(org.naviqore.raptor.TravelMode.class);
        }

        EnumSet<org.naviqore.raptor.TravelMode> raptorTravelModes = EnumSet.noneOf(
                org.naviqore.raptor.TravelMode.class);
        for (TravelMode travelMode : travelModes) {
            raptorTravelModes.add(org.naviqore.raptor.TravelMode.valueOf(travelMode.name()));
        }

        return raptorTravelModes;
    }

    private static TravelMode map(RouteType routeType) {
        DefaultRouteType defaultRouteType = RouteTypeMapper.map(routeType);
        return switch (defaultRouteType) {
            case BUS, TROLLEYBUS -> TravelMode.BUS;
            case TRAM, CABLE_TRAM -> TravelMode.TRAM;
            case RAIL, MONORAIL -> TravelMode.RAIL;
            case FERRY -> TravelMode.SHIP;
            case SUBWAY -> TravelMode.SUBWAY;
            case AERIAL_LIFT -> TravelMode.AERIAL_LIFT;
            case FUNICULAR -> TravelMode.FUNICULAR;
        };
    }

    public static Walk createWalk(int distance, int duration, WalkType walkType, LocalDateTime departureTime,
                                  LocalDateTime arrivalTime, GeoCoordinate sourceLocation, GeoCoordinate targetLocation,
                                  @Nullable Stop stop) {
        return new GtfsRaptorWalk(distance, duration, walkType, departureTime, arrivalTime, sourceLocation,
                targetLocation, stop);
    }

    public static Transfer createTransfer(int distance, int duration, LocalDateTime departureTime,
                                          LocalDateTime arrivalTime, Stop sourceStop, Stop targetStop) {
        return new GtfsRaptorTransfer(distance, duration, departureTime, arrivalTime, sourceStop, targetStop);
    }

    private static PublicTransitLeg createPublicTransitLeg(org.naviqore.raptor.Leg leg, GtfsSchedule schedule,
                                                           int distance) {
        org.naviqore.gtfs.schedule.model.Trip gtfsTrip = schedule.getTrips().get(leg.getTripId());
        LocalDate serviceDay = getServiceDay(leg, gtfsTrip);
        int duration = (int) Duration.between(leg.getDepartureTime(), leg.getArrivalTime()).toSeconds();
        Trip trip = map(gtfsTrip, serviceDay);

        assert gtfsTrip.getStopTimes().size() == trip.getStopTimes()
                .size() : "GTFS trip and trip implementation in service must have the same number of stop times.";

        // find departure and arrival stop time in stop sequence of trip
        StopTime departure = null;
        StopTime arrival = null;
        for (int i = 0; i < gtfsTrip.getStopTimes().size(); i++) {
            var gtfsStopTime = gtfsTrip.getStopTimes().get(i);
            // if the fromStop id and the departure time matches, set the departure stop time
            if (gtfsStopTime.stop().getId().equals(leg.getFromStopId()) && gtfsStopTime.departure()
                    .toLocalTime()
                    .equals(leg.getDepartureTime().toLocalTime())) {
                departure = trip.getStopTimes().get(i);
                continue;
            }

            // if the toStop id and the arrival time matches, set the arrival stop time
            if (gtfsStopTime.stop().getId().equals(leg.getToStopId()) && gtfsStopTime.arrival()
                    .toLocalTime()
                    .equals(leg.getArrivalTime().toLocalTime())) {
                arrival = trip.getStopTimes().get(i);
                break;
            }
        }

        assert departure != null : "Departure stop time cannot be null";
        assert arrival != null : "Arrival stop time cannot be null";

        return new GtfsRaptorPublicTransitLeg(distance, duration, trip, departure, arrival);
    }

    private static LocalDate getServiceDay(org.naviqore.raptor.Leg leg, org.naviqore.gtfs.schedule.model.Trip trip) {
        String StopId = leg.getFromStopId();
        LocalTime departureTime = leg.getDepartureTime().toLocalTime();

        for (org.naviqore.gtfs.schedule.model.StopTime stopTime : trip.getStopTimes()) {
            if (stopTime.stop().getId().equals(StopId) && stopTime.departure().toLocalTime().equals(departureTime)) {
                int dayShift = stopTime.departure().getTotalSeconds() / SECONDS_IN_DAY;
                return leg.getDepartureTime().toLocalDate().minusDays(dayShift);
            }
        }

        throw new IllegalStateException("Could not find service day for leg");
    }
}
