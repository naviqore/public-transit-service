package org.naviqore.service;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * A connection in a public transit schedule, consisting of multiple public transit legs and walks.
 */
public interface Connection {

    List<Leg> getLegs();

    /**
     * The time the connection leaves its origin: at a location, before the walk to the first stop and the access time,
     * also where the walk is too short to be a leg of its own. Its time window and maximum travel duration apply to it.
     */
    OffsetDateTime getDepartureTime();

    /**
     * The time the connection reaches its destination: at a location, after the egress time and the walk from the last
     * stop, also where the walk is too short to be a leg of its own.
     */
    OffsetDateTime getArrivalTime();

}
