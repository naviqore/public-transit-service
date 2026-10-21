package org.naviqore.service.gtfs.raptor;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.naviqore.gtfs.schedule.type.*;
import org.naviqore.raptor.QueryConfig;
import org.naviqore.service.*;
import org.naviqore.service.TimeType;
import org.naviqore.service.config.ConnectionQueryConfig;
import org.naviqore.service.spi.ScheduledStopTime;
import org.naviqore.service.spi.ScheduledTrip;
import org.naviqore.service.spi.TransitSchedule;
import org.naviqore.utils.search.SearchIndex;
import org.naviqore.utils.spatial.GeoCoordinate;

import java.time.*;
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

    public static Trip map(ScheduledTrip trip, LocalDate date, TransitSchedule schedule) {
        // create stop times
        ZoneId zoneId = trip.zoneId();
        List<GtfsRaptorStopTime> stopTimes = trip.stopTimes()
                .stream()
                .map(stopTime -> new GtfsRaptorStopTime(schedule.getStop(stopTime.stopId()),
                        toOffsetDateTime(stopTime.arrival(), date, zoneId),
                        toOffsetDateTime(stopTime.departure(), date, zoneId)))
                .toList();

        // initialize trip, needs a cast to stop times from stop time impl (list)
        GtfsRaptorTrip gtfsRaptorTrip = new GtfsRaptorTrip(trip.id(), trip.headSign(),
                schedule.getRoute(trip.routeId()), stopTimes.stream().map(stopTime -> (StopTime) stopTime).toList(),
                trip.bikesAllowed(), trip.wheelchairAccessible());

        // set trip on stop times
        stopTimes.forEach(stopTime -> stopTime.setTrip(gtfsRaptorTrip));

        return gtfsRaptorTrip;
    }

    public static StopTime map(ScheduledStopTime stopTime, LocalDate date, TransitSchedule schedule) {
        ScheduledTrip trip = schedule.getTrip(stopTime.tripId());
        ZoneId zoneId = trip.zoneId();
        return new GtfsRaptorStopTime(schedule.getStop(stopTime.stopId()),
                toOffsetDateTime(stopTime.arrival(), date, zoneId),
                toOffsetDateTime(stopTime.departure(), date, zoneId), map(trip, date, schedule));
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
                                 @Nullable Leg lastMile, TransitSchedule schedule) {
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

    public static Leg map(org.naviqore.raptor.Leg leg, TransitSchedule schedule) {
        int duration = (int) Duration.between(leg.getDepartureTime(), leg.getArrivalTime()).toSeconds();
        Stop sourceStop = schedule.getStop(leg.getFromStopId());
        Stop targetStop = schedule.getStop(leg.getToStopId());
        int distance = (int) Math.round(sourceStop.getCoordinate().distanceTo(targetStop.getCoordinate()));

        return switch (leg.getType()) {
            case WALK_TRANSFER ->
                    new GtfsRaptorTransfer(distance, duration, leg.getDepartureTime(), leg.getArrivalTime(), sourceStop,
                            targetStop);
            case ROUTE -> createPublicTransitLeg(leg, schedule, distance);
        };
    }

    public static QueryConfig map(ConnectionQueryConfig config) {
        return QueryConfig.builder()
                .maximumWalkDuration(config.getMaximumWalkDuration())
                .minimumTransferDuration(config.getMinimumTransferDuration())
                .maximumTransfers(config.getMaximumTransfers())
                // RAPTOR does not apply the maximum travel duration as expected by the query definition: it sets the
                // cutoff time when RAPTOR stops progressing, but to work as intended this would require starting the
                // journey immediately, which is not necessarily the case.
                // Set this explicitly so the service's unbounded RAPTOR cutoff does not depend on QueryConfig default.
                .maximumTravelDuration(Integer.MAX_VALUE)
                .wheelchairAccessible(config.isWheelchairAccessible())
                .bikeAccessible(config.isBikeAllowed())
                .allowedTravelModes(map(config.getTravelModes()))
                .build();
    }

    public static org.naviqore.raptor.TimeType mapToRaptor(TimeType timeType) {
        return switch (timeType) {
            case DEPARTURE -> org.naviqore.raptor.TimeType.DEPARTURE;
            case ARRIVAL -> org.naviqore.raptor.TimeType.ARRIVAL;
        };
    }

    public static org.naviqore.gtfs.schedule.type.TimeType mapToGtfs(TimeType timeType) {
        return switch (timeType) {
            case DEPARTURE -> org.naviqore.gtfs.schedule.type.TimeType.DEPARTURE;
            case ARRIVAL -> org.naviqore.gtfs.schedule.type.TimeType.ARRIVAL;
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

    public static Walk createWalk(int distance, int duration, WalkType walkType, OffsetDateTime departureTime,
                                  OffsetDateTime arrivalTime, GeoCoordinate sourceLocation,
                                  GeoCoordinate targetLocation, @Nullable Stop stop) {
        return new GtfsRaptorWalk(distance, duration, walkType, departureTime, arrivalTime, sourceLocation,
                targetLocation, stop);
    }

    public static Transfer createTransfer(int distance, int duration, OffsetDateTime departureTime,
                                          OffsetDateTime arrivalTime, Stop sourceStop, Stop targetStop) {
        return new GtfsRaptorTransfer(distance, duration, departureTime, arrivalTime, sourceStop, targetStop);
    }

    private static PublicTransitLeg createPublicTransitLeg(org.naviqore.raptor.Leg leg, TransitSchedule schedule,
                                                           int distance) {
        ScheduledTrip scheduledTrip = schedule.getTrip(leg.getTripId());
        LocalDate serviceDay = getServiceDay(leg, scheduledTrip);
        int duration = (int) Duration.between(leg.getDepartureTime(), leg.getArrivalTime()).toSeconds();
        Trip trip = map(scheduledTrip, serviceDay, schedule);

        assert scheduledTrip.stopTimes().size() == trip.getStopTimes()
                .size() : "Scheduled trip and trip implementation in service must have the same number of stop times.";

        // find departure and arrival stop time in stop sequence of trip
        StopTime departure = null;
        StopTime arrival = null;
        for (int i = 0; i < scheduledTrip.stopTimes().size(); i++) {
            ScheduledStopTime scheduledStopTime = scheduledTrip.stopTimes().get(i);
            // if the fromStop id and the departure time matches, set the departure stop time
            if (scheduledStopTime.stopId().equals(leg.getFromStopId()) && toLocalTime(
                    scheduledStopTime.departure()).equals(leg.getDepartureTime().toLocalTime())) {
                departure = trip.getStopTimes().get(i);
                continue;
            }

            // if the toStop id and the arrival time matches, set the arrival stop time
            if (scheduledStopTime.stopId().equals(leg.getToStopId()) && toLocalTime(
                    scheduledStopTime.arrival()).equals(leg.getArrivalTime().toLocalTime())) {
                arrival = trip.getStopTimes().get(i);
                break;
            }
        }

        assert departure != null : "Departure stop time cannot be null";
        assert arrival != null : "Arrival stop time cannot be null";

        return new GtfsRaptorPublicTransitLeg(distance, duration, trip, departure, arrival);
    }

    private static LocalDate getServiceDay(org.naviqore.raptor.Leg leg, ScheduledTrip trip) {
        String StopId = leg.getFromStopId();
        LocalTime departureTime = leg.getDepartureTime().toLocalTime();

        for (ScheduledStopTime stopTime : trip.stopTimes()) {
            if (stopTime.stopId().equals(StopId) && toLocalTime(stopTime.departure()).equals(departureTime)) {
                int dayShift = stopTime.departure() / SECONDS_IN_DAY;
                return leg.getDepartureTime().toLocalDate().minusDays(dayShift);
            }
        }

        throw new IllegalStateException("Could not find service day for leg");
    }

    private static OffsetDateTime toOffsetDateTime(int serviceDaySeconds, LocalDate date, ZoneId zoneId) {
        return new ServiceDayTime(serviceDaySeconds).toOffsetDateTime(date, zoneId);
    }

    private static LocalTime toLocalTime(int serviceDaySeconds) {
        return new ServiceDayTime(serviceDaySeconds).toLocalTime();
    }
}
