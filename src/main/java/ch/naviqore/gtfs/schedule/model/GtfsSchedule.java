package ch.naviqore.gtfs.schedule.model;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class GtfsSchedule {

    private final Map<String, Agency> agencies;
    private final Map<String, Calendar> calendars;
    private final Map<String, Stop> stops;
    private final Map<String, Route> routes;
    private final Map<String, Trip> trips;

    /**
     * Retrieves a snapshot of the GTFS schedule active on a specific date.
     *
     * @param date the date for which the active schedule is requested.
     * @return GtfsScheduleDay containing only the active routes, stops, and trips for the specified date.
     */
    public GtfsScheduleDay getScheduleForDay(LocalDate date) {
        Map<String, Trip> activeTrips = trips.entrySet()
                .stream()
                .filter(entry -> entry.getValue().getCalendar().isServiceAvailable(date))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        // TODO: Implement efficiently without copying.
        // return new GtfsScheduleDay(date, activeStops, activeRoutes, activeTrips);
        return null;
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
