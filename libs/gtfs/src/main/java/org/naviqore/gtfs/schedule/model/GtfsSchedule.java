package org.naviqore.gtfs.schedule.model;

import lombok.Getter;
import lombok.experimental.Accessors;
import org.naviqore.gtfs.schedule.type.AccessibilityInformation;
import org.naviqore.gtfs.schedule.type.BikeInformation;
import org.naviqore.gtfs.schedule.type.ServiceDayTime;
import org.naviqore.gtfs.schedule.type.TimeType;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * General Transit Feed Specification (GTFS) schedule
 * <p>
 * This is an immutable class, meaning that once an instance is created, it cannot be modified. Use the
 * {@link GtfsScheduleBuilder} to construct a GTFS schedule instance.
 *
 * @author munterfi
 */
@Getter
public class GtfsSchedule {

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
    public List<StopTime> getStopTimes(String stopId, OffsetDateTime from, OffsetDateTime to, TimeType timeType) {
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("to must be after from");
        }

        Stop stop = getStop(stopId);
        List<ScheduledEvent> events = new ArrayList<>();
        final Instant fromInstant = from.toInstant();
        final Instant toInstant = to.toInstant();

        for (StopTime st : stop.getStopTimes()) {
            ServiceDayTime gtfsTime = switch (timeType) {
                case DEPARTURE -> st.departure();
                case ARRIVAL -> st.arrival();
            };
            ZoneId zone = st.trip().getRoute().getAgency().timezone();

            // calculate the range of service dates that could possibly contain this stop time:
            // - look back from the 'from' instant by the trip's internal offset (total seconds)
            // - an extra day is subtracted to account for the "noon minus 12h" anchor and dst shifts
            // - latest possible service day (maxServiceDate) is simply the calendar date of 'to'
            LocalDate minServiceDate = from.minusSeconds(gtfsTime.getTotalSeconds()).toLocalDate().minusDays(1);
            LocalDate maxServiceDate = to.toLocalDate();

            for (LocalDate date = minServiceDate; !date.isAfter(maxServiceDate); date = date.plusDays(1)) {
                if (st.trip().getCalendar().isServiceAvailable(date)) {
                    Instant physicalInstant = gtfsTime.toZonedDateTime(date, zone).toInstant();

                    // logical interval: [from, to)
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
    public List<Stop> getRelatedStops(String stopId) {
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
