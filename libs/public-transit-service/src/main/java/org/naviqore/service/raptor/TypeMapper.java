package org.naviqore.service.raptor;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.naviqore.raptor.QueryConfig;
import org.naviqore.service.*;
import org.naviqore.service.config.ConnectionQueryConfig;
import org.naviqore.timetable.model.Timetable;
import org.naviqore.timetable.type.AccessibilityInformation;
import org.naviqore.timetable.type.BikeInformation;
import org.naviqore.timetable.type.ServiceDayTime;
import org.naviqore.utils.search.SearchIndex;
import org.naviqore.utils.spatial.GeoCoordinate;

import java.time.*;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

@NoArgsConstructor(access = AccessLevel.NONE)
public final class TypeMapper {

    private static final ServiceDayTime START_OF_SERVICE_DAY = new ServiceDayTime(0);

    public static Stop map(org.naviqore.timetable.model.Stop stop) {
        if (stop == null) {
            return null;
        }

        return new RaptorServiceStop(stop.getId(), stop.getName(), stop.getCoordinate());
    }

    public static Route map(org.naviqore.timetable.model.Route route) {
        return new RaptorServiceRoute(route.getId(), route.getLongName(), route.getShortName(),
                map(route.getTravelMode()), route.getRouteTypeDescription(), route.getAgency().name());
    }

    public static Trip map(org.naviqore.timetable.model.Trip trip, LocalDate date) {
        // create stop times, counted from the start of the service day, which is the same for all of them
        ZoneId zoneId = trip.getRoute().getAgency().timezone();
        Instant startOfServiceDay = START_OF_SERVICE_DAY.toZonedDateTime(date, zoneId).toInstant();
        List<RaptorServiceStopTime> stopTimes = trip.getStopTimes()
                .stream()
                .map(stopTime -> new RaptorServiceStopTime(map(stopTime.stop()),
                        toOffsetDateTime(stopTime.arrival(), startOfServiceDay, zoneId),
                        toOffsetDateTime(stopTime.departure(), startOfServiceDay, zoneId)))
                .toList();

        // initialize trip, needs a cast to stop times from stop time impl (list)
        RaptorServiceTrip raptorServiceTrip = new RaptorServiceTrip(trip.getId(), trip.getHeadSign(),
                map(trip.getRoute()), stopTimes.stream().map(stopTime -> (StopTime) stopTime).toList(),
                trip.getBikesAllowed() == BikeInformation.ALLOWED,
                trip.getWheelchairAccessible() == AccessibilityInformation.ACCESSIBLE);

        // set trip on stop times
        stopTimes.forEach(stopTime -> stopTime.setTrip(raptorServiceTrip));

        return raptorServiceTrip;
    }

    private static OffsetDateTime toOffsetDateTime(ServiceDayTime time, Instant startOfServiceDay, ZoneId zoneId) {
        return OffsetDateTime.ofInstant(startOfServiceDay.plusSeconds(time.getTotalSeconds()), zoneId);
    }

    public static StopTime map(org.naviqore.timetable.model.StopTime stopTime, LocalDate date) {
        ZoneId zoneId = stopTime.trip().getRoute().getAgency().timezone();
        return new RaptorServiceStopTime(map(stopTime.stop()), stopTime.arrival().toOffsetDateTime(date, zoneId),
                stopTime.departure().toOffsetDateTime(date, zoneId), map(stopTime.trip(), date));
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
                                 @Nullable Leg lastMile, Timetable schedule) {
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

        return new RaptorServiceConnection(legs);
    }

    public static Leg map(org.naviqore.raptor.Leg leg, Timetable schedule) {
        int duration = (int) Duration.between(leg.getDepartureTime(), leg.getArrivalTime()).toSeconds();
        Stop sourceStop = map(schedule.getStops().get(leg.getFromStopId()));
        Stop targetStop = map(schedule.getStops().get(leg.getToStopId()));
        int distance = (int) Math.round(sourceStop.getCoordinate().distanceTo(targetStop.getCoordinate()));

        return switch (leg.getType()) {
            case WALK_TRANSFER ->
                    new RaptorServiceTransfer(distance, duration, leg.getDepartureTime(), leg.getArrivalTime(),
                            sourceStop, targetStop);
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

    public static org.naviqore.timetable.type.TimeType mapToTimetable(TimeType timeType) {
        return switch (timeType) {
            case DEPARTURE -> org.naviqore.timetable.type.TimeType.DEPARTURE;
            case ARRIVAL -> org.naviqore.timetable.type.TimeType.ARRIVAL;
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

    private static TravelMode map(org.naviqore.timetable.type.TravelMode travelMode) {
        return TravelMode.valueOf(travelMode.name());
    }

    public static Walk createWalk(int distance, int duration, WalkType walkType, OffsetDateTime departureTime,
                                  OffsetDateTime arrivalTime, GeoCoordinate sourceLocation,
                                  GeoCoordinate targetLocation, @Nullable Stop stop) {
        return new RaptorServiceWalk(distance, duration, walkType, departureTime, arrivalTime, sourceLocation,
                targetLocation, stop);
    }

    public static Transfer createTransfer(int distance, int duration, OffsetDateTime departureTime,
                                          OffsetDateTime arrivalTime, Stop sourceStop, Stop targetStop) {
        return new RaptorServiceTransfer(distance, duration, departureTime, arrivalTime, sourceStop, targetStop);
    }

    private static PublicTransitLeg createPublicTransitLeg(org.naviqore.raptor.Leg leg, Timetable schedule,
                                                           int distance) {
        org.naviqore.timetable.model.Trip timetableTrip = schedule.getTrips().get(leg.getTripId());
        List<? extends org.naviqore.timetable.model.StopTime> stopTimes = timetableTrip.getStopTimes();
        ZoneId zoneId = timetableTrip.getRoute().getAgency().timezone();
        Instant departureTime = leg.getDepartureTime().toInstant();
        int duration = (int) Duration.between(leg.getDepartureTime(), leg.getArrivalTime()).toSeconds();

        // find departure and arrival stop time in stop sequence of trip: the departure is a stop time at the from stop
        // that departs at the departure time of the leg on some service day, the arrival the first later stop time at
        // the to stop that arrives the duration of the leg later. Times are compared as instants, since on the day of a
        // DST change the time of a stop time is not the time on the clock.
        for (int departureIdx = 0; departureIdx < stopTimes.size(); departureIdx++) {
            org.naviqore.timetable.model.StopTime departure = stopTimes.get(departureIdx);
            if (!departure.stop().getId().equals(leg.getFromStopId())) {
                continue;
            }

            LocalDate serviceDay = getServiceDay(departure.departure(), departureTime, zoneId);
            if (!departure.departure().toZonedDateTime(serviceDay, zoneId).toInstant().equals(departureTime)) {
                continue;
            }

            for (int arrivalIdx = departureIdx + 1; arrivalIdx < stopTimes.size(); arrivalIdx++) {
                org.naviqore.timetable.model.StopTime arrival = stopTimes.get(arrivalIdx);
                // both times count the seconds from the start of the service day, so they differ by the duration
                if (arrival.stop().getId().equals(leg.getToStopId()) && arrival.arrival()
                        .getTotalSeconds() - departure.departure().getTotalSeconds() == duration) {
                    Trip trip = map(timetableTrip, serviceDay);
                    return new RaptorServicePublicTransitLeg(distance, duration, trip,
                            trip.getStopTimes().get(departureIdx), trip.getStopTimes().get(arrivalIdx));
                }
            }
        }

        throw new IllegalStateException(
                String.format("Trip %s does not run from stop %s at %s to stop %s at %s", leg.getTripId(),
                        leg.getFromStopId(), leg.getDepartureTime(), leg.getToStopId(), leg.getArrivalTime()));
    }

    /**
     * Gets the only service day on which the time can be the given instant; whether it is, the caller checks. The time
     * counts from "noon minus 12h" of its service day, so twelve hours after the instant minus the time is noon of that
     * day.
     */
    private static LocalDate getServiceDay(ServiceDayTime time, Instant instant, ZoneId zoneId) {
        return instant.minusSeconds(time.getTotalSeconds()).atZone(zoneId).plusHours(12).toLocalDate();
    }
}
