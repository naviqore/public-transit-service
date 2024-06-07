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

        @Test
        void shouldTakeFasterRouteOfOverlappingRoutes(RaptorTestBuilder builder) {
            // Create Two Versions of the same route with different travel speeds (both leaving at same time from A)
            Raptor raptor = builder.withAddRoute1_AG().withAddRoute1_AG("R1X", 0, 15, 3, 1).build();
            String sourceStop = "A";
            String targetStop = "G";
            int departureTime = 8 * RaptorTestBuilder.SECONDS_IN_HOUR;
            List<Connection> connections = raptor.routeEarliestArrival(sourceStop, targetStop, departureTime);

            // Both Routes leave at 8:00 at Stop A, but R1 arrives at G at 8:35 whereas R1X arrives at G at 8:23
            // R1X should be taken
            assertEquals(1, connections.size());
            Helpers.assertConnection(connections.getFirst(), sourceStop, targetStop, departureTime, 0, 0, 1);
            // check departure at 8:00
            Connection connection = connections.getFirst();
            assertEquals(departureTime, connection.getDepartureTime());
            // check arrival time at 8:23
            assertEquals(departureTime + 23 * 60, connection.getArrivalTime());
            // check that R1X(-F for forward) route was used
            assertEquals("R1X-F", connection.getRouteLegs().getFirst().routeId());
        }

        @Test
        void shouldTakeSlowerRouteOfOverlappingRoutesDueToEarlierDepartureTime(RaptorTestBuilder builder) {
            // Create Two Versions of the same route with different travel speeds and different departure times
            Raptor raptor = builder.withAddRoute1_AG().withAddRoute1_AG("R1X", 15, 30, 3, 1).build();
            String sourceStop = "A";
            String targetStop = "G";
            int departureTime = 8 * RaptorTestBuilder.SECONDS_IN_HOUR;
            List<Connection> connections = raptor.routeEarliestArrival(sourceStop, targetStop, departureTime);

            // Route R1 leaves at 8:00 at Stop A and arrives at G at 8:35 whereas R1X leaves at 8:15 from Stop A and
            // arrives at G at 8:38. R1 should be used.
            assertEquals(1, connections.size());
            Helpers.assertConnection(connections.getFirst(), sourceStop, targetStop, departureTime, 0, 0, 1);
            // check departure at 8:00
            Connection connection = connections.getFirst();
            assertEquals(departureTime, connection.getDepartureTime());
            // check arrival time at 8:35
            assertEquals(departureTime + 35 * 60, connection.getArrivalTime());
            // check that R1(-F for forward) route was used
            assertEquals("R1-F", connection.getRouteLegs().getFirst().routeId());
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

    }

    @Nested
    class SameStationTransfers {

        @Test
        void shouldTakeFirstTripWithoutAddingSameStationTransferTime(RaptorTestBuilder builder) {
            Raptor raptor = builder.buildWithDefaults();
            // There should be a connection leaving stop A at 5:00 am
            String sourceStop = "A";
            String targetStop = "B";
            int departureTime = 5 * RaptorTestBuilder.SECONDS_IN_HOUR;
            List<Connection> connections = raptor.routeEarliestArrival(sourceStop, targetStop, departureTime);
            assertEquals(1, connections.size());
            assertEquals(departureTime, connections.getFirst().getDepartureTime());
        }

        @Test
        void shouldMissConnectingTripBecauseOfSameStationTransferTime(RaptorTestBuilder builder) {
            Raptor raptor = builder.withAddRoute1_AG(19, 15, 5, 1).withAddRoute2_HL().build();
            // TODO: Adjust when same station transfers are stop specific
            // There should be a connection leaving stop A at 5:19 am and arriving at stop B at 5:24 am
            // Connection at 5:24 from B to C should be missed because of the same station transfer time (120s)
            String sourceStop = "A";
            String targetStop = "H";
            int departureTime = 5 * RaptorTestBuilder.SECONDS_IN_HOUR;
            List<Connection> connections = raptor.routeEarliestArrival(sourceStop, targetStop, departureTime);

            assertEquals(1, connections.size());
            assertEquals(departureTime + 19 * 60, connections.getFirst().getDepartureTime());

            assertNotEquals(departureTime + 24 * 60, connections.getFirst().getLegs().get(1).departureTime());
        }

        @Test
        void shouldCatchConnectingTripBecauseWithSameStationTransferTime(RaptorTestBuilder builder) {
            Raptor raptor = builder.withAddRoute1_AG(17, 15, 5, 1).withAddRoute2_HL().build();
            // TODO: Adjust when same station transfers are stop specific
            // There should be a connection leaving stop A at 5:17 am and arriving at stop B at 5:22 am
            // Connection at 5:24 from B to C should be cached when the same station transfer time is 120s
            String sourceStop = "A";
            String targetStop = "H";
            int departureTime = 5 * RaptorTestBuilder.SECONDS_IN_HOUR;
            List<Connection> connections = raptor.routeEarliestArrival(sourceStop, targetStop, departureTime);

            assertEquals(1, connections.size());
            assertEquals(departureTime + 17 * 60, connections.getFirst().getDepartureTime());

            assertEquals(departureTime + 24 * 60, connections.getFirst().getLegs().get(1).departureTime());
        }

    }

    @Nested
    class IsoLines {

        @Test
        void shouldCreateIsoLinesToAllStops(RaptorTestBuilder builder) {
            Raptor raptor = builder.buildWithDefaults();

            String sourceStop = "A";
            int departureTime = 8 * RaptorTestBuilder.SECONDS_IN_HOUR;
            Map<String, Connection> isoLines = raptor.getIsoLines(Map.of(sourceStop, departureTime));

            int stopsInSystem = 19;
            int expectedIsoLines = stopsInSystem - 1;
            Helpers.assertIsoLines(isoLines, sourceStop, departureTime, expectedIsoLines);
        }

        @Test
        void shouldCreateIsoLinesToSomeStopsNotAllConnected(RaptorTestBuilder builder) {
            // Route 1 and 3 are not connected, thus all Stops of Route 3 should not be reachable from A
            Raptor raptor = builder.withAddRoute1_AG().withAddRoute3_MQ().build();

            String sourceStop = "A";
            int departureTime = 8 * RaptorTestBuilder.SECONDS_IN_HOUR;
            Map<String, Connection> isoLines = raptor.getIsoLines(Map.of(sourceStop, departureTime));

            List<String> reachableStops = List.of("B", "C", "D", "E", "F", "G");
            // Not Reachable Stops: M, K, N, O, P, Q

            Helpers.assertIsoLines(isoLines, sourceStop, departureTime, reachableStops.size());

            for (String stop : reachableStops) {
                assertTrue(isoLines.containsKey(stop), "Stop " + stop + " should be reachable");
            }
        }

        @Test
        void shouldCreateIsoLinesToStopsOfOtherLineOnlyConnectedByFootpath(RaptorTestBuilder builder) {
            // Route 1 and Route 3 are only connected by Footpath between Stops D and N
            Raptor raptor = builder.withAddRoute1_AG().withAddRoute3_MQ().withAddTransfer1_ND().build();

            String sourceStop = "A";
            int departureTime = 8 * RaptorTestBuilder.SECONDS_IN_HOUR;
            Map<String, Connection> isoLines = raptor.getIsoLines(Map.of(sourceStop, departureTime));

            List<String> reachableStops = List.of("B", "C", "D", "E", "F", "G", "M", "K", "N", "O", "P", "Q");

            Helpers.assertIsoLines(isoLines, sourceStop, departureTime, reachableStops.size());

            for (String stop : reachableStops) {
                assertTrue(isoLines.containsKey(stop), "Stop " + stop + " should be reachable");
            }
        }

        @Test
        void shouldCreateIsoLinesFromTwoNotConnectedSourceStops(RaptorTestBuilder builder) {
            Raptor raptor = builder.withAddRoute1_AG().withAddRoute3_MQ().build();

            Map<String, Integer> departureTimeHours = Map.of("A", 8, "M", 16);

            List<String> reachableStopsFromStopA = List.of("B", "C", "D", "E", "F", "G");
            Map<String, Integer> sourceStops = Map.of("A", departureTimeHours.get("A") * RaptorTestBuilder.SECONDS_IN_HOUR,
                    "M", departureTimeHours.get("M") * RaptorTestBuilder.SECONDS_IN_HOUR);
            List<String> reachableStopsFromStopM = List.of("K", "N", "O", "P", "Q");

            Map<String, Connection> isoLines = raptor.getIsoLines(sourceStops);

            assertEquals(reachableStopsFromStopA.size() + reachableStopsFromStopM.size(), isoLines.size());

            Map<String, List<String>> sourceTargets = Map.of("A", reachableStopsFromStopA, "M",
                    reachableStopsFromStopM);

            for (Map.Entry<String, List<String>> entry : sourceTargets.entrySet()) {
                String sourceStop = entry.getKey();
                List<String> reachableStops = entry.getValue();
                int departureTimeHour = departureTimeHours.get(sourceStop);
                int departureTime = departureTimeHour * RaptorTestBuilder.SECONDS_IN_HOUR;
                for (String stop : reachableStops) {
                    assertTrue(isoLines.containsKey(stop), "Stop " + stop + " should be reachable from " + sourceStop);
                    Connection connection = isoLines.get(stop);
                    assertTrue(connection.getDepartureTime() >= departureTime,
                            String.format("Connection should have departure time equal or after %d:00",
                                    departureTimeHour));
                    assertTrue(connection.getArrivalTime() < Integer.MAX_VALUE,
                            "Connection should have arrival time before infinity");
                    assertEquals(connection.getFromStopId(), sourceStop, "From stop should be " + sourceStop);
                    assertEquals(connection.getToStopId(), stop, "To stop should be " + stop);
                }
            }
        }

        private static class Helpers {

            private static final int INFINITY = Integer.MAX_VALUE;

            private static void assertIsoLines(Map<String, Connection> isoLines, String sourceStopId, int departureTime,
                                               int expectedIsoLines) {
                assertEquals(expectedIsoLines, isoLines.size());
                assertFalse(isoLines.containsKey(sourceStopId), "Source stop should not be in iso lines");
                for (Map.Entry<String, Connection> entry : isoLines.entrySet()) {
                    assertTrue(departureTime <= entry.getValue().getDepartureTime(),
                            "Departure time should be greater than or equal to departure time");
                    assertTrue(departureTime < entry.getValue().getArrivalTime(),
                            "Arrival time should be greater than or equal to departure time");
                    assertTrue(entry.getValue().getArrivalTime() < INFINITY,
                            "Arrival time should be less than INFINITY");
                    assertEquals(sourceStopId, entry.getValue().getFromStopId(), "From stop should be source stop");
                    assertEquals(entry.getKey(), entry.getValue().getToStopId(), "To stop should be key of map entry");
                }
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

            assertThrows(IllegalArgumentException.class, () -> raptor.routeEarliestArrival(sourceStops, targetStops),
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

            assertThrows(IllegalArgumentException.class, () -> raptor.routeEarliestArrival(sourceStops, targetStops),
                    "Departure time has to be valid for all valid source stops");
        }

        @Test
        void shouldThrowErrorNullSourceStops() {
            Map<String, Integer> sourceStops = null;
            Map<String, Integer> targetStops = Map.of("H", 0);

            assertThrows(IllegalArgumentException.class, () -> raptor.routeEarliestArrival(sourceStops, targetStops),
                    "Source stops cannot be null");
        }

        @Test
        void shouldThrowErrorNullTargetStops() {
            Map<String, Integer> sourceStops = Map.of("A", 0);
            Map<String, Integer> targetStops = null;

            assertThrows(IllegalArgumentException.class, () -> raptor.routeEarliestArrival(sourceStops, targetStops),
                    "Target stops cannot be null");
        }

        @Test
        void shouldThrowErrorEmptyMapSourceStops() {
            Map<String, Integer> sourceStops = Map.of();
            Map<String, Integer> targetStops = Map.of("H", 0);

            assertThrows(IllegalArgumentException.class, () -> raptor.routeEarliestArrival(sourceStops, targetStops),
                    "Source and target stops cannot be null");
        }

        @Test
        void shouldThrowErrorEmptyMapTargetStops() {
            Map<String, Integer> sourceStops = Map.of("A", 0);
            Map<String, Integer> targetStops = Map.of();

            assertThrows(IllegalArgumentException.class, () -> raptor.routeEarliestArrival(sourceStops, targetStops),
                    "Source and target stops cannot be null");
        }

        @Test
        void shouldThrowErrorWhenDepartureTimeIsOutOfRange() {
            String sourceStop = "A";
            String targetStop = "B";

            assertThrows(IllegalArgumentException.class, () -> raptor.routeEarliestArrival(sourceStop, targetStop, -1),
                    "Departure time cannot be negative");
            assertThrows(IllegalArgumentException.class,
                    () -> raptor.routeEarliestArrival(sourceStop, targetStop, 49 * RaptorTestBuilder.SECONDS_IN_HOUR),
                    "Departure time cannot be greater than two days");
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
