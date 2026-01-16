package org.naviqore.service;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * A connection in a public transit schedule, consisting of multiple public transit legs and walks.
 */
public interface Connection {

    List<Leg> getLegs();

    OffsetDateTime getDepartureTime();

    OffsetDateTime getArrivalTime();

}
