package ch.naviqore.raptor.model;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    private static final List<Utils.Route> ROUTES = List.of(
            new Utils.Route("R1", List.of("A", "B", "C", "D", "E", "F", "G")),
            new Utils.Route("R2", List.of("H", "B", "I", "J", "K", "L")),
            new Utils.Route("R3", List.of("M", "K", "N", "O", "P", "Q")),
            new Utils.Route("R4", List.of("R", "P", "F", "S")));
    private static final List<Utils.Transfer> TRANSFERS = List.of(new Utils.Transfer("N", "D", 60),
            new Utils.Transfer("L", "R", 30));

    static class Utils {

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
        void shouldFindConnectionsBetweenIntersectingRoutes() {
            // Should return two pareto optimal connections:
            // 1. Connection (with two route legs and one transfer (including footpath) --> slower but fewer transfers)
            //  - Route R1-F from A to D
            //  - Foot Transfer from D to N
            //  - Route R3-F from N to Q

            // 2. Connection (with three route legs and two transfers (same station) --> faster but more transfers)
            //  - Route R1-F from A to F
            //  - Route R4-R from F to P
            //  - Route R3-F from P to Q
            Raptor raptor = Utils.buildRaptor();
            String sourceStop = "A";
            String targetStop = "Q";
            int departureTime = 8 * SECONDS_IN_HOUR;
            List<Connection> connections = raptor.routeEarliestArrival(sourceStop, targetStop, departureTime);

            // check if 2 connections were found
            assertEquals(2, connections.size());

            // check if the first connection is correct
            Connection connection1 = connections.getFirst();
            assertEquals(sourceStop, connection1.getFromStopId());
            assertEquals(targetStop, connection1.getToStopId());
            assertTrue(connection1.getDepartureTime() >= departureTime,
                    "Departure time should be greater equal than searched for departure time");
            // check that transfers make sense
            assertEquals(1, connection1.getNumFootPathTransfers());
            assertEquals(1, connection1.getNumTransfers());
            assertEquals(0, connection1.getNumSameStationTransfers());

            // check second connection
            Connection connection2 = connections.get(1);
            assertEquals(sourceStop, connection2.getFromStopId());
            assertEquals(targetStop, connection2.getToStopId());
            assertTrue(connection2.getDepartureTime() >= departureTime,
                    "Departure time should be greater equal than searched for departure time");
            // check that transfers make sense
            assertEquals(0, connection2.getNumFootPathTransfers());
            assertEquals(2, connection2.getNumTransfers());
            assertEquals(2, connection2.getNumSameStationTransfers());

            // compare two connections (make sure they are pareto optimal)
            assertTrue(connection1.getDuration() > connection2.getDuration(),
                    "First connection should be slower than second connection");
            assertTrue(connection1.getNumRouteLegs() < connection2.getNumRouteLegs(),
                    "First connection should have fewer route legs than second connection");
        }

        @Test
        void shouldNotFindConnectionBetweenNotLinkedStops() {
            // Remove route R2/R4 to make stop Q (on R3) unreachable from A (on R1)
            List<Utils.Route> routes = ROUTES.stream()
                    .filter(route -> !route.id.equals("R2") && !route.id.equals("R4"))
                    .toList();
            List<Utils.Transfer> transfers = new ArrayList<>();
            Raptor raptor = Utils.buildRaptor(routes, transfers, DAY_START_HOUR, DAY_END_HOUR);
            String sourceStop = "A";
            String targetStop = "Q";
            int departureTime = 8 * SECONDS_IN_HOUR;
            List<Connection> connections = raptor.routeEarliestArrival(sourceStop, targetStop, departureTime);
            assertTrue(connections.isEmpty(), "No connection should be found");
        }

        @Test
        void shouldFindConnectionBetweenOnlyFootpath() {
            // TODO: Fix this test case; The connection returned is R3-R (N -> K), R2-R (K -> B) and R1-F (B -> D)
            //  instead of a footpath (N -> D) only.
            List<Utils.Transfer> transfers = List.of(new Utils.Transfer("N", "D", 1));
            Raptor raptor = Utils.buildRaptor(ROUTES, transfers, DAY_START_HOUR, DAY_END_HOUR);
            String sourceStop = "N";
            String targetStop = "D";
            int departureTime = 8 * SECONDS_IN_HOUR;
            List<Connection> connections = raptor.routeEarliestArrival(sourceStop, targetStop, departureTime);
            assertEquals(1, connections.size());
            Connection connection = connections.getFirst();
            assertEquals(sourceStop, connection.getFromStopId());
            assertEquals(targetStop, connection.getToStopId());
            assertTrue(connection.getDepartureTime() >= departureTime,
                    "Departure time should be greater equal than searched for departure time");
            assertEquals(1, connection.getNumFootPathTransfers());
            assertEquals(1, connection.getNumTransfers());
            assertEquals(0, connection.getNumSameStationTransfers());
            assertEquals(0, connection.getNumRouteLegs());
        }

        @Test
        void shouldThrowErrorWhenRequestBetweenSameStop() {
            // TODO: Throw error
            Raptor raptor = Utils.buildRaptor();
            String sourceStop = "A";
            String targetStop = "A";
            int departureTime = 8 * SECONDS_IN_HOUR;
            List<Connection> connections = raptor.routeEarliestArrival(sourceStop, targetStop, departureTime);
            assertEquals(0, connections.size());
        }

        @Test
        void routeBetweenTwoStopsOnSameRoute() {
            Raptor raptor = Utils.buildRaptor();
            String sourceStop = "A";
            String targetStop = "B";
            int departureTime = 8 * SECONDS_IN_HOUR;
            List<Connection> connections = raptor.routeEarliestArrival(sourceStop, targetStop, departureTime);
            System.out.println(connections);
            assertEquals(1, connections.size());
            Connection connection = connections.getFirst();
            assertEquals(sourceStop, connection.getFromStopId());
            assertEquals(targetStop, connection.getToStopId());
            assertTrue(connection.getDepartureTime() >= departureTime,
                    "Departure time should be greater equal than searched for departure time");
            assertEquals(0, connection.getNumFootPathTransfers());
            assertEquals(0, connection.getNumTransfers());
            assertEquals(0, connection.getNumSameStationTransfers());
        }
    }

}
