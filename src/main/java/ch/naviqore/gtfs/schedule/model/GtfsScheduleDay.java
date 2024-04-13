package ch.naviqore.gtfs.schedule.model;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Map;

/**
 * GTFS Schedule Service Day
 * <p>
 * Represents a daily snapshot of the GTFS schedule, containing only the active services on a specific date.
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class GtfsScheduleDay {

    @Getter
    private final LocalDate date;
    private final Map<String, Stop> stops;
    private final Map<String, Route> routes;
    private final Map<String, Trip> trips;

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
