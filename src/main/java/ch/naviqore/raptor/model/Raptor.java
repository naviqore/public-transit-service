package ch.naviqore.raptor.model;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@Log4j2
public class Raptor {

    public final static int NO_INDEX = -1;

    private final Lookup lookup;
    private final RouteTraversal routeTraversal;
    private final StopContext stopContext;

    public static RaptorBuilder builder() {
        return new RaptorBuilder();
    }

    public void routeEarliestArrival(String sourceStop, String targetStop, int departureTime) {
        log.debug("Routing earliest arrival from {} to {} at {}", sourceStop, targetStop, departureTime);

        final int sourceIdx = lookup.stops().get(sourceStop);
        final int targetIdx = lookup.stops().get(targetStop);

        // initialization
        final int numberOfStops = stopContext.stops().length;
        int[] earliestArrival = new int[numberOfStops];
        Arrays.fill(earliestArrival, Integer.MAX_VALUE);
        earliestArrival[sourceIdx] = departureTime;

        // add first stop to marked stops
        Set<Integer> marked = new HashSet<>();
        marked.add(sourceIdx);

        // perform rounds
        int round = 0;
        while (!marked.isEmpty()) {
            log.info("Processing round {} (= transfers)", round);
            Set<Integer> nextMarked = new HashSet<>();
            for (int stopIdx : marked) {
                log.debug("Processing marked stop with index {}", stopIdx);
                Stop stop = stopContext.stops()[stopIdx];
                for (int i = stop.stopRouteIdx(); i < stop.numberOfRoutes(); i++) {
                    int routeIdx = stopContext.stopRoutes()[i];
                    log.debug("Scanning route with index {}", routeIdx);
                    Route route = routeTraversal.routes()[routeIdx];
                    // iterate until current stop index is found on route
                    int stopOffset = 0;
                    for (int j = route.firstRouteStopIdx(); j < route.numberOfStops(); j++) {
                        if (routeTraversal.routeStops()[j].stopIndex() == stopIdx) {
                            break;
                        }
                        stopOffset++;
                    }
                    log.debug("Stop offset on route is {}: {}", stopOffset,
                            stopContext.stops()[routeTraversal.routeStops()[route.firstRouteStopIdx() + stopOffset].stopIndex()]);
                    // find active trip: check if possible to hop on trip
                    int arrivalTimeAtCurrentStop = earliestArrival[stopIdx];
                    int activeTrip = 0;
                    for (int k = route.firstStopTimeIdx() + stopOffset; k < route.firstStopTimeIdx() + route.numberOfTrips() * route.numberOfStops(); k += route.numberOfStops()) {
                        // TODO: Consider dwell time
                        if (routeTraversal.stopTimes()[k].departure() >= arrivalTimeAtCurrentStop) {
                            break;
                        }
                        activeTrip++;
                    }
                    log.debug("Scanning trip active trip number {}", activeTrip);
                    int from = route.firstStopTimeIdx() + activeTrip * route.numberOfStops() + stopOffset;
                    int to = route.firstStopTimeIdx() + (activeTrip + 1) * route.numberOfStops();
                    int currentRouteStopIdx = route.firstRouteStopIdx() + stopOffset;
                    for (int k = from; k < to; k++) {
                        int currentStopIdx = routeTraversal.routeStops()[currentRouteStopIdx].stopIndex();
                        if (routeTraversal.stopTimes()[k].arrival() < earliestArrival[currentStopIdx]) {
                            earliestArrival[currentStopIdx] = routeTraversal.stopTimes()[k].arrival();
                            nextMarked.add(currentStopIdx);
                        }
                        currentRouteStopIdx++;
                    }
                }
            }

            // relax transfers (= footpaths)
            for (int stopIdx : marked) {
                Stop stop = stopContext.stops()[stopIdx];
                if (stop.transferIdx() == NO_INDEX) {
                    continue;
                }
                for (int k = stop.transferIdx(); k < stop.numberOfTransfers(); k++) {
                    Transfer transfer = stopContext.transfers()[k];
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
        for (Stop stop : stopContext.stops()) {
            System.out.println(stop.id() + ": " + earliestArrival[lookup.stops().get(stop.id())]);
        }

        log.debug("Earliest arrival at {}: {}", targetStop, earliestArrival[lookup.stops().get(targetStop)]);
    }

}
