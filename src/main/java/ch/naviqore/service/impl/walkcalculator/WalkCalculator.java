package ch.naviqore.service.impl.walkcalculator;

import ch.naviqore.utils.spatial.GeoCoordinate;

/**
 * Calculates the walk duration between two points.
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
}
