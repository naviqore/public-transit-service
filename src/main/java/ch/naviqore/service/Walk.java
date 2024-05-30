package ch.naviqore.service;

import java.time.LocalDateTime;
import java.util.Optional;

public interface Walk extends Leg {

    LocalDateTime getArrivalTime();

    LocalDateTime getDepartureTime();

    Location getSourceLocation();

    Location getTargetLocation();

    /**
     * The target public transit stop, if walk starts at a stop.
     */
    Optional<Stop> getSourceStop();

    /**
     * The target public transit stop, if walk ends at a stop.
     */
    Optional<Stop> getTargetStop();

}
