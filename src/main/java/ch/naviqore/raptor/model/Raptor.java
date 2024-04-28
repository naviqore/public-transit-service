package ch.naviqore.raptor.model;

import lombok.extern.log4j.Log4j2;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Raptor algorithm implementation
 *
 * @author munterfi
 */
@Log4j2
public class Raptor {

    public final static int NO_INDEX = -1;
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

        final int sourceIdx = stopsToIdx.get(sourceStop);
        final int targetIdx = stopsToIdx.get(targetStop);

        // initialization
        int[] earliestArrival = new int[stops.length];
        Arrays.fill(earliestArrival, Integer.MAX_VALUE);
        earliestArrival[sourceIdx] = departureTime;

        // add first stop to marked stops
        Set<Integer> marked = new HashSet<>();
        marked.add(sourceIdx);

        // perform rounds
        int round = 0;
        while (!marked.isEmpty()) {
            log.info("Processing round {} (= transfers), marked: {}", round,
                    marked.stream().map(stopIdx -> stops[stopIdx].id()).toList());
            Set<Integer> nextMarked = new HashSet<>();
            for (int stopIdx : marked) {
                log.debug("Processing marked stop {} - {}", stopIdx, stops[stopIdx].id());
                Stop stop = stops[stopIdx];
                for (int i = stop.stopRouteIdx(); i < stop.numberOfRoutes(); i++) {
                    int routeIdx = stopRoutes[i];
                    Route route = routes[routeIdx];
                    log.debug("Scanning route {} - {}", routeIdx, route.id());
                    // iterate until current stop index is found on route
                    int stopOffset = 0;
                    for (int j = route.firstRouteStopIdx(); j < route.firstRouteStopIdx() + route.numberOfStops(); j++) {
                        if (routeStops[j].stopIndex() == stopIdx) {
                            break;
                        }
                        stopOffset++;
                    }
                    log.debug("Stop offset on route {} is {} - {}", route.id(), stopOffset,
                            stops[routeStops[route.firstRouteStopIdx() + stopOffset].stopIndex()].id());
                    // find active trip: check if possible to hop on trip
                    int arrivalTimeAtCurrentStop = earliestArrival[stopIdx];
                    int activeTrip = 0;
                    for (int k = route.firstStopTimeIdx() + stopOffset; k < route.firstStopTimeIdx() + route.numberOfTrips() * route.numberOfStops(); k += route.numberOfStops()) {
                        // TODO: Consider dwell time
                        if (stopTimes[k].departure() >= arrivalTimeAtCurrentStop) {
                            break;
                        }
                        activeTrip++;
                    }
                    log.debug("Scanning active trip number {} on route {} - {}", activeTrip, routeIdx, route.id());
                    int from = route.firstStopTimeIdx() + activeTrip * route.numberOfStops() + stopOffset;
                    int to = route.firstStopTimeIdx() + (activeTrip + 1) * route.numberOfStops();
                    int currentRouteStopIdx = route.firstRouteStopIdx() + stopOffset;
                    for (int k = from; k < to; k++) {
                        int currentStopIdx = routeStops[currentRouteStopIdx].stopIndex();
                        if (stopTimes[k].arrival() < earliestArrival[currentStopIdx]) {
                            earliestArrival[currentStopIdx] = stopTimes[k].arrival();
                            nextMarked.add(currentStopIdx);
                        }
                        currentRouteStopIdx++;
                    }
                }
            }

            // relax transfers (= footpaths)
            for (int stopIdx : marked) {
                Stop stop = stops[stopIdx];
                if (stop.transferIdx() == NO_INDEX) {
                    continue;
                }
                for (int k = stop.transferIdx(); k < stop.numberOfTransfers(); k++) {
                    Transfer transfer = transfers[k];
                    int targetStopIdx = transfer.targetStopIdx();
                    int arrivalTimeAfterTransfer = earliestArrival[stopIdx] + transfer.duration();
                    if (arrivalTimeAfterTransfer < earliestArrival[targetStopIdx]) {
                        earliestArrival[targetStopIdx] = arrivalTimeAfterTransfer;
                        nextMarked.add(targetStopIdx);
                    }
                }
            }

            // prepare for next round
            marked = nextMarked;
            round++;
        }

        // print results for debugging
        for (int i = 0; i < earliestArrival.length; i++) {
            if (earliestArrival[i] == Integer.MAX_VALUE) {
                earliestArrival[i] = -1;
            }
        }
        for (Stop stop : stops) {
            System.out.println(stop.id() + ": " + earliestArrival[stopsToIdx.get(stop.id())]);
        }

        log.debug("Earliest arrival at {}: {}", targetStop, earliestArrival[stopsToIdx.get(targetStop)]);
    }

}
