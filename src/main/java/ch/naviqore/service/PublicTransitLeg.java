package ch.naviqore.service;

import org.jetbrains.annotations.NotNull;

public interface PublicTransitLeg extends Leg {

    StopTime getArrival();

    StopTime getDeparture();

    @Override
    @NotNull
    Stop getSourceStop();

    @Override
    @NotNull
    Stop getTargetStop();

}
