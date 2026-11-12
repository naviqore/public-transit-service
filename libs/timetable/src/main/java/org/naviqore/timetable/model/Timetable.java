package org.naviqore.timetable.model;

import org.naviqore.timetable.type.TimeType;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Public transit timetable: the stops, routes, trips, calendars and transfers of a schedule.
 * <p>
 * A schedule format provides its data through these interfaces, implemented by its own model, so the timetable is never
 * copied: the returned collections may be views whose elements are created when they are accessed. Implementations are
 * immutable and safe for concurrent reads.
 * <p>
 * Stops, routes, trips and calendars are identified by their IDs, which are unique within their kind; two instances
 * with the same ID are equal.
 */
public interface Timetable {

    Map<String, ? extends Calendar> getCalendars();

    Map<String, ? extends Stop> getStops();

    Map<String, ? extends Route> getRoutes();

    Map<String, ? extends Trip> getTrips();

    /**
     * Retrieves the first date covered by the calendars of the timetable.
     *
     * @return the start date of the timetable
     */
    LocalDate getStartDate();

    /**
     * Retrieves the last date covered by the calendars of the timetable.
     *
     * @return the end date of the timetable
     */
    LocalDate getEndDate();

    /**
     * Checks if the trips of the timetable carry wheelchair accessibility information.
     *
     * @return true if at least one trip has known wheelchair accessibility, false otherwise
     */
    boolean hasTripAccessibilityInformation();

    /**
     * Checks if the trips of the timetable carry bike information.
     *
     * @return true if at least one trip has known bike information, false otherwise
     */
    boolean hasTripBikeInformation();

    /**
     * Retrieves all stop times for a specific stop within a physical time window [from, to).
     * <p>
     * A stop time is included if its departure or arrival, depending on the time type, falls into the window on any
     * service day its trip runs, for trips of arbitrary durations (>24h) and in the time zone of their agency.
     *
     * @param stopId   unique identifier of the stop
     * @param from     inclusive start of the physical window
     * @param to       exclusive end of the physical window
     * @param timeType whether to evaluate departure or arrival times
     * @return chronologically sorted list of matching stop times
     */
    List<? extends StopTime> getStopTimes(String stopId, OffsetDateTime from, OffsetDateTime to, TimeType timeType);

    /**
     * Retrieves all stops for a given stop, including child stops if the stop has any.
     *
     * @param stopId the identifier of the stop
     * @return a list of stops including the stop itself and its children
     */
    List<? extends Stop> getStopWithChildren(String stopId);

}
