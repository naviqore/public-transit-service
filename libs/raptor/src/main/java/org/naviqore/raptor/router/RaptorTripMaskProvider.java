package org.naviqore.raptor.router;

import org.naviqore.raptor.QueryConfig;

import java.time.LocalDate;
import java.util.Map;

/**
 * Interface to provide trip masks for the raptor routing.
 * <p>
 * The trip mask provider should be able to provide information if a route trip is taking place on a given date.
 * Internally this will then be used to create a stop time array for the route scanner.
 */
public interface RaptorTripMaskProvider {
    /**
     * Set the trip ids for each route.
     * <p>
     * This method is called when the raptor data is loaded by the raptor instance. And passes the reference to the trip
     * mask provider containing a map of route ids to an array of trip ids.
     *
     * @param routeTripIds a map of route ids to an array of trip ids.
     */
    void setTripIds(Map<String, String[]> routeTripIds);

    /**
     * Get the service id for a date.
     * <p>
     * Each date has a service id associated with it. This service id is used to cache the trip mask for the given date.
     * And allow using the same trip mask for multiple dates if the service id is the same.
     *
     * @param date the date for which the service id should be returned.
     * @return the service id for the given date.
     */
    String getServiceIdForDate(LocalDate date);

    /**
     * Get the trip mask for a given date and query config.
     * <p>
     * This method should return a map of route ids to trip masks for the given date and query config.
     *
     * @param date        the date for which the trip mask should be returned.
     * @param queryConfig the query config for which the trip mask should be returned.
     * @return the raptor day mask of the day.
     */
    DayTripMask getDayTripMask(LocalDate date, QueryConfig queryConfig);

    /**
     * This represents a service day trip mask for a given day.
     * <p>
     * The service day mask holds the date it's valid for, a serviceId which can be identical for multiple days if the
     * service is the same. And a map of route ids to {@link RouteTripMask}.
     *
     * @param serviceId the service id for the day
     * @param date      the date of the day
     * @param tripMask  a map of route ids to route trip masks for the day
     */
    record DayTripMask(String serviceId, LocalDate date, Map<String, RouteTripMask> tripMask) {
    }

    /**
     * Represents a route trip mask for a given day and route.
     *
     * @param routeTripMask the route trip mask for the day, where each index represents trip (sorted by departure
     *                      times) and the boolean value at that index indicates if the trip is taking place on the
     *                      given day.
     */
    record RouteTripMask(boolean[] routeTripMask) {
        public static final int NO_TRIP = Integer.MIN_VALUE;
    }

}
