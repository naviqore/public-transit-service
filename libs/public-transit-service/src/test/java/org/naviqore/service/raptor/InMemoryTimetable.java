package org.naviqore.service.raptor;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.naviqore.timetable.model.*;
import org.naviqore.timetable.model.Calendar;
import org.naviqore.timetable.type.*;
import org.naviqore.utils.spatial.GeoCoordinate;

import java.time.*;
import java.util.*;

/**
 * Timetable held in memory, without any schedule format behind it: one tram route from Alpha via Bravo to Charlie, with
 * a trip at 08:00 and at 09:00 on every day of 2026.
 * <p>
 * Each stop, route, trip and calendar exists once, so identity equality is equality by ID.
 */
public class InMemoryTimetable implements Timetable {

    public static final ZoneId ZONE_ID = ZoneId.of("Europe/Zurich");
    public static final LocalDate START_DATE = LocalDate.of(2026, Month.JANUARY, 1);
    public static final LocalDate END_DATE = LocalDate.of(2026, Month.DECEMBER, 31);

    private final Map<String, InMemoryCalendar> calendars = new LinkedHashMap<>();
    private final Map<String, InMemoryStop> stops = new LinkedHashMap<>();
    private final Map<String, InMemoryRoute> routes = new LinkedHashMap<>();
    private final Map<String, InMemoryTrip> trips = new LinkedHashMap<>();

    public InMemoryTimetable() {
        InMemoryStop alpha = addStop("A", "Alpha", 47.3769, 8.5417);
        InMemoryStop bravo = addStop("B", "Bravo", 47.3800, 8.5500);
        InMemoryStop charlie = addStop("C", "Charlie", 47.3850, 8.5600);

        InMemoryCalendar daily = new InMemoryCalendar("daily", START_DATE, END_DATE);
        calendars.put(daily.getId(), daily);

        InMemoryRoute route = new InMemoryRoute("R1", new InMemoryAgency("Example Transit", ZONE_ID), "1",
                "Alpha - Charlie");
        routes.put(route.getId(), route);

        for (int hour = 8; hour <= 9; hour++) {
            InMemoryTrip trip = new InMemoryTrip("T" + (hour - 7), route, daily);
            trip.addStopTime(alpha, hour, 0, 0);
            trip.addStopTime(bravo, hour, 10, 11);
            trip.addStopTime(charlie, hour, 20, 20);
            route.getTrips().add(trip);
            trips.put(trip.getId(), trip);
        }
    }

    private InMemoryStop addStop(String id, String name, double latitude, double longitude) {
        InMemoryStop stop = new InMemoryStop(id, name, new GeoCoordinate(latitude, longitude));
        stops.put(id, stop);

        return stop;
    }

    @Override
    public Map<String, ? extends Calendar> getCalendars() {
        return calendars;
    }

    @Override
    public Map<String, ? extends Stop> getStops() {
        return stops;
    }

    @Override
    public Map<String, ? extends Route> getRoutes() {
        return routes;
    }

    @Override
    public Map<String, ? extends Trip> getTrips() {
        return trips;
    }

    @Override
    public LocalDate getStartDate() {
        return START_DATE;
    }

    @Override
    public LocalDate getEndDate() {
        return END_DATE;
    }

    @Override
    public boolean hasTripAccessibilityInformation() {
        return false;
    }

    @Override
    public boolean hasTripBikeInformation() {
        return false;
    }

    @Override
    public List<? extends StopTime> getStopTimes(String stopId, OffsetDateTime from, OffsetDateTime to,
                                                 TimeType timeType) {
        List<ScheduledEvent> events = new ArrayList<>();
        for (InMemoryTrip trip : trips.values()) {
            for (StopTime stopTime : trip.getStopTimes()) {
                if (!stopTime.stop().getId().equals(stopId)) {
                    continue;
                }
                ServiceDayTime time = timeType == TimeType.DEPARTURE ? stopTime.departure() : stopTime.arrival();
                for (LocalDate date = from.toLocalDate().minusDays(1); !date.isAfter(
                        to.toLocalDate()); date = date.plusDays(1)) {
                    Instant instant = time.toZonedDateTime(date, ZONE_ID).toInstant();
                    if (trip.getCalendar().isServiceAvailable(date) && !instant.isBefore(
                            from.toInstant()) && instant.isBefore(to.toInstant())) {
                        events.add(new ScheduledEvent(stopTime, instant));
                    }
                }
            }
        }

        return events.stream()
                .sorted(Comparator.comparing(ScheduledEvent::physicalInstant))
                .map(ScheduledEvent::stopTime)
                .toList();
    }

    @Override
    public List<? extends Stop> getStopWithChildren(String stopId) {
        InMemoryStop stop = stops.get(stopId);
        if (stop == null) {
            throw new IllegalArgumentException("Stop " + stopId + " not found");
        }

        return List.of(stop);
    }

    private record ScheduledEvent(StopTime stopTime, Instant physicalInstant) {
    }

    private record InMemoryAgency(String name, ZoneId timezone) implements Agency {
    }

    private record InMemoryCalendar(String id, LocalDate startDate, LocalDate endDate) implements Calendar {

        @Override
        public String getId() {
            return id;
        }

        @Override
        public boolean isServiceAvailable(LocalDate date) {
            return !date.isBefore(startDate) && !date.isAfter(endDate);
        }
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    @Getter
    private static final class InMemoryStop implements Stop {

        private final String id;
        private final String name;
        private final GeoCoordinate coordinate;

        @Override
        public Optional<? extends Stop> getParent() {
            return Optional.empty();
        }

        @Override
        public List<? extends Stop> getChildren() {
            return List.of();
        }

        @Override
        public List<? extends Transfer> getTransfers() {
            return List.of();
        }
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    @Getter
    private static final class InMemoryRoute implements Route {

        private final String id;
        private final Agency agency;
        private final String shortName;
        private final String longName;
        private final List<Trip> trips = new ArrayList<>();

        @Override
        public TravelMode getTravelMode() {
            return TravelMode.TRAM;
        }

        @Override
        public String getRouteTypeDescription() {
            return "Tram";
        }
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    @Getter
    private static final class InMemoryTrip implements Trip {

        private final String id;
        private final Route route;
        private final Calendar calendar;
        private final List<StopTime> stopTimes = new ArrayList<>();

        private void addStopTime(Stop stop, int hour, int arrivalMinute, int departureMinute) {
            stopTimes.add(new InMemoryStopTime(stop, this, new ServiceDayTime(hour, arrivalMinute, 0),
                    new ServiceDayTime(hour, departureMinute, 0)));
        }

        @Override
        public String getHeadSign() {
            return stopTimes.getLast().stop().getName();
        }

        @Override
        public AccessibilityInformation getWheelchairAccessible() {
            return AccessibilityInformation.UNKNOWN;
        }

        @Override
        public BikeInformation getBikesAllowed() {
            return BikeInformation.UNKNOWN;
        }
    }

    private record InMemoryStopTime(Stop stop, Trip trip, ServiceDayTime arrival,
                                    ServiceDayTime departure) implements StopTime {
    }

}
