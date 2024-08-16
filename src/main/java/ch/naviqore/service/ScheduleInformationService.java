package ch.naviqore.service;

import ch.naviqore.service.exception.RouteNotFoundException;
import ch.naviqore.service.exception.StopNotFoundException;
import ch.naviqore.service.exception.TripNotActiveException;
import ch.naviqore.service.exception.TripNotFoundException;
import ch.naviqore.utils.spatial.GeoCoordinate;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
     * Searches for stops by name.
     *
     * @param like       the search term to match against stop names
     * @param searchType the type of search to perform (STARTS_WITH, ENDS_WITH, CONTAINS, EXACT)
     * @return a list of stops matching the search criteria
     */
    List<Stop> getStops(String like, SearchType searchType);

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
     * @param limit    the maximum number of stops to retrieve
     * @return a list of the nearest stops to the specified location within the given radius
     */
    List<Stop> getNearestStops(GeoCoordinate location, int radius, int limit);

    /**
     * Retrieves the next departures from a specific stop within a given date range.
     *
     * @param stop  the stop for which to retrieve departures
     * @param from  the start datetime for the departures
     * @param until the end datetime for the departures (nullable)
     * @param limit the maximum number of departures to retrieve
     * @return a list of upcoming departures from the specified stop
     */
    List<StopTime> getNextDepartures(Stop stop, LocalDateTime from, @Nullable LocalDateTime until, int limit);

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
