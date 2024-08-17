package ch.naviqore.service.gtfs.raptor;

import ch.naviqore.gtfs.schedule.model.GtfsSchedule;
import ch.naviqore.gtfs.schedule.type.DefaultRouteType;
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
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

@NoArgsConstructor(access = AccessLevel.NONE)
final class TypeMapper {

    private static final int SECONDS_IN_DAY = 86400;

    public static Stop map(ch.naviqore.gtfs.schedule.model.Stop stop) {
        if (stop == null) {
            return null;
        }

        return new GtfsRaptorStop(stop.getId(), stop.getName(), stop.getCoordinate());
    }

    public static Route map(ch.naviqore.gtfs.schedule.model.Route route) {
        return new GtfsRaptorRoute(route.getId(), route.getLongName(), route.getShortName(),
                route.getType().getDescription(), route.getAgency().name());
    }

    public static Trip map(ch.naviqore.gtfs.schedule.model.Trip trip, LocalDate date) {
        // create stop times
        List<GtfsRaptorStopTime> stopTimes = trip.getStopTimes()
                .stream()
                .map(stopTime -> new GtfsRaptorStopTime(map(stopTime.stop()), stopTime.arrival().toLocalDateTime(date),
                        stopTime.departure().toLocalDateTime(date)))
                .toList();

        // initialize trip, needs a cast to stop times from stop time impl (list)
        GtfsRaptorTrip gtfsRaptorTrip = new GtfsRaptorTrip(trip.getId(), trip.getHeadSign(), map(trip.getRoute()),
                stopTimes.stream().map(stopTime -> (StopTime) stopTime).toList());

        // set trip on stop times impls
        stopTimes.forEach(stopTime -> stopTime.setTrip(gtfsRaptorTrip));

        return gtfsRaptorTrip;
    }

    public static StopTime map(ch.naviqore.gtfs.schedule.model.StopTime stopTime, LocalDate date) {
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

    public static Walk createWalk(int distance, int duration, WalkType walkType, LocalDateTime departureTime,
                                  LocalDateTime arrivalTime, GeoCoordinate sourceLocation, GeoCoordinate targetLocation,
                                  @Nullable Stop stop) {
        return new GtfsRaptorWalk(distance, duration, walkType, departureTime, arrivalTime, sourceLocation,
                targetLocation, stop);
    }

    public static Connection map(ch.naviqore.raptor.Connection connection, @Nullable Walk firstMile,
                                 @Nullable Walk lastMile, GtfsSchedule schedule) {
        List<Leg> legs = new ArrayList<>();

        if (firstMile != null) {
            legs.addFirst(firstMile);
        }

        for (ch.naviqore.raptor.Leg leg : connection.getLegs()) {
            legs.add(map(leg, schedule));
        }

        if (lastMile != null) {
            legs.addLast(lastMile);
        }

        return new GtfsRaptorConnection(legs);
    }

    public static Leg map(ch.naviqore.raptor.Leg leg, GtfsSchedule schedule) {
        int duration = (int) Duration.between(leg.getDepartureTime(), leg.getArrivalTime()).toSeconds();
        Stop sourceStop = map(schedule.getStops().get(leg.getFromStopId()));
        Stop targetStop = map(schedule.getStops().get(leg.getToStopId()));
        int distance = (int) Math.round(sourceStop.getLocation().distanceTo(targetStop.getLocation()));

        return switch (leg.getType()) {
            case WALK_TRANSFER ->
                    new GtfsRaptorTransfer(distance, duration, leg.getDepartureTime(), leg.getArrivalTime(), sourceStop,
                            targetStop);
            case ROUTE -> createPublicTransitLeg(leg, schedule, distance);
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

    public static EnumSet<ch.naviqore.raptor.TravelMode> map(EnumSet<TravelMode> travelModes) {
        EnumSet<ch.naviqore.raptor.TravelMode> raptorTravelModes = EnumSet.noneOf(ch.naviqore.raptor.TravelMode.class);
        for (TravelMode travelMode : travelModes) {
            raptorTravelModes.add(ch.naviqore.raptor.TravelMode.valueOf(travelMode.name()));
        }
        return raptorTravelModes;
    }

    public static EnumSet<DefaultRouteType> mapToRouteTypes(EnumSet<ch.naviqore.raptor.TravelMode> travelModes) {
        EnumSet<DefaultRouteType> routeTypes = EnumSet.noneOf(DefaultRouteType.class);
        for (ch.naviqore.raptor.TravelMode travelMode : travelModes) {
            routeTypes.addAll(map(travelMode));
        }
        return routeTypes;
    }

    public static EnumSet<DefaultRouteType> map(ch.naviqore.raptor.TravelMode travelMode) {
        if (travelMode.equals(ch.naviqore.raptor.TravelMode.BUS)) {
            return EnumSet.of(DefaultRouteType.BUS, DefaultRouteType.TROLLEYBUS);
        } else if (travelMode.equals(ch.naviqore.raptor.TravelMode.TRAM)) {
            return EnumSet.of(DefaultRouteType.TRAM, DefaultRouteType.CABLE_TRAM);
        } else if (travelMode.equals(ch.naviqore.raptor.TravelMode.RAIL)) {
            return EnumSet.of(DefaultRouteType.RAIL, DefaultRouteType.MONORAIL);
        } else if (travelMode.equals(ch.naviqore.raptor.TravelMode.SHIP)) {
            return EnumSet.of(DefaultRouteType.FERRY);
        } else if (travelMode.equals(ch.naviqore.raptor.TravelMode.SUBWAY)) {
            return EnumSet.of(DefaultRouteType.SUBWAY);
        } else if (travelMode.equals(ch.naviqore.raptor.TravelMode.AERIAL_LIFT)) {
            return EnumSet.of(DefaultRouteType.AERIAL_LIFT);
        } else if (travelMode.equals(ch.naviqore.raptor.TravelMode.FUNICULAR)) {
            return EnumSet.of(DefaultRouteType.FUNICULAR);
        } else {
            // should never happen
            throw new IllegalArgumentException("Travel mode not supported");
        }
    }

    private static Leg createPublicTransitLeg(ch.naviqore.raptor.Leg leg, GtfsSchedule schedule, int distance) {
        ch.naviqore.gtfs.schedule.model.Trip gtfsTrip = schedule.getTrips().get(leg.getTripId());
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

    private static LocalDate getServiceDay(ch.naviqore.raptor.Leg leg, ch.naviqore.gtfs.schedule.model.Trip trip) {
        String StopId = leg.getFromStopId();
        LocalTime departureTime = leg.getDepartureTime().toLocalTime();
        for (ch.naviqore.gtfs.schedule.model.StopTime stopTime : trip.getStopTimes()) {
            if (stopTime.stop().getId().equals(StopId) && stopTime.departure().toLocalTime().equals(departureTime)) {
                int dayShift = stopTime.departure().getTotalSeconds() / SECONDS_IN_DAY;
                return leg.getDepartureTime().toLocalDate().minusDays(dayShift);
            }
        }
        throw new IllegalStateException("Could not find service day for leg");
    }
}
