package org.naviqore.service;

import java.time.OffsetDateTime;

/**
 * A transfer is a walk between two stops.
 * <p>
 * Typically, a transfer occurs between two public transit legs within a connection. However, a connection might consist
 * solely of a transfer if it directly links two stops via walking.
 */
public interface Transfer extends Leg {

    OffsetDateTime getArrivalTime();

    OffsetDateTime getDepartureTime();

    Stop getSourceStop();

    Stop getTargetStop();

}
