package ch.naviqore.service;

import ch.naviqore.service.config.ConnectionQueryConfig;
import ch.naviqore.service.exception.RouteNotFoundException;
import ch.naviqore.service.exception.StopNotFoundException;
import ch.naviqore.service.exception.TripNotFoundException;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Public transit service with methods to retrieve stops, trips, routes, and connections.
 */
public interface PublicTransitService {

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
     * @return the nearest stop to the specified location, or null if no stop is found
     */
    @Nullable
    Stop getNearestStop(Location location);

    /**
     * Retrieves the nearest stops to a given location within a specified radius.
     *
     * @param location the location to search around
     * @param radius   the radius to search within, in meters
     * @param limit    the maximum number of stops to retrieve
     * @return a list of the nearest stops to the specified location within the given radius
     */
    List<Stop> getNearestStops(Location location, int radius, int limit);

    /**
     * Retrieves the next departures from a specific stop within a given date range.
     *
     * @param stop  the stop for which to retrieve departures
     * @param from  the start date for the departures
     * @param until the end date for the departures (nullable)
     * @param limit the maximum number of departures to retrieve
     * @return a list of upcoming departures from the specified stop
     */
    List<StopTime> getNextDepartures(Stop stop, LocalDate from, @Nullable LocalDate until, int limit);

    /**
     * Retrieves possible connections between two locations at a specified time.
     *
     * @param source   the starting location
     * @param target   the destination location
     * @param time     the time of departure or arrival
     * @param timeType the type of time specified (departure or arrival)
     * @param config   additional configuration for the query
     * @return a list of possible connections between the source and target locations
     */
    List<Connection> getConnections(Location source, Location target, LocalDateTime time, TimeType timeType,
                                    ConnectionQueryConfig config);

    /**
     * Retrieves the shortest possible connection to each stop from a given departure location and time  within a given
     * time budget or a maximum number of transfers.
     *
     * @param source        the location to start the journey from
     * @param departureTime the time of departure
     * @param config        additional configuration for the query
     * @return a map of stops to the shortest possible connection to each stop from the departure location
     */
    Map<Stop, Connection> isoline(Location source, LocalDateTime departureTime, ConnectionQueryConfig config);

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
     * @return the trip with the specified ID
     * @throws TripNotFoundException if no trip with the specified ID is found
     */
    Trip getTripById(String tripId) throws TripNotFoundException;

    /**
     * Retrieves a route by its ID.
     *
     * @param routeId the ID of the route to retrieve
     * @return the route with the specified ID
     * @throws RouteNotFoundException if no route with the specified ID is found
     */
    Route getRouteById(String routeId) throws RouteNotFoundException;

}
