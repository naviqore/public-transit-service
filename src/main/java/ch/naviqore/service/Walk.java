package ch.naviqore.service;

import java.time.LocalDateTime;

public interface Walk extends Leg {

    /**
     * Determines if this is first or a last mile walk.
     */
    WalkType getWalkType();

    LocalDateTime getArrivalTime();

    LocalDateTime getDepartureTime();

    Location getSourceLocation();

    Location getTargetLocation();

    /**
     * The source or target stop of this first or last mile walk.
     */
    Stop getStop();

}
