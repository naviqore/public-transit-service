package org.naviqore.raptor.router;

import java.util.Map;

/**
 * Looks up the indices of stops and routes by their ids.
 *
 * @param stops        the index of each stop.
 * @param routes       the indices of the RAPTOR routes that hold the trips of each route: one, or several if trips of
 *                     the route overtake one another.
 * @param routeTripIds the trip ids of each route: those of its RAPTOR routes, one RAPTOR route after the other, in the
 *                     order of {@code routes}.
 */
record Lookup(Map<String, Integer> stops, Map<String, int[]> routes, Map<String, String[]> routeTripIds) {
}
