package ch.naviqore.service;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface Walk {
    @NotNull Location getStart();

    @NotNull Location getEnd();

    @Nullable Stop getStartStop();

    @Nullable Stop getEndStop();

    int getDistance();

    int getDuration();
}
