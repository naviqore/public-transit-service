package ch.naviqore.raptor.impl;

record Route(String id, int firstRouteStopIdx, int numberOfStops, int firstStopTimeIdx, int numberOfTrips,
             String[] tripIds) {
}
