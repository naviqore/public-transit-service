package org.naviqore.raptor;

import java.time.LocalDateTime;

/**
 * A leg is a part of a connection that is traveled on the same route and transport mode, without a transfer.
 */
public interface Leg extends Comparable<Leg> {

    String getRouteId();

    String getTripId();

    String getFromStopId();

    String getToStopId();

    LocalDateTime getDepartureTime();

    LocalDateTime getArrivalTime();

    Type getType();

    /**
     * Types of legs in a connection.
     */
    enum Type {
        WALK_TRANSFER,
        ROUTE
    }
}
