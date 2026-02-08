package org.naviqore.service;

import org.naviqore.utils.spatial.GeoCoordinate;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface Walk extends Leg {

    WalkType getWalkType();

    OffsetDateTime getDepartureTime();

    OffsetDateTime getArrivalTime();

    GeoCoordinate getSourceLocation();

    GeoCoordinate getTargetLocation();

    /**
     * The source or target stop of a first or last mile walk or none if it is a direct walk.
     */
    Optional<Stop> getStop();

}
