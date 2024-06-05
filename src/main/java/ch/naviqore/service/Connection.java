package ch.naviqore.service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * A connection in a public transit schedule, consisting of multiple public transit legs and walks.
 */
public interface Connection {

    List<Leg> getLegs();

    LocalDateTime getDepartureTime();

    LocalDateTime getArrivalTime();

}
