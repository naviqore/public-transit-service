package org.naviqore.gtfs.schedule.model;

import lombok.Getter;
import lombok.experimental.Accessors;
import org.naviqore.timetable.model.Timetable;
import org.naviqore.timetable.type.AccessibilityInformation;
import org.naviqore.timetable.type.BikeInformation;
import org.naviqore.timetable.type.ServiceDayTime;
import org.naviqore.timetable.type.TimeType;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Function;

/**
 * General Transit Feed Specification (GTFS) schedule
 * <p>
 * This is an immutable class, meaning that once an instance is created, it cannot be modified. Use the
 * {@link GtfsScheduleBuilder} to construct a GTFS schedule instance.
 * <p>
 * The schedule and its model classes implement the {@link Timetable} interfaces, so a GTFS schedule is a timetable
 * without copying any of its data.
 *
 * @author munterfi
 */
@Getter
public class GtfsSchedule implements Timetable {

    private final Map<String, Agency> agencies;
    private final Map<String, Calendar> calendars;
    private final Map<String, Stop> stops;
    private final Map<String, Route> routes;
    private final Map<String, Trip> trips;

    @Accessors(fluent = true)
    private final boolean hasStopAccessibilityInformation;

    @Accessors(fluent = true)
    private final boolean hasTripAccessibilityInformation;

    @Accessors(fluent = true)
    private final boolean hasTripBikeInformation;

    /**
     * Constructs an immutable GTFS schedule.
     * <p>
     * Each map passed to this constructor is copied into an immutable map to prevent further modification and to
     * enhance memory efficiency and thread-safety in a concurrent environment.
     */
    GtfsSchedule(Map<String, Agency> agencies, Map<String, Calendar> calendars, Map<String, Stop> stops,
                 Map<String, Route> routes, Map<String, Trip> trips) {
        this.agencies = Map.copyOf(agencies);
        this.calendars = Map.copyOf(calendars);
        this.stops = Map.copyOf(stops);
        this.routes = Map.copyOf(routes);
        this.trips = Map.copyOf(trips);

        // retrieve accessibility and bike information
        hasStopAccessibilityInformation = this.stops.values()
                .stream()
                .anyMatch(stop -> stop.getWheelchairBoarding() != AccessibilityInformation.UNKNOWN);

        hasTripAccessibilityInformation = this.trips.values()
                .stream()
                .anyMatch(trip -> trip.getWheelchairAccessible() != AccessibilityInformation.UNKNOWN);

        hasTripBikeInformation = this.trips.values()
                .stream()
                .anyMatch(trip -> trip.getBikesAllowed() != BikeInformation.UNKNOWN);
    }

    /**
     * Creates a new GTFS schedule builder.
     *
     * @return A new GTFS schedule builder.
     */
    public static GtfsScheduleBuilder builder() {
        return new GtfsScheduleBuilder();
    }

    /**
     * Retrieves all stop times for a specific stop within a physical time window [from, to).
     * <p>
     * Handles arbitrary Trip durations (>24h) and varying agency timezones.
     *
     * @param stopId   unique identifier of the stop
     * @param from     inclusive start of the physical window
     * @param to       exclusive end of the physical window
     * @param timeType whether to evaluate departure or arrival times
     * @return chronologically sorted list of matching stop times
     */
    @Override
    public List<StopTime> getStopTimes(String stopId, OffsetDateTime from, OffsetDateTime to, TimeType timeType) {
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("to must be after from");
        }

        Stop stop = getStop(stopId);
        List<ScheduledEvent> events = new ArrayList<>();
        final Instant fromInstant = from.toInstant();
        final Instant toInstant = to.toInstant();

        for (StopTime st : stop.getStopTimes()) {
            ServiceDayTime serviceDayTime = switch (timeType) {
                case DEPARTURE -> st.departure();
                case ARRIVAL -> st.arrival();
            };
            ZoneId zone = st.trip().getRoute().getAgency().timezone();

            // calculate the range of service dates that could possibly contain this stop time:
            // - look back from the 'from' instant by the trip's internal offset (total seconds)
            // - an extra day is subtracted to account for the "noon minus 12h" anchor and dst shifts
            // - latest possible service day (maxServiceDate) is simply the calendar date of 'to'
            LocalDate minServiceDate = from.minusSeconds(serviceDayTime.getTotalSeconds()).toLocalDate().minusDays(1);
            LocalDate maxServiceDate = to.toLocalDate();

            for (LocalDate date = minServiceDate; !date.isAfter(maxServiceDate); date = date.plusDays(1)) {
                if (st.trip().getCalendar().isServiceAvailable(date)) {
                    Instant physicalInstant = serviceDayTime.toZonedDateTime(date, zone).toInstant();

                    // logical interval: '[from, to)'
                    if (!physicalInstant.isBefore(fromInstant) && physicalInstant.isBefore(toInstant)) {
                        events.add(new ScheduledEvent(st, physicalInstant));
                    }
                }
            }
        }

        return events.stream()
                .sorted(Comparator.comparing(ScheduledEvent::physicalInstant))
                .map(ScheduledEvent::stopTime)
                .toList();
    }

    /**
     * Retrieves a snapshot of the trips active on a specific date.
     *
     * @param date the date for which the active schedule is requested.
     * @return A list containing only the active trips.
     */
    public List<Trip> getActiveTrips(LocalDate date) {
        return calendars.values()
                .stream()
                .filter(calendar -> calendar.isServiceAvailable(date))
                .flatMap(calendar -> calendar.getTrips().stream())
                .toList();
    }

    /**
     * Retrieves all stops for a given stop, including child stops if the stop has any.
     *
     * @param stopId the identifier of the stop.
     * @return A list of stops including the stop itself and its children.
     */
    @Override
    public List<Stop> getStopWithChildren(String stopId) {
        Stop stop = getStop(stopId);

        if (stop.getChildren().isEmpty()) {
            // child stop; return itself
            return List.of(stop);
        } else {
            // parent stop; return all children and itself (departures on parent are possible)
            List<Stop> stops = new ArrayList<>();
            stops.add(stop);
            stops.addAll(stop.getChildren());

            return stops;
        }
    }

    /**
     * Retrieves the first date covered by the calendars, including their date exceptions.
     *
     * @return the start date of the schedule
     */
    @Override
    public LocalDate getStartDate() {
        return getMinOrMaxDate(Calendar::getStartDate, false);
    }

    /**
     * Retrieves the last date covered by the calendars, including their date exceptions.
     *
     * @return the end date of the schedule
     */
    @Override
    public LocalDate getEndDate() {
        return getMinOrMaxDate(Calendar::getEndDate, true);
    }

    private LocalDate getMinOrMaxDate(Function<Calendar, LocalDate> dateExtractor, boolean isMax) {
        // get the min or max date from the calendars if present
        Optional<LocalDate> calendarDate = calendars.values()
                .stream()
                .map(dateExtractor)
                .filter(Objects::nonNull)
                .reduce((date1, date2) -> isMax ? date1.isAfter(date2) ? date1 : date2 : date1.isBefore(
                        date2) ? date1 : date2);

        // get the min or max date for all date exceptions if present
        Optional<LocalDate> calendarExceptionDate = calendars.values()
                .stream()
                .flatMap(calendar -> calendar.getCalendarDates().keySet().stream())
                .reduce((date1, date2) -> isMax ? date1.isAfter(date2) ? date1 : date2 : date1.isBefore(
                        date2) ? date1 : date2);

        // get overall max or min value
        if (calendarDate.isEmpty() && calendarExceptionDate.isEmpty()) {
            // should never happen
            throw new IllegalStateException("No calendar start and end date found, if gtfs input is invalid.");
        } else if (calendarExceptionDate.isPresent() && calendarDate.isEmpty()) {
            return calendarExceptionDate.get();
        } else if (calendarExceptionDate.isEmpty()) {
            return calendarDate.get();
        } else if (isMax) { // both calendar and calendar date are present, get min or max
            return calendarDate.get()
                    .isAfter(calendarExceptionDate.get()) ? calendarDate.get() : calendarExceptionDate.get();
        } else {
            return calendarDate.get()
                    .isBefore(calendarExceptionDate.get()) ? calendarDate.get() : calendarExceptionDate.get();
        }

    }

    private Stop getStop(String stopId) {
        Stop stop = stops.get(stopId);
        if (stop == null) {
            throw new IllegalArgumentException("Stop " + stopId + " not found");
        }

        return stop;
    }

    /**
     * Bind a stop time to its physical instant to avoid redundant re-calculations during sorting
     */
    private record ScheduledEvent(StopTime stopTime, Instant physicalInstant) {
    }

}
