package ch.naviqore.raptor.simple;

/**
 * Memory optimized itinerant data structure for efficient route traversal
 *
 * @param stopTimes  stop times
 * @param routes     routes
 * @param routeStops route stops
 */
record RouteTraversal(StopTime[] stopTimes, Route[] routes, RouteStop[] routeStops) {
}
