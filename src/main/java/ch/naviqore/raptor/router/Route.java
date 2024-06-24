package ch.naviqore.raptor.router;

record Route(String id, int firstRouteStopIdx, int numberOfStops, int firstStopTimeIdx, int numberOfTrips,
             String[] tripIds) {
}
