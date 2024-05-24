package ch.naviqore.service;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface Connection {
    @NotNull List<Leg> getLegs();

    @NotNull Location getStartLocation();

    @NotNull Location getEndLocation();

    @Nullable Stop getStartStop();

    @Nullable Stop getEndStop();

    @NotNull ArrivalTime getArrivalTime();

    @NotNull DepartureTime getDepartureTime();

    int getDuration();

    int getBeeLineDistance();

    int getNumTransfers();

    int getWalkingDistance();
}
