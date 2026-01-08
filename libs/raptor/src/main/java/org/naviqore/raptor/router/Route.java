package org.naviqore.raptor.router;

import java.time.ZoneId;

record Route(String id, ZoneId zoneId, int firstRouteStopIdx, int numberOfStops, int firstStopTimeIdx,
             int numberOfTrips, String[] tripIds) {
}
