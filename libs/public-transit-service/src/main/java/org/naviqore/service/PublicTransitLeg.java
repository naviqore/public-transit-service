package org.naviqore.service;

public interface PublicTransitLeg extends Leg {

    Trip getTrip();

    StopTime getArrival();

    StopTime getDeparture();

}
