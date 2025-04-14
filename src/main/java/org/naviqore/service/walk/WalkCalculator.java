package org.naviqore.service.walk;

import org.naviqore.utils.spatial.GeoCoordinate;

/**
 * Calculates a walk between two points.
 */
public interface WalkCalculator {

    /**
     * Calculates the walk duration between two points.
     *
     * @param from Starting point.
     * @param to   Target point.
     * @return Walk object with the duration in seconds and distance in meters.
     */
    Walk calculateWalk(GeoCoordinate from, GeoCoordinate to);

    /**
     * Represents a walk between two points. Is only intended to be used in the {@link WalkCalculator}.
     *
     * @param duration Duration in seconds.
     * @param distance Distance in meters.
     */
    record Walk(int duration, int distance) {
    }
}
