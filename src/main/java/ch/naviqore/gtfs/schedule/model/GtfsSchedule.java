package ch.naviqore.gtfs.schedule.model;

import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    private final boolean stopAccessibilityInformationProvider;
    private final boolean tripAccessibilityInformationProvider;
    private final boolean tripBikeInformationProvider;

    /**
     * Constructs an immutable GTFS schedule.
     * <p>
     * Each map passed to this constructor is copied into an immutable map to prevent further modification and to
     * enhance memory efficiency and thread-safety in a concurrent environment.
     */
    GtfsSchedule(Map<String, Agency> agencies, Map<String, Calendar> calendars, Map<String, Stop> stops,
                 Map<String, Route> routes, Map<String, Trip> trips, boolean stopAccessibilityInformationProvider,
                 boolean tripAccessibilityInformationProvider, boolean tripBikeInformationProvider) {
        this.agencies = Map.copyOf(agencies);
        this.calendars = Map.copyOf(calendars);
        this.stops = Map.copyOf(stops);
        this.routes = Map.copyOf(routes);
        this.trips = Map.copyOf(trips);
        this.stopAccessibilityInformationProvider = stopAccessibilityInformationProvider;
        this.tripAccessibilityInformationProvider = tripAccessibilityInformationProvider;
        this.tripBikeInformationProvider = tripBikeInformationProvider;
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
     * Retrieves a list of the next departures from a specific stop.
     *
     * @param stopId   the identifier of the stop.
     * @param dateTime the date and time for which the next departures are requested.
     * @param limit    the maximum number of departures to return.
     * @return A list of the next departures from the specified stop.
     */
    public List<StopTime> getNextDepartures(String stopId, LocalDateTime dateTime, int limit) {
        Stop stop = stops.get(stopId);
        if (stop == null) {
            throw new IllegalArgumentException("Stop " + stopId + " not found");
        }
        return stop.getStopTimes()
                .stream()
                .filter(stopTime -> stopTime.departure().getTotalSeconds() >= dateTime.toLocalTime().toSecondOfDay())
                .filter(stopTime -> stopTime.trip().getCalendar().isServiceAvailable(dateTime.toLocalDate()))
                .limit(limit)
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
                .collect(Collectors.toList());
    }

}
