package ch.naviqore.service;

import org.jetbrains.annotations.NotNull;

public interface Stop extends Location {
    @NotNull String getStopId();
    @NotNull String getName();
}
