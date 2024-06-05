package ch.naviqore.raptor;

record Route(String id, int firstRouteStopIdx, int numberOfStops, int firstStopTimeIdx, int numberOfTrips,
             String[] tripIds) {
}
