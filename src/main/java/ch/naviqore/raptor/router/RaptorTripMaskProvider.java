package ch.naviqore.raptor.router;

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
     * Get the trip mask for a given date.
     * <p>
     * This method should return a map of route ids to trip masks for the given date.
     *
     * @param date the date for which the trip mask should be returned.
     * @return the raptor day mask of the day.
     */
    RaptorDayMask getTripMask(LocalDate date);
}
