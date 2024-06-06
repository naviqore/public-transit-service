package ch.naviqore.raptor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the Raptor class.
 */
@ExtendWith(RaptorTestExtension.class)
class RaptorTest {

    @Nested
    class EarliestArrival {

        @Test
        void shouldFindConnectionsBetweenIntersectingRoutes(RaptorTestBuilder builder) {
            // Should return two pareto optimal connections:
            // 1. Connection (with two route legs and one transfer (including footpath) --> slower but fewer transfers)
            //  - Route R1-F from A to D
            //  - Foot Transfer from D to N
            //  - Route R3-F from N to Q

            // 2. Connection (with three route legs and two transfers (same station) --> faster but more transfers)
            //  - Route R1-F from A to F
            //  - Route R4-R from F to P
            //  - Route R3-F from P to Q
            Raptor raptor = builder.buildWithDefaults();

            String sourceStop = "A";
            String targetStop = "Q";
            int departureTime = 8 * RaptorTestBuilder.SECONDS_IN_HOUR;
            List<Connection> connections = raptor.routeEarliestArrival(sourceStop, targetStop, departureTime);

            // check if 2 connections were found
            assertEquals(2, connections.size());
            Helpers.assertConnection(connections.getFirst(), sourceStop, targetStop, departureTime, 0, 1, 2);
            Helpers.assertConnection(connections.get(1), sourceStop, targetStop, departureTime, 2, 0, 3);
            Helpers.checkIfConnectionsAreParetoOptimal(connections);
        }

        @Test
        void routeBetweenTwoStopsOnSameRoute(RaptorTestBuilder builder) {
            Raptor raptor = builder.buildWithDefaults();

            String sourceStop = "A";
            String targetStop = "B";
            int departureTime = 8 * RaptorTestBuilder.SECONDS_IN_HOUR;
            List<Connection> connections = raptor.routeEarliestArrival(sourceStop, targetStop, departureTime);
            assertEquals(1, connections.size());
            Helpers.assertConnection(connections.getFirst(), sourceStop, targetStop, departureTime, 0, 0, 1);
        }

        @Test
        void routeWithSelfIntersectingRoute(RaptorTestBuilder builder) {
            builder.withAddRoute5_AH_selfIntersecting();
            Raptor raptor = builder.build();

            String sourceStop = "A";
            String targetStop = "H";
            int departureTime = 10 * RaptorTestBuilder.SECONDS_IN_HOUR;

            List<Connection> connections = raptor.routeEarliestArrival(sourceStop, targetStop, departureTime);
            assertEquals(2, connections.size());

            // First Connection Should have no transfers but ride the entire loop (slow)
            Helpers.assertConnection(connections.getFirst(), sourceStop, targetStop, departureTime, 0, 0, 1);
            // Second Connection Should Change at Stop B and take the earlier trip of the same route there (faster)
            Helpers.assertConnection(connections.get(1), sourceStop, targetStop, departureTime, 1, 0, 2);

            Helpers.checkIfConnectionsAreParetoOptimal(connections);
        }

        @Test
        void routeFromTwoSourceStopsWithSameDepartureTime(RaptorTestBuilder builder) {
            Raptor raptor = builder.buildWithDefaults();

            int departureTime = 8 * RaptorTestBuilder.SECONDS_IN_HOUR;
            Map<String, Integer> sourceStops = Map.of("A", departureTime, "B", departureTime);
            Map<String, Integer> targetStops = Map.of("H", 0);

            // fastest and only connection should be B -> H
            List<Connection> connections = raptor.routeEarliestArrival(sourceStops, targetStops);
            assertEquals(1, connections.size());
            Helpers.assertConnection(connections.getFirst(), "B", "H", departureTime, 0, 0, 1);
        }

        @Test
        void routeFromTwoSourceStopsWithLaterDepartureTimeOnCloserStop(RaptorTestBuilder builder) {
            Raptor raptor = builder.buildWithDefaults();

            int departureTime = 8 * RaptorTestBuilder.SECONDS_IN_HOUR;
            Map<String, Integer> sourceStops = Map.of("A", departureTime, "B",
                    departureTime + RaptorTestBuilder.SECONDS_IN_HOUR);
            Map<String, Integer> targetStops = Map.of("H", 0);

            // fastest and only connection should be B -> H
            List<Connection> connections = raptor.routeEarliestArrival(sourceStops, targetStops);
            assertEquals(2, connections.size());
            Helpers.assertConnection(connections.getFirst(), "B", "H",
                    departureTime + RaptorTestBuilder.SECONDS_IN_HOUR, 0, 0, 1);
            Helpers.assertConnection(connections.get(1), "A", "H", departureTime, 1, 0, 2);
        }

        @Test
        void routeFromStopToTwoTargetStopsNoWalkTimeToTarget(RaptorTestBuilder builder) {
            Raptor raptor = builder.buildWithDefaults();

            int departureTime = 8 * RaptorTestBuilder.SECONDS_IN_HOUR;
            Map<String, Integer> sourceStops = Map.of("A", departureTime);
            Map<String, Integer> targetStops = Map.of("F", 0, "S", 0);

            // fastest and only connection should be A -> F
            List<Connection> connections = raptor.routeEarliestArrival(sourceStops, targetStops);
            assertEquals(1, connections.size());
            Helpers.assertConnection(connections.getFirst(), "A", "F", departureTime, 0, 0, 1);
        }

        @Test
        void routeFromStopToTwoTargetStopsWithWalkTimeToTarget(RaptorTestBuilder builder) {
            Raptor raptor = builder.buildWithDefaults();

            int departureTime = 8 * RaptorTestBuilder.SECONDS_IN_HOUR;
            Map<String, Integer> sourceStops = Map.of("A", departureTime);
            Map<String, Integer> targetStops = Map.of("F", RaptorTestBuilder.SECONDS_IN_HOUR, "S", 0);

            // since F is closer to A than S, the fastest connection should be A -> F, but because of the hour
            // walk time to target, the connection A -> S should be faster (no additional walk time)
            List<Connection> connections = raptor.routeEarliestArrival(sourceStops, targetStops);
            assertEquals(2, connections.size());
            Helpers.assertConnection(connections.getFirst(), "A", "F", departureTime, 0, 0, 1);
            Helpers.assertConnection(connections.get(1), "A", "S", departureTime, 1, 0, 2);

            // Note since the required walk time to target is not added as a leg, the solutions will not be pareto
            // optimal without additional post-processing.
        }

        @Test
        void shouldNotFindConnectionBetweenNotLinkedStops(RaptorTestBuilder builder) {
            // Omit route R2/R4 and transfers to make stop Q (on R3) unreachable from A (on R1)
            Raptor raptor = builder.withAddRoute1_AG().withAddRoute3_MQ().build();

            String sourceStop = "A";
            String targetStop = "Q";
            int departureTime = 8 * RaptorTestBuilder.SECONDS_IN_HOUR;
            List<Connection> connections = raptor.routeEarliestArrival(sourceStop, targetStop, departureTime);
            assertTrue(connections.isEmpty(), "No connection should be found");
        }

        @Test
        void shouldFindConnectionBetweenOnlyFootpath(RaptorTestBuilder builder) {
            Raptor raptor = builder.withAddRoute1_AG()
                    .withAddRoute2_HL()
                    .withAddRoute3_MQ()
                    .withAddRoute4_RS()
                    .withAddTransfer1_ND(1)
                    .withAddTransfer2_LR()
                    .build();

            String sourceStop = "N";
            String targetStop = "D";
            int departureTime = 8 * RaptorTestBuilder.SECONDS_IN_HOUR;
            List<Connection> connections = raptor.routeEarliestArrival(sourceStop, targetStop, departureTime);
            assertEquals(1, connections.size());
            Helpers.assertConnection(connections.getFirst(), sourceStop, targetStop, departureTime, 0, 1, 0);
        }

        private static class Helpers {

            private static void assertConnection(Connection connection, String sourceStop, String targetStop,
                                                 int departureTime, int numSameStationTransfers, int numWalkTransfers,
                                                 int numTrips) {
                assertEquals(sourceStop, connection.getFromStopId());
                assertEquals(targetStop, connection.getToStopId());
                assertTrue(connection.getDepartureTime() >= departureTime,
                        "Departure time should be greater equal than searched for departure time");

                assertEquals(numSameStationTransfers, connection.getNumberOfSameStationTransfers(),
                        "Number of same station transfers should match");
                assertEquals(numWalkTransfers, connection.getWalkTransfers().size(),
                        "Number of walk transfers should match");
                assertEquals(numSameStationTransfers + numWalkTransfers, connection.getNumberOfTotalTransfers(),
                        "Number of transfers should match");

                assertEquals(numTrips, connection.getRouteLegs().size(), "Number of trips should match");
            }

            private static void checkIfConnectionsAreParetoOptimal(List<Connection> connections) {
                Connection previousConnection = connections.getFirst();
                for (int i = 1; i < connections.size(); i++) {
                    Connection currentConnection = connections.get(i);
                    assertTrue(previousConnection.getDuration() > currentConnection.getDuration(),
                            "Previous connection should be slower than current connection");
                    assertTrue(previousConnection.getRouteLegs().size() < currentConnection.getRouteLegs().size(),
                            "Previous connection should have fewer route legs than current connection");
                    previousConnection = currentConnection;
                }
            }

        }

        @Nested
        class InputValidation {

            private Raptor raptor;

            @BeforeEach
            void setUp(RaptorTestBuilder builder) {
                raptor = builder.buildWithDefaults();
            }

            @Test
            void shouldThrowErrorWhenSourceStopNotExists() {
                String sourceStop = "NonExistentStop";
                String targetStop = "A";
                int departureTime = 8 * RaptorTestBuilder.SECONDS_IN_HOUR;

                assertThrows(IllegalArgumentException.class,
                        () -> raptor.routeEarliestArrival(sourceStop, targetStop, departureTime),
                        "Source stop has to exists");
            }

            @Test
            void shouldThrowErrorWhenTargetStopNotExists() {
                String sourceStop = "A";
                String targetStop = "NonExistentStop";
                int departureTime = 8 * RaptorTestBuilder.SECONDS_IN_HOUR;

                assertThrows(IllegalArgumentException.class,
                        () -> raptor.routeEarliestArrival(sourceStop, targetStop, departureTime),
                        "Target stop has to exists");
            }

            @Test
            void shouldNotThrowErrorForValidAndNonExistingSourceStop() {
                int departureTime = 8 * RaptorTestBuilder.SECONDS_IN_HOUR;
                Map<String, Integer> sourceStops = Map.of("A", departureTime, "NonExistentStop", departureTime);
                Map<String, Integer> targetStops = Map.of("H", 0);

                assertDoesNotThrow(() -> raptor.routeEarliestArrival(sourceStops, targetStops),
                        "Source stops can contain non-existing stops, if one entry is valid");
            }

            @Test
            void shouldThrowErrorForInvalidDepartureTimeFromOneOfManySourceStops() {
                int departureTime = 8 * RaptorTestBuilder.SECONDS_IN_HOUR;
                Map<String, Integer> sourceStops = Map.of("A", departureTime, "B", Integer.MAX_VALUE);
                Map<String, Integer> targetStops = Map.of("H", 0);

                assertThrows(IllegalArgumentException.class,
                        () -> raptor.routeEarliestArrival(sourceStops, targetStops),
                        "Departure time has to be valid for all valid source stops");
            }

            @Test
            void shouldNotThrowErrorForValidAndNonExistingTargetStop() {
                int departureTime = 8 * RaptorTestBuilder.SECONDS_IN_HOUR;
                Map<String, Integer> sourceStops = Map.of("H", departureTime);
                Map<String, Integer> targetStops = Map.of("A", 0, "NonExistentStop", 0);

                assertDoesNotThrow(() -> raptor.routeEarliestArrival(sourceStops, targetStops),
                        "Target stops can contain non-existing stops, if one entry is valid");
            }

            @Test
            void shouldThrowErrorForInvalidWalkToTargetTimeFromOneOfManyTargetStops() {
                int departureTime = 8 * RaptorTestBuilder.SECONDS_IN_HOUR;
                Map<String, Integer> sourceStops = Map.of("H", departureTime);
                Map<String, Integer> targetStops = Map.of("A", 0, "B", -1);

                assertThrows(IllegalArgumentException.class,
                        () -> raptor.routeEarliestArrival(sourceStops, targetStops),
                        "Departure time has to be valid for all valid source stops");
            }

            @Test
            void shouldThrowErrorNullSourceStops(){
                Map<String, Integer> sourceStops = null;
                Map<String, Integer> targetStops = Map.of("H", 0);

                assertThrows(IllegalArgumentException.class,
                        () -> raptor.routeEarliestArrival(sourceStops, targetStops),
                        "Source stops cannot be null");
            }

            @Test
            void shouldThrowErrorNullTargetStops(){
                Map<String, Integer> sourceStops = Map.of("A", 0);
                Map<String, Integer> targetStops = null;

                assertThrows(IllegalArgumentException.class,
                        () -> raptor.routeEarliestArrival(sourceStops, targetStops),
                        "Target stops cannot be null");
            }

            @Test
            void shouldThrowErrorEmptyMapSourceStops(){
                Map<String, Integer> sourceStops = Map.of();
                Map<String, Integer> targetStops = Map.of("H", 0);

                assertThrows(IllegalArgumentException.class,
                        () -> raptor.routeEarliestArrival(sourceStops, targetStops),
                        "Source and target stops cannot be null");
            }

            @Test
            void shouldThrowErrorEmptyMapTargetStops(){
                Map<String, Integer> sourceStops = Map.of("A", 0);
                Map<String, Integer> targetStops = Map.of();

                assertThrows(IllegalArgumentException.class,
                        () -> raptor.routeEarliestArrival(sourceStops, targetStops),
                        "Source and target stops cannot be null");
            }

            @Test
            void shouldThrowErrorWhenDepartureTimeIsOutOfRange() {
                String sourceStop = "A";
                String targetStop = "B";

                assertThrows(IllegalArgumentException.class,
                        () -> raptor.routeEarliestArrival(sourceStop, targetStop, -1),
                        "Departure time cannot be negative");
                assertThrows(IllegalArgumentException.class, () -> raptor.routeEarliestArrival(sourceStop, targetStop,
                        49 * RaptorTestBuilder.SECONDS_IN_HOUR), "Departure time cannot be greater than two days");
            }

            @Test
            void shouldThrowErrorWhenRequestBetweenSameStop() {
                String sourceStop = "A";
                String targetStop = "A";
                int departureTime = 8 * RaptorTestBuilder.SECONDS_IN_HOUR;

                assertThrows(IllegalArgumentException.class,
                        () -> raptor.routeEarliestArrival(sourceStop, targetStop, departureTime),
                        "Stops cannot be the same");
            }

        }

    }

}
