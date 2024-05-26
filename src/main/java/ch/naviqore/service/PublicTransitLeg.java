package ch.naviqore.service;

public interface PublicTransitLeg extends Leg {

    StopTime getArrival();

    StopTime getDeparture();

}
