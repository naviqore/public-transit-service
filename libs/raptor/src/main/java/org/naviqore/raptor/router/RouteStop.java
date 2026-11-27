package org.naviqore.raptor.router;

/**
 * A stop of a route, and whether passengers can board and alight there, as all trips of the route can.
 *
 * @param stopIndex  the index of the stop.
 * @param routeIndex the index of the route.
 * @param boarding   whether passengers can board the trips of the route at the stop.
 * @param alighting  whether passengers can alight from the trips of the route at the stop.
 */
record RouteStop(int stopIndex, int routeIndex, boolean boarding, boolean alighting) {
}
