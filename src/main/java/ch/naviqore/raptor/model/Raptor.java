package ch.naviqore.raptor.model;

import lombok.NonNull;
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
    private final InputValidator validator;

    Raptor(Lookup lookup, StopContext stopContext, RouteTraversal routeTraversal) {
        this.stopsToIdx = lookup.stops();
        this.routesToIdx = lookup.routes();
        this.transfers = stopContext.transfers();
        this.stops = stopContext.stops();
        this.stopRoutes = stopContext.stopRoutes();
        this.stopTimes = routeTraversal.stopTimes();
        this.routes = routeTraversal.routes();
        this.routeStops = routeTraversal.routeStops();
        this.validator = new InputValidator();
    }

    public static RaptorBuilder builder() {
        return new RaptorBuilder();
    }

    public List<Connection> routeEarliestArrival(@NonNull String sourceStopId, @NonNull String targetStopId,
                                                 int departureTime) {
        InputValidator.validateDepartureTime(departureTime);
        InputValidator.validateStopIds(sourceStopId, targetStopId);
        int sourceStopIdx = validator.validateAndGetStopIdx(sourceStopId);
        int targetStopIdx = validator.validateAndGetStopIdx(targetStopId);

        log.info("Routing earliest arrival from {} to {} at {}", sourceStopId, targetStopId, departureTime);
        List<Leg[]> earliestArrivalsPerRound = spawnFromSourceStop(sourceStopIdx, targetStopIdx, departureTime);

        // get pareto-optimal solutions
        return reconstructParetoOptimalSolutions(earliestArrivalsPerRound, targetStopIdx);
    }

    // this implementation will spawn from source stop until all stops are reached with all pareto optimal connections
    private List<Leg[]> spawnFromSourceStop(int sourceStopIdx, int departureTime) {
        return spawnFromSourceStop(sourceStopIdx, NO_INDEX, departureTime);
    }

    // if targetStopIdx is set (>= 0), then the search will stop when target stop cannot be pareto optimized
    private List<Leg[]> spawnFromSourceStop(int sourceStopIdx, int targetStopIdx, int departureTime) {
        // initialization
        final int[] earliestArrivals = new int[stops.length];
        Arrays.fill(earliestArrivals, Integer.MAX_VALUE);
        // subtract same stop transfer time, as this will be added by default before scanning routes
        earliestArrivals[sourceStopIdx] = departureTime - SAME_STOP_TRANSFER_TIME;

        final List<Leg[]> earliestArrivalsPerRound = new ArrayList<>();
        earliestArrivalsPerRound.add(new Leg[stops.length]);
        earliestArrivalsPerRound.getFirst()[sourceStopIdx] = new Leg(0, departureTime, ArrivalType.INITIAL, NO_INDEX,
                sourceStopIdx, null);

        Set<Integer> markedStops = new HashSet<>();
        markedStops.add(sourceStopIdx);

        // expand footpaths for source stop
        expandFootpathsForSourceStop(earliestArrivals, earliestArrivalsPerRound, markedStops, sourceStopIdx,
                departureTime);

        // continue with further rounds as long as there are new marked stops
        int round = 1;
        while (!markedStops.isEmpty()) {
            log.debug("Scanning routes for round {}", round);
            Set<Integer> markedStopsNext = new HashSet<>();

            // initialize the earliest arrivals for current round
            Leg[] earliestArrivalsLastRound = earliestArrivalsPerRound.get(round - 1);
            earliestArrivalsPerRound.add(new Leg[stops.length]);
            Leg[] earliestArrivalsThisRound = earliestArrivalsPerRound.get(round);

            // get routes of marked stops
            Set<Integer> routesToScan = new HashSet<>();
            for (int stopIdx : markedStops) {
                Stop currentStop = stops[stopIdx];
                int stopRouteIdx = currentStop.stopRouteIdx();
                int stopRouteEndIdx = stopRouteIdx + currentStop.numberOfRoutes();
                while (stopRouteIdx < stopRouteEndIdx) {
                    routesToScan.add(stopRoutes[stopRouteIdx]);
                    stopRouteIdx++;
                }
            }
            log.debug("Routes to scan: {}", routesToScan);

            // scan routes
            for (int currentRouteIdx : routesToScan) {
                Route currentRoute = routes[currentRouteIdx];
                log.debug("Scanning route {}", currentRoute.id());
                final int firstRouteStopIdx = currentRoute.firstRouteStopIdx();
                final int firstStopTimeIdx = currentRoute.firstStopTimeIdx();
                final int numberOfStops = currentRoute.numberOfStops();
                final int numberOfTrips = currentRoute.numberOfTrips();
                int tripOffset = 0;
                boolean enteredTrip = false;
                int tripEntryTime = 0;
                Leg enteredAtArrival = null;

                // iterate over stops in route
                for (int stopOffset = 0; stopOffset < numberOfStops; stopOffset++) {
                    int stopIdx = routeStops[firstRouteStopIdx + stopOffset].stopIndex();
                    Stop stop = stops[stopIdx];
                    int earliestArrivalTime = earliestArrivals[stopIdx];

                    // find first marked stop in route
                    if (!enteredTrip) {
                        if (earliestArrivalTime == Integer.MAX_VALUE) {
                            // when current arrival is infinity (Integer.MAX_VALUE), then the stop cannot be reached
                            log.debug("Stop {} cannot be reached, continue", stop.id());
                            continue;
                        }

                        if (!markedStops.contains(stopIdx)) {
                            // this stop has already been scanned in previous round without improved arrival time
                            log.debug("Stop {} was not improved in previous round, continue", stop.id());
                            continue;
                        }

                        if (stopOffset + 1 == numberOfStops) {
                            // last stop in route, does not make sense to check for trip to enter
                            log.debug("Stop {} is last stop in route, continue", stop.id());
                            continue;
                        }

                        // got first marked stop in the route
                        log.debug("Got first entry point at stop {} at {}", stop.id(), earliestArrivalTime);
                        enteredTrip = true;
                    } else {
                        // in this case we are on a trip and need to check if arrival time has improved
                        // get time of arrival on current trip
                        StopTime stopTime = stopTimes[firstStopTimeIdx + tripOffset * numberOfStops + stopOffset];
                        if (stopTime.arrival() < earliestArrivalTime) {
                            log.debug("Stop {} was improved", stop.id());

                            // check if search should be stopped after finding the best time
                            if (targetStopIdx >= 0 && stopTime.arrival() >= earliestArrivals[targetStopIdx]) {
                                log.debug("Stop {} is not better than best time, continue", stop.id());
                                continue;
                            }

                            earliestArrivals[stopIdx] = stopTime.arrival();
                            earliestArrivalsThisRound[stopIdx] = new Leg(tripEntryTime, stopTime.arrival(),
                                    ArrivalType.ROUTE, currentRouteIdx, stopIdx, enteredAtArrival);
                            // mark stop improvement for next round
                            markedStopsNext.add(stopIdx);
                            // earlier trip is not possible
                            continue;
                        } else {
                            log.debug("Stop {} was not improved", stop.id());
                            Leg previous = earliestArrivalsLastRound[stopIdx];
                            if( previous == null || previous.arrivalTime >= stopTime.arrival() ) {
                                log.debug("Stop {} has been improved in same round, earlier trip not possible within this round", stop.id());
                                continue;
                            } else {
                                log.debug("Checking for earlier trips at stop {}", stop.id());
                            }
                        }
                    }

                    // find active trip, increase trip offset
                    tripOffset = 0;
                    enteredAtArrival = earliestArrivalsLastRound[stopIdx];
                    while (tripOffset < numberOfTrips) {
                        StopTime currentStopTime = stopTimes[firstStopTimeIdx + tripOffset * numberOfStops + stopOffset];
                        if (currentStopTime.departure() >= earliestArrivalTime + SAME_STOP_TRANSFER_TIME) {
                            log.debug("Found active trip ({}) on route {}", tripOffset, currentRoute.id());
                            tripEntryTime = currentStopTime.departure();
                            break;
                        }
                        if (tripOffset < numberOfTrips - 1) {
                            tripOffset++;
                        } else {
                            // no active trip found
                            log.debug("No active trip found on route {}", currentRoute.id());
                            break;
                        }
                    }
                }
            }

            // relax footpaths for all markedStops
            // temp variable to add any new stops to markedStopsNext
            Set<Integer> newStops = new HashSet<>();
            for (int stopIdx : markedStopsNext) {
                Stop currentStop = stops[stopIdx];
                if (currentStop.numberOfTransfers() == 0) {
                    continue;
                }
                for (int i = currentStop.transferIdx(); i < currentStop.numberOfTransfers(); i++) {
                    Transfer transfer = transfers[i];
                    // TODO: Handle variable SAME_STOP_TRANSFER_TIMEs
                    int newTargetStopArrivalTime = earliestArrivals[stopIdx] + transfer.duration() - SAME_STOP_TRANSFER_TIME;

                    // update improved arrival time
                    if (earliestArrivals[transfer.targetStopIdx()] > newTargetStopArrivalTime) {
                        log.debug("Stop {} was improved by transfer from stop {}", stops[transfer.targetStopIdx()].id(),
                                stops[stopIdx].id());
                        earliestArrivals[transfer.targetStopIdx()] = newTargetStopArrivalTime;
                        earliestArrivalsThisRound[transfer.targetStopIdx()] = new Leg(earliestArrivals[stopIdx],
                                newTargetStopArrivalTime, ArrivalType.TRANSFER, i, transfer.targetStopIdx(),
                                earliestArrivalsThisRound[stopIdx]);
                        newStops.add(transfer.targetStopIdx());
                    }
                }
            }
            markedStopsNext.addAll(newStops);

            // prepare next round
            markedStops = markedStopsNext;
            round++;
        }

        return earliestArrivalsPerRound;
    }

    private List<Connection> reconstructParetoOptimalSolutions(List<Leg[]> earliestArrivalsPerRound,
                                                               int targetStopIdx) {
        final List<Connection> connections = new ArrayList<>();

        // iterate over all rounds
        for (Leg[] legs : earliestArrivalsPerRound) {
            Leg arrival = legs[targetStopIdx];

            // target stop not reached in this round
            if (arrival == null) {
                continue;
            }

            // iterate through arrivals starting at target stop
            Connection connection = new Connection();
            while (arrival.type != ArrivalType.INITIAL) {
                String id;
                String fromStopId = stops[arrival.previous.stopIdx].id();
                String toStopId = stops[arrival.stopIdx].id();
                Connection.LegType type;
                int departureTime = arrival.departureTime;
                int arrivalTime = arrival.arrivalTime;
                if (arrival.type == ArrivalType.ROUTE) {
                    id = routes[arrival.routeOrTransferIdx].id();
                    type = Connection.LegType.ROUTE;
                } else if (arrival.type == ArrivalType.TRANSFER) {
                    id = String.format("transfer_%s_%s", fromStopId, toStopId);
                    type = Connection.LegType.FOOTPATH;
                    // include same stop transfer time (which is subtracted before scanning routes)
                    arrivalTime += SAME_STOP_TRANSFER_TIME;
                } else {
                    throw new IllegalStateException("Unknown arrival type");
                }
                connection.addLeg(new Connection.Leg(id, fromStopId, toStopId, departureTime, arrivalTime, type));
                arrival = arrival.previous;
            }

            // initialize connection: Reverse order of legs and add connection
            if (!connection.getLegs().isEmpty()) {
                connection.initialize();
                connections.add(connection);
            }

        }

        return connections;
    }

    private void expandFootpathsForSourceStop(int[] earliestArrivals, List<Leg[]> earliestArrivalsPerRound,
                                              Set<Integer> markedStops, int sourceStopIdx, int departureTime) {
        // if stop has no transfers, then no footpaths can be expanded
        if (stops[sourceStopIdx].numberOfTransfers() == 0) {
            return;
        }
        // mark all transfer stops, no checks needed for since all transfers will improve arrival time and can be
        // marked
        Stop sourceStop = stops[sourceStopIdx];
        for (int i = sourceStop.transferIdx(); i < sourceStop.transferIdx() + sourceStop.numberOfTransfers(); i++) {
            Transfer transfer = transfers[i];
            int newTargetStopArrivalTime = departureTime + transfer.duration() - SAME_STOP_TRANSFER_TIME;
            earliestArrivals[transfer.targetStopIdx()] = newTargetStopArrivalTime;
            earliestArrivalsPerRound.getFirst()[transfer.targetStopIdx()] = new Leg(departureTime,
                    newTargetStopArrivalTime, ArrivalType.TRANSFER, i, transfer.targetStopIdx(),
                    earliestArrivalsPerRound.getFirst()[sourceStopIdx]);
            markedStops.add(transfer.targetStopIdx());
        }
    }

    private enum ArrivalType {
        INITIAL,
        ROUTE,
        TRANSFER
    }

    private record Leg(int departureTime, int arrivalTime, ArrivalType type, int routeOrTransferIdx, int stopIdx,
                       Leg previous) {
    }

    /**
     * Validate inputs to raptor.
     */
    private class InputValidator {
        private static final int MIN_DEPARTURE_TIME = 0;
        private static final int MAX_DEPARTURE_TIME = 48 * 60 * 60;

        private static void validateStopIds(String sourceStopId, String targetStopId) {
            if (sourceStopId.equals(targetStopId)) {
                throw new IllegalArgumentException("Source and target stop IDs must not be the same.");
            }
        }

        private static void validateDepartureTime(int departureTime) {
            if (departureTime < MIN_DEPARTURE_TIME || departureTime > MAX_DEPARTURE_TIME) {
                throw new IllegalArgumentException(
                        "Departure time must be between " + MIN_DEPARTURE_TIME + " and " + MAX_DEPARTURE_TIME + " seconds.");
            }
        }

        private int validateAndGetStopIdx(String stopId) {
            try {
                return stopsToIdx.get(stopId);
            } catch (NullPointerException e) {
                throw new IllegalArgumentException("Stop id " + stopId + " not found.");
            }
        }
    }

}
