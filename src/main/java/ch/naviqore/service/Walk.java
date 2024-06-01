package ch.naviqore.service;

import ch.naviqore.utils.spatial.GeoCoordinate;

import java.util.Optional;

public interface Walk extends Leg {

    WalkType getWalkType();

    GeoCoordinate getSourceLocation();

    GeoCoordinate getTargetLocation();

    /**
     * The source or target stop of a first or last mile walk or none if it is a direct walk.
     */
    Optional<Stop> getStop();

}
