package ch.naviqore.raptor.model;

/**
 * Memory optimized itinerant data structure for efficient route traversal
 *
 * @param stopTimes  stop times
 * @param routes     routes
 * @param routeStops route stops
 */
public record RouteTraversal(StopTime[] stopTimes, Route[] routes, RouteStop[] routeStops) {
}
