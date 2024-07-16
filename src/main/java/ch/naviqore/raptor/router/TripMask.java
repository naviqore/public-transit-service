package ch.naviqore.raptor.router;

/**
 * Represents a trip mask for a given day for a route.
 *
 * @param tripMask the trip mask for the day for the route, where each index represents trip (sorted by departure times)
 *                 and the boolean value at that index indicates if the trip is taking place on the given day.
 */
public record TripMask(boolean[] tripMask) {
    public static final int NO_TRIP = Integer.MIN_VALUE;
}
