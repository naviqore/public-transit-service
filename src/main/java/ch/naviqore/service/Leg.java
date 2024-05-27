package ch.naviqore.service;

import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;

/**
 * Represents a leg of a connection, including its order, distance, and duration.
 */
public interface Leg {

    LegType getLegType();

    Location getSourceLocation();

    Location getTargetLocation();

    LocalDateTime getArrivalTime();

    LocalDateTime getDepartureTime();

    int getDistance();

    int getDuration();

    /**
     * The target public transit stop, if walk starts at a stop.
     */
    @Nullable
    Stop getSourceStop();

    /**
     * The target public transit stop, if walk ends at a stop.
     */
    @Nullable
    Stop getTargetStop();

}
