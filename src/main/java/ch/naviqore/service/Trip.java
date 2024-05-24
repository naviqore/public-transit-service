package ch.naviqore.service;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface Trip {
    @NotNull String getTripId();

    @NotNull Route getRoute();

    @NotNull StopTime getStartStop();

    @NotNull StopTime getEndStop();

    @NotNull List<StopTime> getStopTimes();
}
