package ch.naviqore.raptor.model;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Tests for the Raptor class.
 * <p>
 * Simple example schedule for testing:
 * <pre>
 *                      M
 *                      |
 *        I ---- J ---- K ---- L #### R
 *        |             |             |
 *        |             N ---- O ---- P ---- Q
 *        |             #             |
 * A ---- B ---- C ---- D ---- E ---- F ---- G
 *        |                           |
 *        H                           S
 * </pre>
 * <p>
 * Routes:
 * <ul>
 * <li>R1: A, B, C, D, E, F, G</li>
 * <li>R2: H, B, I, J, K, L</li>
 * <li>R3: M, K, N, O, P, Q</li>
 * <li>R4: R, P, F, S</li>
 * </ul>
 * <p>
 * Transfers:
 * <ul>
 * <li>N <--> D: 60*10</li>
 * <li>L <--> R: 60*3</li>
 * </ul>
 *
 * @author munterfi
 */
class RaptorTest {

    private static final int SECONDS_IN_HOUR = 3600;
    private static final int DAY_START_HOUR = 5;
    private static final int DAY_END_HOUR = 25;
    private static final List<Utilities.Route> ROUTES = List.of(
            new Utilities.Route("R1", List.of("A", "B", "C", "D", "E", "F", "G")),
            new Utilities.Route("R2", List.of("H", "B", "I", "J", "K", "L")),
            new Utilities.Route("R3", List.of("M", "K", "N", "O", "P", "Q")),
            new Utilities.Route("R4", List.of("R", "P", "F", "S")));
    private static final List<Utilities.Transfer> TRANSFERS = List.of(new Utilities.Transfer("N", "D", 60),
            new Utilities.Transfer("L", "R", 30));

    static class Utilities {

        public static Raptor buildRaptor() {
            return buildRaptor(ROUTES, TRANSFERS, DAY_START_HOUR, DAY_END_HOUR);
        }

        public static Raptor buildRaptor(List<Route> routes, List<Transfer> transfers, int dayStart, int dayEnd) {
            Set<String> addedStops = new HashSet<>();
            RaptorBuilder builder = Raptor.builder();
            routes.forEach(route -> {
                builder.addRoute(route.id + "-F");
                builder.addRoute(route.id + "-R");
                route.stops.forEach(stop -> {
                    if (!addedStops.contains(stop)) {
                        builder.addStop(stop);
                        addedStops.add(stop);
                    }
                });
                for (int i = 0; i < route.stops.size(); i++) {
                    builder.addRouteStop(route.stops.get(i), route.id + "-F");
                    builder.addRouteStop(route.stops.get(route.stops.size() - 1 - i), route.id + "-R");
                }
                int time = dayStart * SECONDS_IN_HOUR + route.firstDepartureOffsetInMinutes * 60;
                while (time < dayEnd * SECONDS_IN_HOUR) {
                    int departureTime = time;
                    // first stop of trip has no arrival time
                    int arrivalTime = 0;
                    for (int i = 0; i < route.stops.size(); i++) {
                        if (i + 1 == route.stops.size()) {
                            // last stop of trip has no departure time
                            departureTime = 0;
                        }
                        builder.addStopTime(route.stops.get(i), route.id + "-F", arrivalTime, departureTime);
                        builder.addStopTime(route.stops.get(route.stops.size() - 1 - i), route.id + "-R", arrivalTime,
                                departureTime);

                        arrivalTime = departureTime + route.timeBetweenStopsInMinutes * 60;
                        departureTime = arrivalTime + route.dwellTimeInMinutes * 60;
                    }
                    time += route.timeBetweenDeparturesInMinutes * 60;
                }
            });
            transfers.forEach(transfer -> {
                builder.addTransfer(transfer.sourceStop, transfer.targetStop, transfer.durationInMinutes * 60);
                builder.addTransfer(transfer.targetStop, transfer.sourceStop, transfer.durationInMinutes * 60);
            });
            return builder.build();
        }

        record Route(String id, List<String> stops, int firstDepartureOffsetInMinutes,
                     int timeBetweenDeparturesInMinutes, int timeBetweenStopsInMinutes, int dwellTimeInMinutes) {
            public Route(String id, List<String> stops) {
                this(id, stops, 0, 15, 5, 1);
            }
        }

        record Transfer(String sourceStop, String targetStop, int durationInMinutes) {
        }
    }

    @Nested
    class EarliestArrival {
        @Test
        void routingBetweenIntersectingRoutes() {
            Raptor raptor = Utilities.buildRaptor();
            List<Raptor.Connection> connections = raptor.routeEarliestArrival("A", "Q", 8 * SECONDS_IN_HOUR);
            System.out.println(connections);
            // TODO: assertThat...
        }
    }

}
