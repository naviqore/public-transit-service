package ch.naviqore.service;

import org.jetbrains.annotations.Nullable;

public interface Walk extends Leg {

    Location getSourceLocation();

    Location getTargetLocation();

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
