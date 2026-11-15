package org.naviqore.timetable.model;

import org.naviqore.timetable.type.TransferType;

import java.util.Optional;

/**
 * A transfer from one stop to another, or within a stop, from the trips arriving at the one stop to the trips departing
 * from the other.
 * <p>
 * A transfer may apply to particular routes or trips only: to the trips of a route, to a trip, or to both, on either
 * side. Of the transfers that apply to a pair of an arriving and a departing trip, the most specific one applies, as
 * GTFS ranks them: the one naming both trips, then one naming a trip and a route, one naming a trip, both routes, one
 * route, and last the one naming neither.
 */
public interface Transfer {

    Stop getFromStop();

    Stop getToStop();

    /**
     * Retrieves the route of the arriving trips the transfer applies to, if it applies to a particular route.
     *
     * @return the route the transfer begins on
     */
    Optional<? extends Route> getFromRoute();

    /**
     * Retrieves the route of the departing trips the transfer applies to, if it applies to a particular route.
     *
     * @return the route the transfer ends on
     */
    Optional<? extends Route> getToRoute();

    /**
     * Retrieves the arriving trip the transfer applies to, if it applies to a particular trip.
     *
     * @return the trip the transfer begins on
     */
    Optional<? extends Trip> getFromTrip();

    /**
     * Retrieves the departing trip the transfer applies to, if it applies to a particular trip.
     *
     * @return the trip the transfer ends on
     */
    Optional<? extends Trip> getToTrip();

    TransferType getTransferType();

    /**
     * Retrieves the minimum time in seconds needed for the transfer, if the transfer defines one.
     *
     * @return the minimum transfer time
     */
    Optional<Integer> getMinTransferTime();

}
