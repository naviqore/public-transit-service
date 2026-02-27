package org.naviqore.service;

import org.naviqore.service.exception.RouteNotFoundException;
import org.naviqore.service.exception.StopNotFoundException;
import org.naviqore.service.exception.TripNotActiveException;
import org.naviqore.service.exception.TripNotFoundException;
import org.naviqore.utils.spatial.GeoCoordinate;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface ScheduleInformationService {

    /**
     * Retrieves the validity period of the schedule.
     *
     * @return the validity period during which the schedule is considered valid
     */
    Validity getValidity();

    /**
     * Checks if the schedule has accessibility information.
     *
     * @return true if the schedule has accessibility information, false otherwise
     */
    boolean hasAccessibilityInformation();

    /**
     * Checks if the schedule has bike information.
     *
     * @return true if the schedule has bike information, false otherwise
     */
    boolean hasBikeInformation();

    /**
     * Checks if the schedule has travel mode information.
     *
     * @return true if the schedule has travel mode information, false otherwise
     */
    boolean hasTravelModeInformation();

    /**
     * Searches for stops by name.
     *
     * @param like             the search term to match against stop names
     * @param searchType       the type of search to perform (STARTS_WITH, ENDS_WITH, CONTAINS, EXACT)
     * @param stopSortStrategy the sorting strategy for the results (RELEVANCE, ALPHABETICAL)
     * @return a list of stops matching the search criteria
     */
    List<Stop> getStops(String like, SearchType searchType, StopSortStrategy stopSortStrategy);

    /**
     * Retrieves the nearest stop to a given location.
     *
     * @param location the location to search around
     * @return the nearest stop to the specified location if any.
     */
    Optional<Stop> getNearestStop(GeoCoordinate location);

    /**
     * Retrieves the nearest stops to a given location within a specified radius.
     *
     * @param location the location to search around
     * @param radius   the radius to search within, in meters
     * @return a list of the nearest stops to the specified location within the given radius
     */
    List<Stop> getNearestStops(GeoCoordinate location, int radius);

    /**
     * Retrieves the stop times (departures or arrivals) for a specific stop within a given time window.
     *
     * @param stop      the stop for which to retrieve stop times
     * @param from      the inclusive start datetime
     * @param to        the exclusive end datetime
     * @param timeType  whether to retrieve arrivals or departures
     * @param stopScope the scope for resolving stops (STRICT, CHILDREN, RELATED, NEARBY)
     * @return a list of stop times for the specified stop
     */
    List<StopTime> getStopTimes(Stop stop, OffsetDateTime from, OffsetDateTime to, TimeType timeType,
                                StopScope stopScope);

    /**
     * Retrieves a stop by its ID.
     *
     * @param stopId the ID of the stop to retrieve
     * @return the stop with the specified ID
     * @throws StopNotFoundException if no stop with the specified ID is found
     */
    Stop getStopById(String stopId) throws StopNotFoundException;

    /**
     * Retrieves a trip by its ID.
     *
     * @param tripId the ID of the trip to retrieve
     * @param date   the date on which the trip should take place
     * @return the trip with the specified ID
     * @throws TripNotFoundException if no trip with the specified ID is found
     * @throws TripNotFoundException if the trip is found but not active on specified date
     */
    Trip getTripById(String tripId, LocalDate date) throws TripNotFoundException, TripNotActiveException;

    /**
     * Retrieves a route by its ID.
     *
     * @param routeId the ID of the route to retrieve
     * @return the route with the specified ID
     * @throws RouteNotFoundException if no route with the specified ID is found
     */
    Route getRouteById(String routeId) throws RouteNotFoundException;

}
