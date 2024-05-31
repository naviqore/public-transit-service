package ch.naviqore.service;

import java.time.LocalDateTime;
import java.util.Optional;

public interface Walk extends Leg {

    WalkType getWalkType();

    LocalDateTime getArrivalTime();

    LocalDateTime getDepartureTime();

    Location getSourceLocation();

    Location getTargetLocation();

    /**
     * The source or target stop of a first or last mile walk or none if it is a direct walk.
     */
    Optional<Stop> getStop();

}
