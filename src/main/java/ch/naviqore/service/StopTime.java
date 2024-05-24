package ch.naviqore.service;

import org.jetbrains.annotations.NotNull;

public interface StopTime {
    @NotNull Trip getTrip();

    @NotNull Stop getStop();

    @NotNull ArrivalTime getArrivalTime();

    @NotNull DepartureTime getDepartureTime();
}
