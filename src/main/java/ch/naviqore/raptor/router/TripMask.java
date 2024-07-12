package ch.naviqore.raptor.router;

/**
 * Represents a trip mask for a given day for a route.
 * <p>
 * The trip mask is an array of booleans where each index represents a trip id and the value at that index indicates if
 * the trip is taking place on the given day.
 *
 * @param earliestTripTime the earliest trip time for the route (in seconds relative to the mask date)
 *                         (used to skip service days quickly while scanning)
 * @param latestTripTime   the latest trip time for the route (in seconds relative to the mask date)
 *                         (used to skip service days quickly while scanning)
 *
 *
 */
public record TripMask(int earliestTripTime, int latestTripTime, boolean[] tripMask) {
    public static final int NO_TRIP = Integer.MIN_VALUE;
}
