package ch.naviqore.service;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface Leg {
    @NotNull Location getDepartureLocation();

    @NotNull Location getArrivalLocation();

    @Nullable Stop getDepartureStop();

    @Nullable Stop getArrivalStop();

    @NotNull DepartureTime getDepartureTime();

    @NotNull ArrivalTime getArrivalTime();

    @Nullable Trip getTrip();

    @Nullable Walk getWalk();
}
