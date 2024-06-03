package ch.naviqore.service.walk;

import ch.naviqore.utils.spatial.GeoCoordinate;

// TODO: Discuss - I moved this out of impl package since this a generic input for the service? I would vote for this.
//  - This could be injected into the service factory and then used internally by the transfer generator
//  and the service for routing footpaths if we pass coordinates. Allows later to inject even better implementations
//  as for example A* :-)

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

    /**
     * Represents a walk between two points. Is only intended to be used in the {@link WalkCalculator}.
     *
     * @param duration Duration in seconds.
     * @param distance Distance in meters.
     */
    record Walk(int duration, int distance) {
    }
}
