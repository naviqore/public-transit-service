package ch.naviqore.raptor.model;

public record Route(String id, int firstRouteStopIdx, int numberOfStops, int firstStopTimeIdx, int numberOfTrips) {
}
