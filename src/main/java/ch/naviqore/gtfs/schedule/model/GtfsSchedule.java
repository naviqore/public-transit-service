package ch.naviqore.gtfs.schedule.model;

import ch.naviqore.gtfs.schedule.spatial.Coordinate;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * General Transit Feed Specification (GTFS) schedule
 * <p>
 * Use the {@link GtfsScheduleBuilder} to construct a GTFS schedule instance.
 *
 * @author munterfi
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class GtfsSchedule {

    private final Map<String, Agency> agencies;
    private final Map<String, Calendar> calendars;
    private final Map<String, Stop> stops;
    private final Map<String, Route> routes;
    private final Map<String, Trip> trips;

    /**
     * Creates a new GTFS schedule builder.
     *
     * @return A new GTFS schedule builder.
     */
    public static GtfsScheduleBuilder builder() {
        return new GtfsScheduleBuilder();
    }

    /**
     * Retrieves a list of stops within a specified distance from a given location.
     *
     * @param latitude    the latitude of the origin location.
     * @param longitude   the longitude of the origin location.
     * @param maxDistance the maximum distance from the origin location.
     * @return A list of stops within the specified distance.
     */
    public List<Stop> getNearestStops(double latitude, double longitude, int maxDistance) {
        // TODO: Use a spatial index for efficient nearest neighbor search, e.g. KD-tree or R-tree
        // TODO: We need a range search in the KD-tree https://naviqore.atlassian.net/browse/NAV-32
        Coordinate origin = new Coordinate(latitude, longitude);
        return stops.values()
                .stream()
                .filter(stop -> stop.getCoordinate().distanceTo(origin) <= maxDistance)
                .collect(Collectors.toList());
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

    public Map<String, Agency> getAgencies() {
        return Collections.unmodifiableMap(agencies);
    }

    public Map<String, Calendar> getCalendars() {
        return Collections.unmodifiableMap(calendars);
    }

    public Map<String, Stop> getStops() {
        return Collections.unmodifiableMap(stops);
    }

    public Map<String, Route> getRoutes() {
        return Collections.unmodifiableMap(routes);
    }

    public Map<String, Trip> getTrips() {
        return Collections.unmodifiableMap(trips);
    }
}
