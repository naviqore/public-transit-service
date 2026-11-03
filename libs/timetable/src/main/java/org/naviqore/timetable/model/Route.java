package org.naviqore.timetable.model;

import org.naviqore.timetable.type.TravelMode;

import java.util.List;

/**
 * A public transit route operated by an agency.
 * <p>
 * Note: The trips of a route can follow different stop sequences.
 */
public interface Route {

    String getId();

    Agency getAgency();

    String getShortName();

    String getLongName();

    TravelMode getTravelMode();

    /**
     * Retrieves the description of the type of the route, which may be more specific than its travel mode.
     *
     * @return the route type description
     */
    String getRouteTypeDescription();

    List<? extends Trip> getTrips();

}
