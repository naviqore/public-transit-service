package ch.naviqore.raptor.model;

import lombok.extern.log4j.Log4j2;

import java.util.*;

/**
 * Raptor algorithm implementation
 *
 * @author munterfi
 */
@Log4j2
public class Raptor {

    public final static int NO_INDEX = -1;
    public final static int SAME_STOP_TRANSFER_TIME = 120;
    // lookup
    private final Map<String, Integer> stopsToIdx;
    private final Map<String, Integer> routesToIdx;
    // stop context
    private final Transfer[] transfers;
    private final Stop[] stops;
    private final int[] stopRoutes;
    // route traversal
    private final StopTime[] stopTimes;
    private final Route[] routes;
    private final RouteStop[] routeStops;

    Raptor(Lookup lookup, StopContext stopContext, RouteTraversal routeTraversal) {
        this.stopsToIdx = lookup.stops();
        this.routesToIdx = lookup.routes();
        this.transfers = stopContext.transfers();
        this.stops = stopContext.stops();
        this.stopRoutes = stopContext.stopRoutes();
        this.stopTimes = routeTraversal.stopTimes();
        this.routes = routeTraversal.routes();
        this.routeStops = routeTraversal.routeStops();
    }

    public static RaptorBuilder builder() {
        return new RaptorBuilder();
    }

    public void routeEarliestArrival(String sourceStop, String targetStop, int departureTime) {
        log.debug("Routing earliest arrival from {} to {} at {}", sourceStop, targetStop, departureTime);

        // TODO: Input validation, same stop, nulls, not exising stops.

        final int sourceIdx = stopsToIdx.get(sourceStop);
        final int targetIdx = stopsToIdx.get(targetStop);

        // initialization
        final List<Arrival[]> earliestArrivalsPerRound = new ArrayList<>();
        earliestArrivalsPerRound.add(new Arrival[stops.length]);
        earliestArrivalsPerRound.getFirst()[sourceIdx] = new Arrival(departureTime, ArrivalType.INITIAL, NO_INDEX,
                NO_INDEX);

        Set<Integer> markedStops = new HashSet<>();
        markedStops.add(sourceIdx);

        int round = 1;
        while (!markedStops.isEmpty()) {
            log.debug("Scanning routes for round {} (=trips)", round);
            log.debug("Marked stops: {}", markedStops);
            Set<Integer> markedStopsNext = new HashSet<>();

            // initialize the earliest arrivals for current round
            Arrival[] earliestArrivalsLastRound = earliestArrivalsPerRound.get(round - 1);
            earliestArrivalsPerRound.add(earliestArrivalsLastRound.clone());
            Arrival[] earliestArrivalsThisRound = earliestArrivalsPerRound.get(round);

            // get routes of marked stops
            Set<Route> routesToScan = new HashSet<>();
            for (int stopIdx : markedStops) {
                Stop currentStop = stops[stopIdx];
                int stopRouteIdx = currentStop.stopRouteIdx();
                int stopRouteEndIdx = stopRouteIdx + currentStop.numberOfRoutes();
                while (stopRouteIdx < stopRouteEndIdx) {
                    routesToScan.add(routes[stopRoutes[stopRouteIdx]]);
                    stopRouteIdx++;
                }
            }
            log.debug("Routes to scan: {}", routesToScan);

            // scan routes
            for (Route currentRoute : routesToScan) {
                log.debug("Scanning route {}", currentRoute.id());
                final int firstRouteStopIdx = currentRoute.firstRouteStopIdx();
                final int firstStopTimeIdx = currentRoute.firstStopTimeIdx();
                final int numberOfStops = currentRoute.numberOfStops();
                final int numberOfTrips = currentRoute.numberOfTrips();
                int tripOffset = 0;
                boolean enteredTrip = false;

                // iterate over stops in route
                for (int stopOffset = 0; stopOffset < numberOfStops; stopOffset++) {
                    int earliestDepartureTime;
                    int stopIdx = routeStops[firstRouteStopIdx + stopOffset].stopIndex();
                    Stop stop = stops[stopIdx];

                    Arrival currentArrivalLastRound = earliestArrivalsLastRound[routeStops[firstRouteStopIdx + stopOffset].stopIndex()];

                    // find first marked stop in route
                    if (!enteredTrip) {
                        if (currentArrivalLastRound == null) {
                            // when current arrival is null, then the stop cannot be reached
                            log.debug("Stop {} cannot be reached, continue", stop.id());
                            continue;
                        }

                        if (!markedStops.contains(stopIdx)) {
                            log.debug("marked stops: {}, stopidx: {}", markedStops, currentArrivalLastRound);
                            // this stop has already been scanned in previous round without improved arrival time
                            log.debug("Stop {} was not improved in previous round, continue", stop.id());
                            continue;
                        }

                        // got first marked stop in the route
                        log.debug("Got first entry point at stop {} at {} (type: {})", stop.id(),
                                currentArrivalLastRound.time, currentArrivalLastRound.type());
                        enteredTrip = true;
                        earliestDepartureTime = currentArrivalLastRound.time();
                    } else {
                        // in this case we are on a trip and need to check if arrival time has improved
                        // get time of arrival on current trip
                        StopTime stopTime = stopTimes[firstStopTimeIdx + tripOffset * numberOfStops + stopOffset];
                        if (currentArrivalLastRound == null || stopTime.arrival() < currentArrivalLastRound.time) {
                            log.debug("Stop {} was improved", stop.id());
                            // TODO: Get correct route idx
                            earliestArrivalsThisRound[stopIdx] = new Arrival(stopTime.arrival(), ArrivalType.ROUTE, -1,
                                    stopIdx);
                            // mark stop improvement for next round
                            markedStopsNext.add(stopIdx);
                            // Because earlier trip is not possible
                            continue;
                        }
                        earliestDepartureTime = currentArrivalLastRound.time;
                    }

                    // find active trip, increase trip offset
                    tripOffset = 0;
                    while (tripOffset < numberOfTrips) {
                        StopTime currentStopTime = stopTimes[firstStopTimeIdx + tripOffset * numberOfStops + stopOffset];
                        if (currentStopTime.departure() >= earliestDepartureTime + SAME_STOP_TRANSFER_TIME) {
                            // active trip: possible to enter this trip
                            log.debug("Found active trip ({}) on route {}", tripOffset, currentRoute.id());
                            break;
                        }
                        tripOffset++;
                    }
                }
            }

            // TODO: Relax footpath transfers

            // prepare next round
            markedStops = markedStopsNext;
            round++;
        }

        // get pareto-optimal solutions
        int legs = -1;
        for (Arrival[] earliestArrival : earliestArrivalsPerRound) {
            Arrival targetArrival = earliestArrival[targetIdx];
            if (targetArrival != null) {
                log.info("Found connection with {} legs: {}", legs, targetArrival);
            } else {
                log.info("Found no connection with {} legs", legs);
            }
            legs++;
        }

    }

    enum ArrivalType {
        INITIAL,
        ROUTE,
        TRANSFER
    }

    record Arrival(int time, ArrivalType type, int enteredArrivalIdx, int enteredAtStopIdx) {
    }

}
