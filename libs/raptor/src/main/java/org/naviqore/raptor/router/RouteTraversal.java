package org.naviqore.raptor.router;

/**
 * Memory optimized itinerant data structure for efficient route traversal
 *
 * @param stopTimes  stop times
 * @param routes     routes
 * @param routeStops route stops
 */
record RouteTraversal(int[] stopTimes, Route[] routes, RouteStop[] routeStops) {
}
