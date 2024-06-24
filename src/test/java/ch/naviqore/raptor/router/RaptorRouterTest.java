package ch.naviqore.raptor.router;

import ch.naviqore.raptor.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the Raptor class.
 */
@ExtendWith(RaptorRouterTestExtension.class)
class RaptorRouterTest {

    private static final int INFINITY = Integer.MAX_VALUE;

    private static final String STOP_A = "A";
    private static final String STOP_B = "B";
    private static final String STOP_C = "C";
    private static final String STOP_D = "D";
    private static final String STOP_E = "E";
    private static final String STOP_F = "F";
    private static final String STOP_G = "G";
    private static final String STOP_H = "H";
    private static final String STOP_K = "K";
    private static final String STOP_N = "N";
    private static final String STOP_M = "M";
    private static final String STOP_O = "O";
    private static final String STOP_P = "P";
    private static final String STOP_Q = "Q";
    private static final String STOP_S = "S";

    private static final int FIVE_AM = 5 * RaptorRouterTestBuilder.SECONDS_IN_HOUR;
    private static final int EIGHT_AM = 8 * RaptorRouterTestBuilder.SECONDS_IN_HOUR;
    private static final int NINE_AM = 9 * RaptorRouterTestBuilder.SECONDS_IN_HOUR;

    static class ConvenienceMethods {

        static Map<String, Connection> getIsoLines(RaptorAlgorithm raptor, Map<String, Integer> sourceStops) {
            return getIsoLines(raptor, sourceStops, new QueryConfig());
        }

        static Map<String, Connection> getIsoLines(RaptorAlgorithm raptor, Map<String, Integer> sourceStops,
                                                   QueryConfig config) {
            return raptor.routeIsolines(createStopTimeMap(sourceStops), TimeType.DEPARTURE, config);
        }

        static List<Connection> routeEarliestArrival(RaptorAlgorithm raptor, String sourceStopId, String targetStopId,
                                                     int departureTime) {
            return routeEarliestArrival(raptor, createStopMap(sourceStopId, departureTime),
                    createStopMap(targetStopId, 0));
        }

        static List<Connection> routeEarliestArrival(RaptorAlgorithm raptor, String sourceStopId, String targetStopId,
                                                     int departureTime, QueryConfig config) {
            return routeEarliestArrival(raptor, createStopMap(sourceStopId, departureTime),
                    createStopMap(targetStopId, 0), config);
        }

        static Map<String, Integer> createStopMap(String stopId, int value) {
            return Map.of(stopId, value);
        }

        static List<Connection> routeEarliestArrival(RaptorAlgorithm raptor, Map<String, Integer> sourceStops,
                                                     Map<String, Integer> targetStopIds) {
            return routeEarliestArrival(raptor, sourceStops, targetStopIds, new QueryConfig());
        }

        static List<Connection> routeEarliestArrival(RaptorAlgorithm raptor, Map<String, Integer> sourceStops,
                                                     Map<String, Integer> targetStopIds, QueryConfig config) {
            return raptor.routeEarliestArrival(createStopTimeMap(sourceStops), targetStopIds, config);
        }

        static List<Connection> routeLatestDeparture(RaptorAlgorithm raptor, String sourceStopId, String targetStopId,
                                                     int arrivalTime) {
            return routeLatestDeparture(raptor, createStopMap(sourceStopId, 0),
                    createStopMap(targetStopId, arrivalTime));
        }

        static List<Connection> routeLatestDeparture(RaptorAlgorithm raptor, Map<String, Integer> sourceStops,
                                                     Map<String, Integer> targetStops) {
            return routeLatestDeparture(raptor, sourceStops, targetStops, new QueryConfig());
        }

        static List<Connection> routeLatestDeparture(RaptorAlgorithm raptor, Map<String, Integer> sourceStops,
                                                     Map<String, Integer> targetStops, QueryConfig config) {
            return raptor.routeLatestDeparture(sourceStops, createStopTimeMap(targetStops), config);
        }

        static Map<String, LocalDateTime> createStopTimeMap(Map<String, Integer> sourceStops) {
            if (sourceStops == null) {
                return null;
            }
            return sourceStops.entrySet()
                    .stream()
                    .map(entry -> Map.entry(entry.getKey(),
                            LocalDateTime.of(2021, 1, 1, 0, 0).plusSeconds(entry.getValue())))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        }

    }

    @Nested
    class EarliestArrival {

        @Test
        void findConnectionsBetweenIntersectingRoutes(RaptorRouterTestBuilder builder) {
            // Should return two pareto optimal connections:
            // 1. Connection (with two route legs and one transfer (including footpath) --> slower but fewer transfers)
            //  - Route R1-F from A to D
            //  - Foot Transfer from D to N
            //  - Route R3-F from N to Q

            // 2. Connection (with three route legs and two transfers (same stop) --> faster but more transfers)
            //  - Route R1-F from A to F
            //  - Route R4-R from F to P
            //  - Route R3-F from P to Q
            RaptorAlgorithm raptor = builder.buildWithDefaults();

            List<Connection> connections = ConvenienceMethods.routeEarliestArrival(raptor, STOP_A, STOP_Q, EIGHT_AM);

            // check if 2 connections were found
            assertEquals(2, connections.size());
            Helpers.assertConnection(connections.getFirst(), STOP_A, STOP_Q, EIGHT_AM, 0, 1, 2);
            Helpers.assertConnection(connections.get(1), STOP_A, STOP_Q, EIGHT_AM, 2, 0, 3);
            Helpers.checkIfConnectionsAreParetoOptimal(connections);
        }

        @Test
        void routeBetweenTwoStopsOnSameRoute(RaptorRouterTestBuilder builder) {
            RaptorAlgorithm raptor = builder.buildWithDefaults();

            List<Connection> connections = ConvenienceMethods.routeEarliestArrival(raptor, STOP_A, STOP_B, EIGHT_AM);
            assertEquals(1, connections.size());
            Helpers.assertConnection(connections.getFirst(), STOP_A, STOP_B, EIGHT_AM, 0, 0, 1);
        }

        @Test
        void routeWithSelfIntersectingRoute(RaptorRouterTestBuilder builder) {
            builder.withAddRoute5_AH_selfIntersecting();
            RaptorAlgorithm raptor = builder.build();

            List<Connection> connections = ConvenienceMethods.routeEarliestArrival(raptor, STOP_A, STOP_H, EIGHT_AM);
            assertEquals(2, connections.size());

            // First Connection Should have no transfers but ride the entire loop (slow)
            Helpers.assertConnection(connections.getFirst(), STOP_A, STOP_H, EIGHT_AM, 0, 0, 1);
            // Second Connection Should Change at Stop B and take the earlier trip of the same route there (faster)
            Helpers.assertConnection(connections.get(1), STOP_A, STOP_H, EIGHT_AM, 1, 0, 2);

            Helpers.checkIfConnectionsAreParetoOptimal(connections);
        }

        @Test
        void routeFromTwoSourceStopsWithSameDepartureTime(RaptorRouterTestBuilder builder) {
            RaptorAlgorithm raptor = builder.buildWithDefaults();

            Map<String, Integer> sourceStops = Map.of(STOP_A, EIGHT_AM, STOP_B, EIGHT_AM);
            Map<String, Integer> targetStops = Map.of(STOP_H, 0);

            // fastest and only connection should be B -> H
            List<Connection> connections = ConvenienceMethods.routeEarliestArrival(raptor, sourceStops, targetStops);
            assertEquals(1, connections.size());
            Helpers.assertConnection(connections.getFirst(), STOP_B, STOP_H, EIGHT_AM, 0, 0, 1);
        }

        @Test
        void routeFromTwoSourceStopsWithLaterDepartureTimeOnCloserStop(RaptorRouterTestBuilder builder) {
            RaptorAlgorithm raptor = builder.buildWithDefaults();

            Map<String, Integer> sourceStops = Map.of(STOP_A, EIGHT_AM, STOP_B, NINE_AM);
            Map<String, Integer> targetStops = Map.of(STOP_H, 0);

            // B -> H has no transfers but later arrival time (due to departure time one hour later)
            // A -> H has one transfer but earlier arrival time
            List<Connection> connections = ConvenienceMethods.routeEarliestArrival(raptor, sourceStops, targetStops);
            assertEquals(2, connections.size());
            Helpers.assertConnection(connections.getFirst(), STOP_B, STOP_H, NINE_AM, 0, 0, 1);
            Helpers.assertConnection(connections.get(1), STOP_A, STOP_H, EIGHT_AM, 1, 0, 2);
            assertTrue(connections.getFirst().getArrivalTime() > connections.get(1).getArrivalTime(),
                    "Connection from A should arrive earlier than connection from B");
        }

        @Test
        void routeFromStopToTwoTargetStopsNoWalkTimeToTarget(RaptorRouterTestBuilder builder) {
            RaptorAlgorithm raptor = builder.buildWithDefaults();

            Map<String, Integer> sourceStops = Map.of(STOP_A, EIGHT_AM);
            Map<String, Integer> targetStops = Map.of(STOP_F, 0, STOP_S, 0);

            // fastest and only connection should be A -> F
            List<Connection> connections = ConvenienceMethods.routeEarliestArrival(raptor, sourceStops, targetStops);
            assertEquals(1, connections.size());
            Helpers.assertConnection(connections.getFirst(), STOP_A, STOP_F, EIGHT_AM, 0, 0, 1);
        }

        @Test
        void routeFromStopToTwoTargetStopsWithWalkTimeToTarget(RaptorRouterTestBuilder builder) {
            RaptorAlgorithm raptor = builder.buildWithDefaults();

            Map<String, Integer> sourceStops = Map.of(STOP_A, EIGHT_AM);
            // Add one-hour walk time to target from stop F and no extra walk time from stop S
            Map<String, Integer> targetStops = Map.of(STOP_F, RaptorRouterTestBuilder.SECONDS_IN_HOUR, STOP_S, 0);

            // since F is closer to A than S, the fastest connection should be A -> F, but because of the hour
            // walk time to target, the connection A -> S should be faster (no additional walk time)
            List<Connection> connections = ConvenienceMethods.routeEarliestArrival(raptor, sourceStops, targetStops);
            assertEquals(2, connections.size());
            Helpers.assertConnection(connections.getFirst(), STOP_A, STOP_F, EIGHT_AM, 0, 0, 1);
            Helpers.assertConnection(connections.get(1), STOP_A, STOP_S, EIGHT_AM, 1, 0, 2);

            // Note since the required walk time to target is not added as a leg, the solutions will not be pareto
            // optimal without additional post-processing.
        }

        @Test
        void notFindConnectionBetweenNotLinkedStops(RaptorRouterTestBuilder builder) {
            // Omit route R2/R4 and transfers to make stop Q (on R3) unreachable from A (on R1)
            RaptorAlgorithm raptor = builder.withAddRoute1_AG().withAddRoute3_MQ().build();

            List<Connection> connections = ConvenienceMethods.routeEarliestArrival(raptor, STOP_A, STOP_Q, EIGHT_AM);
            assertTrue(connections.isEmpty(), "No connection should be found");
        }

        @Test
        void findConnectionBetweenOnlyFootpath(RaptorRouterTestBuilder builder) {
            RaptorAlgorithm raptor = builder.withAddRoute1_AG()
                    .withAddRoute2_HL()
                    .withAddRoute3_MQ()
                    .withAddRoute4_RS()
                    .withAddTransfer1_ND(1)
                    .withAddTransfer2_LR()
                    .build();

            List<Connection> connections = ConvenienceMethods.routeEarliestArrival(raptor, STOP_N, STOP_D, EIGHT_AM);
            assertEquals(1, connections.size());
            Helpers.assertConnection(connections.getFirst(), STOP_N, STOP_D, EIGHT_AM, 0, 1, 0);
        }

        @Test
        void takeFasterRouteOfOverlappingRoutes(RaptorRouterTestBuilder builder) {
            // Create Two Versions of the same route with different travel speeds (both leaving at same time from A)
            RaptorAlgorithm raptor = builder.withAddRoute1_AG()
                    .withAddRoute1_AG("R1X", RaptorRouterTestBuilder.DEFAULT_OFFSET,
                            RaptorRouterTestBuilder.DEFAULT_HEADWAY_TIME, 3, RaptorRouterTestBuilder.DEFAULT_DWELL_TIME)
                    .build();
            List<Connection> connections = ConvenienceMethods.routeEarliestArrival(raptor, STOP_A, STOP_G, EIGHT_AM);

            // Both Routes leave at 8:00 at Stop A, but R1 arrives at G at 8:35 whereas R1X arrives at G at 8:23
            // R1X should be taken
            assertEquals(1, connections.size());
            Helpers.assertConnection(connections.getFirst(), STOP_A, STOP_G, EIGHT_AM, 0, 0, 1);
            // check departure at 8:00
            Connection connection = connections.getFirst();
            assertEquals(EIGHT_AM, connection.getDepartureTime());
            // check arrival time at 8:23
            assertEquals(EIGHT_AM + 23 * 60, connection.getArrivalTime());
            // check that R1X(-F for forward) route was used
            assertEquals("R1X-F", connection.getRouteLegs().getFirst().getRouteId());
        }

        @Test
        void takeSlowerRouteOfOverlappingRoutesDueToEarlierDepartureTime(RaptorRouterTestBuilder builder) {
            // Create Two Versions of the same route with different travel speeds and different departure times
            RaptorAlgorithm raptor = builder.withAddRoute1_AG()
                    .withAddRoute1_AG("R1X", 15, 30, 3, RaptorRouterTestBuilder.DEFAULT_DWELL_TIME)
                    .build();
            List<Connection> connections = ConvenienceMethods.routeEarliestArrival(raptor, STOP_A, STOP_G, EIGHT_AM);

            // Route R1 leaves at 8:00 at Stop A and arrives at G at 8:35 whereas R1X leaves at 8:15 from Stop A and
            // arrives at G at 8:38. R1 should be used.
            assertEquals(1, connections.size());
            Helpers.assertConnection(connections.getFirst(), STOP_A, STOP_G, EIGHT_AM, 0, 0, 1);
            // check departure at 8:00
            Connection connection = connections.getFirst();
            assertEquals(EIGHT_AM, connection.getDepartureTime());
            // check arrival time at 8:35
            assertEquals(EIGHT_AM + 35 * 60, connection.getArrivalTime());
            // check that R1(-F for forward) route was used
            assertEquals("R1-F", connection.getRouteLegs().getFirst().getRouteId());
        }

        private static class Helpers {

            private static void assertConnection(Connection connection, String sourceStop, String targetStop,
                                                 int departureTime, int numSameStopTransfers, int numWalkTransfers,
                                                 int numTrips) {
                assertEquals(sourceStop, connection.getFromStopId());
                assertEquals(targetStop, connection.getToStopId());
                assertTrue(connection.getDepartureTime() >= departureTime,
                        "Departure time should be greater equal than searched for departure time");

                assertEquals(numSameStopTransfers, connection.getNumberOfSameStopTransfers(),
                        "Number of same stop transfers should match");
                assertEquals(numWalkTransfers, connection.getWalkTransfers().size(),
                        "Number of walk transfers should match");
                assertEquals(numSameStopTransfers + numWalkTransfers, connection.getNumberOfTotalTransfers(),
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
    class LatestDeparture {

        @Test
        void findConnectionsBetweenIntersectingRoutes(RaptorRouterTestBuilder builder) {
            // Should return two pareto optimal connections:
            // 1. Connection (with two route legs and one transfer (including footpath) --> slower but fewer transfers)
            //  - Route R1-F from A to D
            //  - Foot Transfer from D to N
            //  - Route R3-F from N to Q

            // 2. Connection (with three route legs and two transfers (same station) --> faster but more transfers)
            //  - Route R1-F from A to F
            //  - Route R4-R from F to P
            //  - Route R3-F from P to Q
            RaptorAlgorithm raptor = builder.buildWithDefaults();

            List<Connection> connections = ConvenienceMethods.routeLatestDeparture(raptor, STOP_A, STOP_Q, NINE_AM);

            // check if 2 connections were found
            assertEquals(2, connections.size());
            Helpers.assertConnection(connections.getFirst(), STOP_A, STOP_Q, NINE_AM, 0, 1, 2);
            Helpers.assertConnection(connections.get(1), STOP_A, STOP_Q, NINE_AM, 2, 0, 3);
            EarliestArrival.Helpers.checkIfConnectionsAreParetoOptimal(connections);
        }

        @Test
        void routeBetweenTwoStopsOnSameRoute(RaptorRouterTestBuilder builder) {
            RaptorAlgorithm raptor = builder.buildWithDefaults();

            List<Connection> connections = ConvenienceMethods.routeLatestDeparture(raptor, STOP_A, STOP_B, NINE_AM);
            assertEquals(1, connections.size());
            Helpers.assertConnection(connections.getFirst(), STOP_A, STOP_B, NINE_AM, 0, 0, 1);
        }

        @Test
        void routeWithSelfIntersectingRoute(RaptorRouterTestBuilder builder) {
            builder.withAddRoute5_AH_selfIntersecting();
            RaptorAlgorithm raptor = builder.build();

            List<Connection> connections = ConvenienceMethods.routeLatestDeparture(raptor, STOP_A, STOP_H, NINE_AM);
            assertEquals(2, connections.size());

            // First Connection Should have no transfers but ride the entire loop (slow)
            Helpers.assertConnection(connections.getFirst(), STOP_A, STOP_H, NINE_AM, 0, 0, 1);
            // Second Connection Should Change at Stop B and take the earlier trip of the same route there (faster)
            Helpers.assertConnection(connections.get(1), STOP_A, STOP_H, NINE_AM, 1, 0, 2);

            EarliestArrival.Helpers.checkIfConnectionsAreParetoOptimal(connections);
        }

        @Test
        void routeToTwoSourceStopsWitNoWalkTime(RaptorRouterTestBuilder builder) {
            RaptorAlgorithm raptor = builder.buildWithDefaults();

            Map<String, Integer> sourceStops = Map.of(STOP_A, 0, STOP_B, 0);
            Map<String, Integer> targetStops = Map.of(STOP_H, NINE_AM);

            // fastest and only connection should be B -> H
            List<Connection> connections = ConvenienceMethods.routeLatestDeparture(raptor, sourceStops, targetStops);
            assertEquals(1, connections.size());
            Helpers.assertConnection(connections.getFirst(), STOP_B, STOP_H, NINE_AM, 0, 0, 1);
        }

        @Test
        void routeToTwoSourceStopsWithWalkTimeOnCloserStop(RaptorRouterTestBuilder builder) {
            RaptorAlgorithm raptor = builder.buildWithDefaults();

            Map<String, Integer> sourceStops = Map.of(STOP_A, 0, STOP_B, RaptorRouterTestBuilder.SECONDS_IN_HOUR);
            Map<String, Integer> targetStops = Map.of(STOP_H, NINE_AM);

            // B -> H has no transfers but (theoretical) worse departure time (due to extra one-hour walk time)
            // A -> H has one transfer but (theoretical) better departure time (no additional walk time
            List<Connection> connections = ConvenienceMethods.routeLatestDeparture(raptor, sourceStops, targetStops);
            assertEquals(2, connections.size());
            Helpers.assertConnection(connections.getFirst(), STOP_B, STOP_H, NINE_AM, 0, 0, 1);
            Helpers.assertConnection(connections.get(1), STOP_A, STOP_H, NINE_AM, 1, 0, 2);
        }

        @Test
        void routeFromTwoTargetStopsToTargetNoWalkTime(RaptorRouterTestBuilder builder) {
            RaptorAlgorithm raptor = builder.buildWithDefaults();

            Map<String, Integer> sourceStops = Map.of(STOP_A, 0);
            Map<String, Integer> targetStops = Map.of(STOP_F, NINE_AM, STOP_S, NINE_AM);

            // fastest and only connection should be A -> F
            List<Connection> connections = ConvenienceMethods.routeLatestDeparture(raptor, sourceStops, targetStops);
            assertEquals(1, connections.size());
            Helpers.assertConnection(connections.getFirst(), STOP_A, STOP_F, NINE_AM, 0, 0, 1);
        }

        @Test
        void routeFromToTargetStopsWithDifferentArrivalTimes(RaptorRouterTestBuilder builder) {
            RaptorAlgorithm raptor = builder.buildWithDefaults();

            Map<String, Integer> sourceStops = Map.of(STOP_A, 0);
            // Add one-hour walk time to target from stop F and no extra walk time from stop S
            Map<String, Integer> targetStops = Map.of(STOP_F, EIGHT_AM, STOP_S, NINE_AM);

            // since F is closer to A than S, the fastest connection should be A -> F, but because of the hour
            // earlier arrival time, the connection A -> S should be faster (no additional walk time)
            List<Connection> connections = ConvenienceMethods.routeLatestDeparture(raptor, sourceStops, targetStops);
            assertEquals(2, connections.size());
            Helpers.assertConnection(connections.getFirst(), STOP_A, STOP_F, EIGHT_AM, 0, 0, 1);
            Helpers.assertConnection(connections.get(1), STOP_A, STOP_S, NINE_AM, 1, 0, 2);
        }

        @Test
        void notFindConnectionBetweenNotLinkedStops(RaptorRouterTestBuilder builder) {
            // Omit route R2/R4 and transfers to make stop Q (on R3) unreachable from A (on R1)
            RaptorAlgorithm raptor = builder.withAddRoute1_AG().withAddRoute3_MQ().build();

            List<Connection> connections = ConvenienceMethods.routeLatestDeparture(raptor, STOP_A, STOP_Q, NINE_AM);
            assertTrue(connections.isEmpty(), "No connection should be found");
        }

        @Test
        void findConnectionBetweenOnlyFootpath(RaptorRouterTestBuilder builder) {
            RaptorAlgorithm raptor = builder.withAddRoute1_AG()
                    .withAddRoute2_HL()
                    .withAddRoute3_MQ()
                    .withAddRoute4_RS()
                    .withAddTransfer1_ND(1)
                    .withAddTransfer2_LR()
                    .build();

            List<Connection> connections = ConvenienceMethods.routeLatestDeparture(raptor, STOP_N, STOP_D, NINE_AM);
            assertEquals(1, connections.size());
            Helpers.assertConnection(connections.getFirst(), STOP_N, STOP_D, NINE_AM, 0, 1, 0);
        }

        @Test
        void takeFasterRouteOfOverlappingRoutes(RaptorRouterTestBuilder builder) {
            // Create Two Versions of the same route with different travel speeds (both leaving at same time from A)
            RaptorAlgorithm raptor = builder.withAddRoute1_AG()
                    .withAddRoute1_AG("R1X", 12, RaptorRouterTestBuilder.DEFAULT_HEADWAY_TIME, 3,
                            RaptorRouterTestBuilder.DEFAULT_DWELL_TIME)
                    .build();

            // Both Routes arrive at 8:35 at Stop G, but R1 leaves A at 8:00 whereas R1X leaves at A at 8:12
            // R1X should be taken
            int arrivalTime = EIGHT_AM + 35 * 60;
            List<Connection> connections = ConvenienceMethods.routeLatestDeparture(raptor, STOP_A, STOP_G, arrivalTime);

            assertEquals(1, connections.size());
            Helpers.assertConnection(connections.getFirst(), STOP_A, STOP_G, arrivalTime, 0, 0, 1);
            // check departure at 8:12
            Connection connection = connections.getFirst();
            assertEquals(EIGHT_AM + 12 * 60, connection.getDepartureTime());
            // check arrival time at 8:23
            assertEquals(arrivalTime, connection.getArrivalTime());
            // check that R1X(-F for forward) route was used
            assertEquals("R1X-F", connection.getRouteLegs().getFirst().getRouteId());
        }

        @Test
        void takeSlowerRouteOfOverlappingRoutesDueToLaterDepartureTime(RaptorRouterTestBuilder builder) {
            // Create Two Versions of the same route with different travel speeds and different departure times
            RaptorAlgorithm raptor = builder.withAddRoute1_AG()
                    .withAddRoute1_AG("R1X", 15, 30, 3, RaptorRouterTestBuilder.DEFAULT_DWELL_TIME)
                    .build();
            List<Connection> connections = ConvenienceMethods.routeEarliestArrival(raptor, STOP_A, STOP_G, EIGHT_AM);

            // Route R1 leaves at 8:00 at Stop A and arrives at G at 8:35 whereas R1X leaves at 7:45 from Stop A and
            // arrives at G at 8:08. R1 should be used.
            int arrivalTime = EIGHT_AM + 35 * 60;
            assertEquals(1, connections.size());
            Helpers.assertConnection(connections.getFirst(), STOP_A, STOP_G, arrivalTime, 0, 0, 1);
            // check departure at 8:00
            Connection connection = connections.getFirst();
            assertEquals(EIGHT_AM, connection.getDepartureTime());
            // check arrival time at 8:35
            assertEquals(arrivalTime, connection.getArrivalTime());
            // check that R1(-F for forward) route was used
            assertEquals("R1-F", connection.getRouteLegs().getFirst().getRouteId());
        }

        private static class Helpers {

            private static void assertConnection(Connection connection, String sourceStop, String targetStop,
                                                 int arrivalTime, int numSameStopTransfers, int numWalkTransfers,
                                                 int numTrips) {
                assertEquals(sourceStop, connection.getFromStopId());
                assertEquals(targetStop, connection.getToStopId());
                assertTrue(connection.getArrivalTime() <= arrivalTime,
                        "Arrival time should be smaller equal than searched for arrival time");

                assertEquals(numSameStopTransfers, connection.getNumberOfSameStopTransfers(),
                        "Number of same station transfers should match");
                assertEquals(numWalkTransfers, connection.getWalkTransfers().size(),
                        "Number of walk transfers should match");
                assertEquals(numSameStopTransfers + numWalkTransfers, connection.getNumberOfTotalTransfers(),
                        "Number of transfers should match");

                assertEquals(numTrips, connection.getRouteLegs().size(), "Number of trips should match");
            }
        }
    }

    @Nested
    class SameStopTransfers {

        @Test
        void takeFirstTripWithoutAddingSameStopTransferTimeAtFirstStop(RaptorRouterTestBuilder builder) {
            RaptorAlgorithm raptor = builder.withAddRoute1_AG()
                    .withAddRoute2_HL()
                    .withSameStopTransferTime(120)
                    .build();
            // There should be a connection leaving stop A at 5:00 am and this test should ensure that the same stop
            // transfer time is not added at the first stop, i.e. departure time at 5:00 am should allow to board the
            // first trip at 5:00 am
            List<Connection> connections = ConvenienceMethods.routeEarliestArrival(raptor, STOP_A, STOP_H, FIVE_AM);
            assertEquals(1, connections.size());
            assertEquals(FIVE_AM, connections.getFirst().getDepartureTime());
        }

        @Test
        void missConnectingTripBecauseOfSameStopTransferTime(RaptorRouterTestBuilder builder) {
            RaptorAlgorithm raptor = builder.withAddRoute1_AG(19, RaptorRouterTestBuilder.DEFAULT_HEADWAY_TIME,
                            RaptorRouterTestBuilder.DEFAULT_TIME_BETWEEN_STOPS, RaptorRouterTestBuilder.DEFAULT_DWELL_TIME)
                    .withAddRoute2_HL()
                    .withSameStopTransferTime(120)
                    .build();
            // There should be a connection leaving stop A at 5:19 am and arriving at stop B at 5:24 am
            // Connection at 5:24 from B to H should be missed because of the same stop transfer time (120s)
            List<Connection> connections = ConvenienceMethods.routeEarliestArrival(raptor, STOP_A, STOP_H, FIVE_AM);

            assertEquals(1, connections.size());
            assertEquals(FIVE_AM + 19 * 60, connections.getFirst().getDepartureTime());

            assertNotEquals(FIVE_AM + 24 * 60, connections.getFirst().getLegs().get(1).getDepartureTime());
        }

        @Test
        void catchConnectingTripBecauseOfNoSameStopTransferTime(RaptorRouterTestBuilder builder) {
            RaptorAlgorithm raptor = builder.withAddRoute1_AG(19, RaptorRouterTestBuilder.DEFAULT_HEADWAY_TIME,
                            RaptorRouterTestBuilder.DEFAULT_TIME_BETWEEN_STOPS, RaptorRouterTestBuilder.DEFAULT_DWELL_TIME)
                    .withAddRoute2_HL()
                    .withSameStopTransferTime(0)
                    .build();
            // There should be a connection leaving stop A at 5:19 am and arriving at stop B at 5:24 am
            // Connection at 5:24 from B to H should not be missed because of no same stop transfer time
            List<Connection> connections = ConvenienceMethods.routeEarliestArrival(raptor, STOP_A, STOP_H, FIVE_AM);

            assertEquals(1, connections.size());
            assertEquals(FIVE_AM + 19 * 60, connections.getFirst().getDepartureTime());
            assertEquals(FIVE_AM + 24 * 60, connections.getFirst().getLegs().get(1).getDepartureTime());
        }

        @Test
        void catchConnectingTripWithSameStopTransferTime(RaptorRouterTestBuilder builder) {
            RaptorAlgorithm raptor = builder.withAddRoute1_AG(17, RaptorRouterTestBuilder.DEFAULT_HEADWAY_TIME,
                            RaptorRouterTestBuilder.DEFAULT_TIME_BETWEEN_STOPS, RaptorRouterTestBuilder.DEFAULT_DWELL_TIME)
                    .withAddRoute2_HL()
                    .withSameStopTransferTime(120)
                    .build();
            // There should be a connection leaving stop A at 5:17 am and arriving at stop B at 5:22 am
            // Connection at 5:24 from B to H should be cached when the same stop transfer time is 120s
            List<Connection> connections = ConvenienceMethods.routeEarliestArrival(raptor, STOP_A, STOP_H, FIVE_AM);

            assertEquals(1, connections.size());
            assertEquals(FIVE_AM + 17 * 60, connections.getFirst().getDepartureTime());

            assertEquals(FIVE_AM + 24 * 60, connections.getFirst().getLegs().get(1).getDepartureTime());
        }

    }

    /*
     * Tests for the application of the QueryConfig class.
     * Note: Since the QueryConfig is used in SpawnFromSourceStop no additional tests for the IsoLines QueryConfig are
     * needed.
     */
    @Nested
    class QueryConfiguration {

        @Test
        void findWalkableTransferWithMaxWalkingTime(RaptorRouterTestBuilder builder) {
            RaptorAlgorithm raptor = builder.buildWithDefaults();
            QueryConfig queryConfig = new QueryConfig();
            queryConfig.setMaximumWalkingDuration(RaptorRouterTestBuilder.SECONDS_IN_HOUR);

            // Should return two pareto optimal connections:
            // 1. Connection (with two route legs and one transfer (including footpath) --> slower but fewer transfers)
            //  - Route R1-F from A to D
            //  - Foot Transfer from D to N (30 minutes walk time
            //  - Route R3-F from N to Q

            // 2. Connection (with three route legs and two transfers (same stop) --> faster but more transfers)
            //  - Route R1-F from A to F
            //  - Route R4-R from F to P
            //  - Route R3-F from P to Q
            List<Connection> connections = ConvenienceMethods.routeEarliestArrival(raptor, STOP_A, STOP_Q, EIGHT_AM,
                    queryConfig);

            // check if 2 connections were found
            assertEquals(2, connections.size());
            EarliestArrival.Helpers.assertConnection(connections.getFirst(), STOP_A, STOP_Q, EIGHT_AM, 0, 1, 2);
            EarliestArrival.Helpers.assertConnection(connections.get(1), STOP_A, STOP_Q, EIGHT_AM, 2, 0, 3);
            EarliestArrival.Helpers.checkIfConnectionsAreParetoOptimal(connections);
        }

        @Test
        void notFindWalkableTransferWithMaxWalkingTime(RaptorRouterTestBuilder builder) {
            RaptorAlgorithm raptor = builder.buildWithDefaults();
            QueryConfig queryConfig = new QueryConfig();
            queryConfig.setMaximumWalkingDuration(RaptorRouterTestBuilder.SECONDS_IN_HOUR / 4); // 15 minutes

            // Should only find three route leg connections, since direct transfer between D and N is longer than
            // allowed maximum walking distance (60 minutes):
            List<Connection> connections = ConvenienceMethods.routeEarliestArrival(raptor, STOP_A, STOP_Q, EIGHT_AM,
                    queryConfig);
            assertEquals(1, connections.size());
            EarliestArrival.Helpers.assertConnection(connections.getFirst(), STOP_A, STOP_Q, EIGHT_AM, 2, 0, 3);
        }

        @Test
        void findConnectionWithMaxTransferNumber(RaptorRouterTestBuilder builder) {
            RaptorAlgorithm raptor = builder.buildWithDefaults();
            QueryConfig queryConfig = new QueryConfig();
            queryConfig.setMaximumTransferNumber(1);

            // Should only find the connection with the fewest transfers:
            // 1. Connection (with two route legs and one transfer (including footpath) --> slower but fewer transfers)
            //  - Route R1-F from A to D
            //  - Foot Transfer from D to N
            //  - Route R3-F from N to Q
            // 2. Connection with two transfers (see above) should not be found
            List<Connection> connections = ConvenienceMethods.routeEarliestArrival(raptor, STOP_A, STOP_Q, EIGHT_AM,
                    queryConfig);
            assertEquals(1, connections.size());
            EarliestArrival.Helpers.assertConnection(connections.getFirst(), STOP_A, STOP_Q, EIGHT_AM, 0, 1, 2);
        }

        @Test
        void findConnectionWithMaxTravelTime(RaptorRouterTestBuilder builder) {
            RaptorAlgorithm raptor = builder.buildWithDefaults();
            QueryConfig queryConfig = new QueryConfig();
            queryConfig.setMaximumTravelTime(RaptorRouterTestBuilder.SECONDS_IN_HOUR);

            // Should only find the quicker connection (more transfers):
            //  - Route R1-F from A to F
            //  - Route R4-R from F to P
            //  - Route R3-F from P to Q
            List<Connection> connections = ConvenienceMethods.routeEarliestArrival(raptor, STOP_A, STOP_Q, EIGHT_AM,
                    queryConfig);
            assertEquals(1, connections.size());
            EarliestArrival.Helpers.assertConnection(connections.getFirst(), STOP_A, STOP_Q, EIGHT_AM, 2, 0, 3);
        }

        @Test
        void useSameStopTransferTimeWithZeroMinimumTransferDuration(RaptorRouterTestBuilder builder) {
            QueryConfig queryConfig = new QueryConfig();
            queryConfig.setMinimumTransferDuration(0);

            RaptorAlgorithm raptor = builder.withAddRoute1_AG(19, 15, 5, 1)
                    .withAddRoute2_HL()
                    .withSameStopTransferTime(120)
                    .build();
            // There should be a connection leaving stop A at 5:19 am and arriving at stop B at 5:24 am. Connection
            // at 5:24 (next 5:39) from B to C should be missed because of the same stop transfer time (120s),
            // regardless of minimum same transfer duration at 0s
            List<Connection> connections = ConvenienceMethods.routeEarliestArrival(raptor, STOP_A, STOP_H, FIVE_AM,
                    queryConfig);

            assertEquals(1, connections.size());
            assertEquals(FIVE_AM + 19 * 60, connections.getFirst().getDepartureTime());
            assertEquals(FIVE_AM + 39 * 60, connections.getFirst().getLegs().get(1).getDepartureTime());
        }

        @Test
        void useMinimumTransferTime(RaptorRouterTestBuilder builder) {
            QueryConfig queryConfig = new QueryConfig();
            queryConfig.setMinimumTransferDuration(20 * 60); // 20 minutes

            RaptorAlgorithm raptor = builder.withAddRoute1_AG(19, 15, 5, 1)
                    .withAddRoute2_HL()
                    .withSameStopTransferTime(120)
                    .build();
            // There should be a connection leaving stop A at 5:19 am and arriving at stop B at 5:24 am. Connection
            // at 5:24 and 5:39 from B to C should be missed because of the minimum transfer duration (20 minutes)
            List<Connection> connections = ConvenienceMethods.routeEarliestArrival(raptor, STOP_A, STOP_H, FIVE_AM,
                    queryConfig);

            assertEquals(1, connections.size());
            assertEquals(FIVE_AM + 19 * 60, connections.getFirst().getDepartureTime());
            assertEquals(FIVE_AM + 54 * 60, connections.getFirst().getLegs().get(1).getDepartureTime());
        }

        @Test
        void addMinimumTransferTimeToWalkTransferDuration(RaptorRouterTestBuilder builder) {
            QueryConfig queryConfig = new QueryConfig();
            queryConfig.setMinimumTransferDuration(20 * 60); // 20 minutes

            RaptorAlgorithm raptor = builder.buildWithDefaults();
            List<Connection> connections = ConvenienceMethods.routeEarliestArrival(raptor, STOP_A, STOP_Q, EIGHT_AM,
                    queryConfig);

            assertEquals(2, connections.size());
            Connection firstConnection = connections.getFirst();
            EarliestArrival.Helpers.assertConnection(firstConnection, STOP_A, STOP_Q, EIGHT_AM, 0, 1, 2);

            // The walk transfer from D to N takes 60 minutes and the route from N to Q leaves every 75 minutes.
            Leg firstLeg = firstConnection.getRouteLegs().getFirst();
            Leg secondLeg = firstConnection.getRouteLegs().getLast();
            int timeDiff = secondLeg.getDepartureTime() - firstLeg.getArrivalTime();
            assertTrue(timeDiff >= 75 * 60, "Time between trips should be at least 75 minutes");
        }

    }

    @Nested
    class IsoLines {

        @Test
        void createIsoLinesToAllStops(RaptorRouterTestBuilder builder) {
            RaptorAlgorithm raptor = builder.buildWithDefaults();
            Map<String, Connection> isoLines = ConvenienceMethods.getIsoLines(raptor, Map.of(STOP_A, EIGHT_AM));

            int stopsInSystem = 19;
            int expectedIsoLines = stopsInSystem - 1;
            Helpers.assertIsoLines(isoLines, expectedIsoLines);
        }

        @Test
        void createIsoLinesToSomeStopsNotAllConnected(RaptorRouterTestBuilder builder) {
            // Route 1 and 3 are not connected, thus all Stops of Route 3 should not be reachable from A
            RaptorAlgorithm raptor = builder.withAddRoute1_AG().withAddRoute3_MQ().build();
            Map<String, Connection> isoLines = ConvenienceMethods.getIsoLines(raptor, Map.of(STOP_A, EIGHT_AM));

            List<String> reachableStops = List.of(STOP_B, STOP_C, STOP_D, STOP_E, STOP_F, STOP_G);
            // Not Reachable Stops: M, K, N, O, P, Q

            Helpers.assertIsoLines(isoLines, reachableStops.size());

            for (String stop : reachableStops) {
                assertTrue(isoLines.containsKey(stop), "Stop " + stop + " should be reachable");
            }
        }

        @Test
        void createIsoLinesToStopsOfOtherLineOnlyConnectedByFootpath(RaptorRouterTestBuilder builder) {
            // Route 1 and Route 3 are only connected by Footpath between Stops D and N
            RaptorAlgorithm raptor = builder.withAddRoute1_AG().withAddRoute3_MQ().withAddTransfer1_ND().build();
            Map<String, Connection> isoLines = ConvenienceMethods.getIsoLines(raptor, Map.of(STOP_A, EIGHT_AM));

            List<String> reachableStops = List.of(STOP_B, STOP_C, STOP_D, STOP_E, STOP_F, STOP_G, STOP_M, STOP_K,
                    STOP_N, STOP_O, STOP_P, STOP_Q);

            Helpers.assertIsoLines(isoLines, reachableStops.size());

            for (String stop : reachableStops) {
                assertTrue(isoLines.containsKey(stop), "Stop " + stop + " should be reachable");
            }
        }

        @Test
        void createIsoLinesFromTwoNotConnectedSourceStops(RaptorRouterTestBuilder builder) {
            RaptorAlgorithm raptor = builder.withAddRoute1_AG().withAddRoute3_MQ().build();

            Map<String, Integer> departureTimeHours = Map.of(STOP_A, 8, STOP_M, 16);

            List<String> reachableStopsFromStopA = List.of(STOP_B, STOP_C, STOP_D, STOP_E, STOP_F, STOP_G);
            Map<String, Integer> sourceStops = Map.of(STOP_A,
                    departureTimeHours.get(STOP_A) * RaptorRouterTestBuilder.SECONDS_IN_HOUR, STOP_M,
                    departureTimeHours.get(STOP_M) * RaptorRouterTestBuilder.SECONDS_IN_HOUR);
            List<String> reachableStopsFromStopM = List.of(STOP_K, STOP_N, STOP_O, STOP_P, STOP_Q);

            Map<String, Connection> isoLines = ConvenienceMethods.getIsoLines(raptor, sourceStops);

            assertEquals(reachableStopsFromStopA.size() + reachableStopsFromStopM.size(), isoLines.size());

            Map<String, List<String>> sourceTargets = Map.of(STOP_A, reachableStopsFromStopA, STOP_M,
                    reachableStopsFromStopM);

            for (Map.Entry<String, List<String>> entry : sourceTargets.entrySet()) {
                String sourceStop = entry.getKey();
                List<String> reachableStops = entry.getValue();
                int departureTimeHour = departureTimeHours.get(sourceStop);
                int departureTime = departureTimeHour * RaptorRouterTestBuilder.SECONDS_IN_HOUR;
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
            private static void assertIsoLines(Map<String, Connection> isoLines, int expectedIsoLines) {
                assertEquals(expectedIsoLines, isoLines.size());
                assertFalse(isoLines.containsKey(STOP_A), "Source stop should not be in iso lines");
                for (Map.Entry<String, Connection> entry : isoLines.entrySet()) {
                    assertTrue(EIGHT_AM <= entry.getValue().getDepartureTime(),
                            "Departure time should be greater than or equal to departure time");
                    assertTrue(EIGHT_AM < entry.getValue().getArrivalTime(),
                            "Arrival time should be greater than or equal to departure time");
                    assertTrue(entry.getValue().getArrivalTime() < INFINITY,
                            "Arrival time should be less than INFINITY");
                    assertEquals(STOP_A, entry.getValue().getFromStopId(), "From stop should be source stop");
                    assertEquals(entry.getKey(), entry.getValue().getToStopId(), "To stop should be key of map entry");
                }
            }

        }

    }

    @Nested
    class InputValidation {

        private RaptorAlgorithm raptor;

        @BeforeEach
        void setUp(RaptorRouterTestBuilder builder) {
            raptor = builder.buildWithDefaults();
        }

        @Test
        void throwErrorWhenSourceStopNotExists() {
            String sourceStop = "NonExistentStop";
            assertThrows(IllegalArgumentException.class,
                    () -> ConvenienceMethods.routeEarliestArrival(raptor, sourceStop, STOP_A, EIGHT_AM),
                    "Source stop has to exists");
        }

        @Test
        void throwErrorWhenTargetStopNotExists() {
            String targetStop = "NonExistentStop";
            assertThrows(IllegalArgumentException.class,
                    () -> ConvenienceMethods.routeEarliestArrival(raptor, STOP_A, targetStop, EIGHT_AM),
                    "Target stop has to exists");
        }

        @Test
        void notThrowErrorForValidAndNonExistingSourceStop() {
            Map<String, Integer> sourceStops = Map.of(STOP_A, EIGHT_AM, "NonExistentStop", EIGHT_AM);
            Map<String, Integer> targetStops = Map.of(STOP_H, 0);

            assertDoesNotThrow(() -> ConvenienceMethods.routeEarliestArrival(raptor, sourceStops, targetStops),
                    "Source stops can contain non-existing stops, if one entry is valid");
        }

        @Test
        void notThrowErrorForValidAndNonExistingTargetStop() {
            Map<String, Integer> sourceStops = Map.of(STOP_H, EIGHT_AM);
            Map<String, Integer> targetStops = Map.of(STOP_A, 0, "NonExistentStop", 0);

            assertDoesNotThrow(() -> ConvenienceMethods.routeEarliestArrival(raptor, sourceStops, targetStops),
                    "Target stops can contain non-existing stops, if one entry is valid");
        }

        @Test
        void throwErrorForInvalidWalkToTargetTimeFromOneOfManyTargetStops() {
            Map<String, Integer> sourceStops = Map.of(STOP_H, EIGHT_AM);
            Map<String, Integer> targetStops = Map.of(STOP_A, 0, STOP_B, -1);

            assertThrows(IllegalArgumentException.class,
                    () -> ConvenienceMethods.routeEarliestArrival(raptor, sourceStops, targetStops),
                    "Departure time has to be valid for all valid source stops");
        }

        @Test
        void throwErrorNullSourceStops() {
            Map<String, Integer> sourceStops = null;
            Map<String, Integer> targetStops = Map.of(STOP_H, 0);

            assertThrows(IllegalArgumentException.class,
                    () -> ConvenienceMethods.routeEarliestArrival(raptor, sourceStops, targetStops),
                    "Source stops cannot be null");
        }

        @Test
        void throwErrorNullTargetStops() {
            Map<String, Integer> sourceStops = Map.of(STOP_A, 0);
            Map<String, Integer> targetStops = null;

            assertThrows(IllegalArgumentException.class,
                    () -> ConvenienceMethods.routeEarliestArrival(raptor, sourceStops, targetStops),
                    "Target stops cannot be null");
        }

        @Test
        void throwErrorEmptyMapSourceStops() {
            Map<String, Integer> sourceStops = Map.of();
            Map<String, Integer> targetStops = Map.of(STOP_H, 0);

            assertThrows(IllegalArgumentException.class,
                    () -> ConvenienceMethods.routeEarliestArrival(raptor, sourceStops, targetStops),
                    "Source and target stops cannot be null");
        }

        @Test
        void throwErrorEmptyMapTargetStops() {
            Map<String, Integer> sourceStops = Map.of(STOP_A, 0);
            Map<String, Integer> targetStops = Map.of();

            assertThrows(IllegalArgumentException.class,
                    () -> ConvenienceMethods.routeEarliestArrival(raptor, sourceStops, targetStops),
                    "Source and target stops cannot be null");
        }

        @Test
        void throwErrorWhenRequestBetweenSameStop() {
            assertThrows(IllegalArgumentException.class,
                    () -> ConvenienceMethods.routeEarliestArrival(raptor, STOP_A, STOP_A, EIGHT_AM),
                    "Stops cannot be the same");
        }

    }

}