package org.naviqore.service.spi;

import org.jspecify.annotations.Nullable;
import org.naviqore.service.Route;
import org.naviqore.service.Stop;
import org.naviqore.service.TimeType;
import org.naviqore.service.Validity;
import org.naviqore.utils.spatial.GeoCoordinate;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Read access to a public transit schedule, as required by the public transit service to answer schedule information
 * queries and to compose routing results.
 * <p>
 * This is the extension point for schedule sources other than GTFS: an implementation adapts its source to this
 * interface, and the service builds on it without knowing the source. The GTFS implementation is provided by the
 * library.
 * <p>
 * All stop times are seconds relative to the service day, where the service day starts at noon minus 12 hours (the GTFS
 * convention), and may exceed 24 hours for trips running past midnight.
 */
public interface TransitSchedule {

    /**
     * Retrieves the validity period of the schedule.
     *
     * @return the validity period during which the schedule is considered valid
     */
    Validity getValidity();

    /**
     * Checks if the trips of the schedule carry accessibility information.
     *
     * @return true if the schedule has accessibility information, false otherwise
     */
    boolean hasAccessibilityInformation();

    /**
     * Checks if the trips of the schedule carry bike information.
     *
     * @return true if the schedule has bike information, false otherwise
     */
    boolean hasBikeInformation();

    /**
     * Retrieves all stops of the schedule.
     *
     * @return all stops, in a stable order
     */
    List<Stop> getStops();

    /**
     * Retrieves a stop by its ID.
     *
     * @param stopId the ID of the stop
     * @return the stop, or null if no stop with the specified ID exists
     */
    @Nullable Stop getStop(String stopId);

    /**
     * Retrieves the parent of a stop in the stop hierarchy, e.g. the station of a platform.
     *
     * @param stopId the ID of the stop
     * @return the parent stop, if the stop has one
     */
    Optional<Stop> getParent(String stopId);

    /**
     * Retrieves the children of a stop in the stop hierarchy, e.g. the platforms of a station.
     *
     * @param stopId the ID of the stop
     * @return the child stops, empty if the stop has none
     */
    List<Stop> getChildren(String stopId);

    /**
     * Retrieves the stops within a radius around a location.
     *
     * @param location the location to search around
     * @param radius   the radius to search within, in meters
     * @return the stops within the radius
     */
    List<Stop> getStopsWithinRadius(GeoCoordinate location, int radius);

    /**
     * Retrieves the stop nearest to a location.
     *
     * @param location the location to search around
     * @return the nearest stop, or null if the schedule has no stops
     */
    @Nullable Stop getNearestStop(GeoCoordinate location);

    /**
     * Retrieves a route by its ID.
     *
     * @param routeId the ID of the route
     * @return the route, or null if no route with the specified ID exists
     */
    @Nullable Route getRoute(String routeId);

    /**
     * Retrieves a trip by its ID.
     *
     * @param tripId the ID of the trip
     * @return the trip, or null if no trip with the specified ID exists
     */
    @Nullable ScheduledTrip getTrip(String tripId);

    /**
     * Checks if a trip runs on a service day.
     *
     * @param tripId     the ID of the trip
     * @param serviceDay the service day
     * @return true if the trip runs on the service day, false otherwise
     */
    boolean isTripActive(String tripId, LocalDate serviceDay);

    /**
     * Retrieves the stop times (departures or arrivals) of the trips at a stop within a time window.
     * <p>
     * A stop time is included if its departure or arrival, depending on the time type, falls into the interval
     * {@code [from, to)} on any service day its trip is active. The stop times are sorted by that instant.
     *
     * @param stopId   the ID of the stop
     * @param from     the inclusive start datetime
     * @param to       the exclusive end datetime
     * @param timeType whether to retrieve arrivals or departures
     * @return the stop times at the stop, sorted by time
     */
    List<ScheduledStopTime> getStopTimes(String stopId, OffsetDateTime from, OffsetDateTime to, TimeType timeType);

}
