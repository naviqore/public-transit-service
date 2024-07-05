package ch.naviqore.raptor.router;

public record TripMask(int earliestTripTime, int latestTripTime, boolean[] tripMask) {
}
