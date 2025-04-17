package org.naviqore.raptor.router;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.naviqore.raptor.Connection;
import org.naviqore.raptor.Leg;
import org.naviqore.raptor.QueryConfig;
import org.naviqore.raptor.RaptorAlgorithm;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the Raptor class.
 */
@ExtendWith(RaptorRouterTestExtension.class)
class RaptorRouterTest {

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

    private static final LocalDateTime START_OF_DAY = LocalDateTime.of(2021, 1, 1, 0, 0);
    private static final LocalDateTime FIVE_AM = START_OF_DAY.plusHours(5);
    private static final LocalDateTime EIGHT_AM = START_OF_DAY.plusHours(8);
    private static final LocalDateTime NINE_AM = START_OF_DAY.plusHours(9);

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

            List<Connection> connections = RaptorRouterTestHelpers.routeEarliestArrival(raptor, STOP_A, STOP_Q,
                    EIGHT_AM);

            // check if 2 connections were found
            assertEquals(2, connections.size());
            RaptorRouterTestHelpers.assertEarliestArrivalConnection(connections.getFirst(), STOP_A, STOP_Q, EIGHT_AM, 0,
                    1, 2, raptor);
            RaptorRouterTestHelpers.assertEarliestArrivalConnection(connections.get(1), STOP_A, STOP_Q, EIGHT_AM, 2, 0,
                    3, raptor);
            RaptorRouterTestHelpers.checkIfConnectionsAreParetoOptimal(connections);
        }

        @Test
        void routeBetweenTwoStopsOnSameRoute(RaptorRouterTestBuilder builder) {
            RaptorAlgorithm raptor = builder.buildWithDefaults();

            List<Connection> connections = RaptorRouterTestHelpers.routeEarliestArrival(raptor, STOP_A, STOP_B,
                    EIGHT_AM);
            assertEquals(1, connections.size());
            RaptorRouterTestHelpers.assertEarliestArrivalConnection(connections.getFirst(), STOP_A, STOP_B, EIGHT_AM, 0,
                    0, 1, raptor);
        }

        @Test
        void routeWithSelfIntersectingRoute(RaptorRouterTestBuilder builder) {
            builder.withAddRoute5_AH_selfIntersecting();
            RaptorAlgorithm raptor = builder.build();

            List<Connection> connections = RaptorRouterTestHelpers.routeEarliestArrival(raptor, STOP_A, STOP_H,
                    EIGHT_AM);
            assertEquals(2, connections.size());

            // First Connection Should have no transfers but ride the entire loop (slow)
            RaptorRouterTestHelpers.assertEarliestArrivalConnection(connections.getFirst(), STOP_A, STOP_H, EIGHT_AM, 0,
                    0, 1, raptor);
            // Second Connection Should Change at Stop B and take the earlier trip of the same route there (faster)
            RaptorRouterTestHelpers.assertEarliestArrivalConnection(connections.get(1), STOP_A, STOP_H, EIGHT_AM, 1, 0,
                    2, raptor);

            RaptorRouterTestHelpers.checkIfConnectionsAreParetoOptimal(connections);
        }

        @Test
        void routeFromTwoSourceStopsWithSameDepartureTime(RaptorRouterTestBuilder builder) {
            RaptorAlgorithm raptor = builder.buildWithDefaults();

            Map<String, LocalDateTime> sourceStops = Map.of(STOP_A, EIGHT_AM, STOP_B, EIGHT_AM);
            Map<String, Integer> targetStops = Map.of(STOP_H, 0);

            // fastest and only connection should be B -> H
            List<Connection> connections = RaptorRouterTestHelpers.routeEarliestArrival(raptor, sourceStops,
                    targetStops);
            assertEquals(1, connections.size());
            RaptorRouterTestHelpers.assertEarliestArrivalConnection(connections.getFirst(), STOP_B, STOP_H, EIGHT_AM, 0,
                    0, 1, raptor);
        }

        @Test
        void routeFromTwoSourceStopsWithLaterDepartureTimeOnCloserStop(RaptorRouterTestBuilder builder) {
            RaptorAlgorithm raptor = builder.buildWithDefaults();

            Map<String, LocalDateTime> sourceStops = Map.of(STOP_A, EIGHT_AM, STOP_B, NINE_AM);
            Map<String, Integer> targetStops = Map.of(STOP_H, 0);

            // B -> H has no transfers but later arrival time (due to departure time one hour later)
            // A -> H has one transfer but earlier arrival time
            List<Connection> connections = RaptorRouterTestHelpers.routeEarliestArrival(raptor, sourceStops,
                    targetStops);
            assertEquals(2, connections.size());
            RaptorRouterTestHelpers.assertEarliestArrivalConnection(connections.getFirst(), STOP_B, STOP_H, NINE_AM, 0,
                    0, 1, raptor);
            RaptorRouterTestHelpers.assertEarliestArrivalConnection(connections.get(1), STOP_A, STOP_H, EIGHT_AM, 1, 0,
                    2, raptor);
            assertTrue(connections.getFirst().getArrivalTime().isAfter(connections.get(1).getArrivalTime()),
                    "Connection from A should arrive earlier than connection from B");
        }

        @Test
        void routeFromStopToTwoTargetStopsNoWalkTimeToTarget(RaptorRouterTestBuilder builder) {
            RaptorAlgorithm raptor = builder.buildWithDefaults();

            Map<String, LocalDateTime> sourceStops = Map.of(STOP_A, EIGHT_AM);
            Map<String, Integer> targetStops = Map.of(STOP_F, 0, STOP_S, 0);

            // fastest and only connection should be A -> F
            List<Connection> connections = RaptorRouterTestHelpers.routeEarliestArrival(raptor, sourceStops,
                    targetStops);
            assertEquals(1, connections.size());
            RaptorRouterTestHelpers.assertEarliestArrivalConnection(connections.getFirst(), STOP_A, STOP_F, EIGHT_AM, 0,
                    0, 1, raptor);
        }

        @Test
        void routeFromStopToTwoTargetStopsWithWalkTimeToTarget(RaptorRouterTestBuilder builder) {
            RaptorAlgorithm raptor = builder.buildWithDefaults();

            Map<String, LocalDateTime> sourceStops = Map.of(STOP_A, EIGHT_AM);
            // Add one-hour walk time to target from stop F and no extra walk time from stop S
            Map<String, Integer> targetStops = Map.of(STOP_F, RaptorRouterTestBuilder.SECONDS_IN_HOUR, STOP_S, 0);

            // since F is closer to A than S, the fastest connection should be A -> F, but because of the hour
            // walk time to target, the connection A -> S should be faster (no additional walk time)
            List<Connection> connections = RaptorRouterTestHelpers.routeEarliestArrival(raptor, sourceStops,
                    targetStops);
            assertEquals(2, connections.size());
            RaptorRouterTestHelpers.assertEarliestArrivalConnection(connections.getFirst(), STOP_A, STOP_F, EIGHT_AM, 0,
                    0, 1, raptor);
            RaptorRouterTestHelpers.assertEarliestArrivalConnection(connections.get(1), STOP_A, STOP_S, EIGHT_AM, 1, 0,
                    2, raptor);

            // Note since the required walk time to target is not added as a leg, the solutions will not be pareto
            // optimal without additional post-processing.
        }

        @Test
        void notFindConnectionBetweenNotLinkedStops(RaptorRouterTestBuilder builder) {
            // Omit route R2/R4 and transfers to make stop Q (on R3) unreachable from A (on R1)
            RaptorAlgorithm raptor = builder.withAddRoute1_AG().withAddRoute3_MQ().build();

            List<Connection> connections = RaptorRouterTestHelpers.routeEarliestArrival(raptor, STOP_A, STOP_Q,
                    EIGHT_AM);
            assertTrue(connections.isEmpty(), "No connection should be found");
        }

        @Test
        void takeFasterRouteOfOverlappingRoutes(RaptorRouterTestBuilder builder) {
            // Create Two Versions of the same route with different travel speeds (both leaving at same time from A)
            RaptorAlgorithm raptor = builder.withAddRoute1_AG()
                    .withAddRoute1_AG("R1X", RaptorRouterTestBuilder.DEFAULT_OFFSET,
                            RaptorRouterTestBuilder.DEFAULT_HEADWAY_TIME, 3, RaptorRouterTestBuilder.DEFAULT_DWELL_TIME)
                    .build();
            List<Connection> connections = RaptorRouterTestHelpers.routeEarliestArrival(raptor, STOP_A, STOP_G,
                    EIGHT_AM);

            // Both Routes leave at 8:00 at Stop A, but R1 arrives at G at 8:35 whereas R1X arrives at G at 8:23
            // R1X should be taken
            assertEquals(1, connections.size());
            RaptorRouterTestHelpers.assertEarliestArrivalConnection(connections.getFirst(), STOP_A, STOP_G, EIGHT_AM, 0,
                    0, 1, raptor);
            // check departure at 8:00
            Connection connection = connections.getFirst();
            assertEquals(EIGHT_AM, connection.getDepartureTime());
            // check arrival time at 8:23
            assertEquals(EIGHT_AM.plusMinutes(23), connection.getArrivalTime());
            // check that R1X(-F for forward) route was used
            assertEquals("R1X-F", connection.getRouteLegs().getFirst().getRouteId());
        }

        @Test
        void takeSlowerRouteOfOverlappingRoutesDueToEarlierDepartureTime(RaptorRouterTestBuilder builder) {
            // Create Two Versions of the same route with different travel speeds and different departure times
            RaptorAlgorithm raptor = builder.withAddRoute1_AG()
                    .withAddRoute1_AG("R1X", 15, 30, 3, RaptorRouterTestBuilder.DEFAULT_DWELL_TIME)
                    .build();
            List<Connection> connections = RaptorRouterTestHelpers.routeEarliestArrival(raptor, STOP_A, STOP_G,
                    EIGHT_AM);

            // Route R1 leaves at 8:00 at Stop A and arrives at G at 8:35 whereas R1X leaves at 8:15 from Stop A and
            // arrives at G at 8:38. R1 should be used.
            assertEquals(1, connections.size());
            RaptorRouterTestHelpers.assertEarliestArrivalConnection(connections.getFirst(), STOP_A, STOP_G, EIGHT_AM, 0,
                    0, 1, raptor);
            // check departure at 8:00
            Connection connection = connections.getFirst();
            assertEquals(EIGHT_AM, connection.getDepartureTime());
            // check arrival time at 8:35
            assertEquals(EIGHT_AM.plusMinutes(35), connection.getArrivalTime());
            // check that R1(-F for forward) route was used
            assertEquals("R1-F", connection.getRouteLegs().getFirst().getRouteId());
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

            List<Connection> connections = RaptorRouterTestHelpers.routeLatestDeparture(raptor, STOP_A, STOP_Q,
                    NINE_AM);

            // check if 2 connections were found
            assertEquals(2, connections.size());
            RaptorRouterTestHelpers.assertLatestDepartureConnection(connections.getFirst(), STOP_A, STOP_Q, NINE_AM, 0,
                    1, 2, raptor);
            RaptorRouterTestHelpers.assertLatestDepartureConnection(connections.get(1), STOP_A, STOP_Q, NINE_AM, 2, 0,
                    3, raptor);
            RaptorRouterTestHelpers.checkIfConnectionsAreParetoOptimal(connections);
        }

        @Test
        void routeBetweenTwoStopsOnSameRoute(RaptorRouterTestBuilder builder) {
            RaptorAlgorithm raptor = builder.buildWithDefaults();

            List<Connection> connections = RaptorRouterTestHelpers.routeLatestDeparture(raptor, STOP_A, STOP_B,
                    NINE_AM);
            assertEquals(1, connections.size());
            RaptorRouterTestHelpers.assertLatestDepartureConnection(connections.getFirst(), STOP_A, STOP_B, NINE_AM, 0,
                    0, 1, raptor);
        }

        @Test
        void routeWithSelfIntersectingRoute(RaptorRouterTestBuilder builder) {
            builder.withAddRoute5_AH_selfIntersecting();
            RaptorAlgorithm raptor = builder.build();

            List<Connection> connections = RaptorRouterTestHelpers.routeLatestDeparture(raptor, STOP_A, STOP_H,
                    NINE_AM);
            assertEquals(2, connections.size());

            // First Connection Should have no transfers but ride the entire loop (slow)
            RaptorRouterTestHelpers.assertLatestDepartureConnection(connections.getFirst(), STOP_A, STOP_H, NINE_AM, 0,
                    0, 1, raptor);
            // Second Connection Should Change at Stop B and take the earlier trip of the same route there (faster)
            RaptorRouterTestHelpers.assertLatestDepartureConnection(connections.get(1), STOP_A, STOP_H, NINE_AM, 1, 0,
                    2, raptor);

            RaptorRouterTestHelpers.checkIfConnectionsAreParetoOptimal(connections);
        }

        @Test
        void routeToTwoSourceStopsWitNoWalkTime(RaptorRouterTestBuilder builder) {
            RaptorAlgorithm raptor = builder.buildWithDefaults();

            Map<String, Integer> sourceStops = Map.of(STOP_A, 0, STOP_B, 0);
            Map<String, LocalDateTime> targetStops = Map.of(STOP_H, NINE_AM);

            // fastest and only connection should be B -> H
            List<Connection> connections = RaptorRouterTestHelpers.routeLatestDeparture(raptor, sourceStops,
                    targetStops);
            assertEquals(1, connections.size());
            RaptorRouterTestHelpers.assertLatestDepartureConnection(connections.getFirst(), STOP_B, STOP_H, NINE_AM, 0,
                    0, 1, raptor);
        }

        @Test
        void routeToTwoSourceStopsWithWalkTimeOnCloserStop(RaptorRouterTestBuilder builder) {
            RaptorAlgorithm raptor = builder.buildWithDefaults();

            Map<String, Integer> sourceStops = Map.of(STOP_A, 0, STOP_B, RaptorRouterTestBuilder.SECONDS_IN_HOUR);
            Map<String, LocalDateTime> targetStops = Map.of(STOP_H, NINE_AM);

            // B -> H has no transfers but (theoretical) worse departure time (due to extra one-hour walk time)
            // A -> H has one transfer but (theoretical) better departure time (no additional walk time
            List<Connection> connections = RaptorRouterTestHelpers.routeLatestDeparture(raptor, sourceStops,
                    targetStops);
            assertEquals(2, connections.size());
            RaptorRouterTestHelpers.assertLatestDepartureConnection(connections.getFirst(), STOP_B, STOP_H, NINE_AM, 0,
                    0, 1, raptor);
            RaptorRouterTestHelpers.assertLatestDepartureConnection(connections.get(1), STOP_A, STOP_H, NINE_AM, 1, 0,
                    2, raptor);
        }

        @Test
        void routeFromTwoTargetStopsToTargetNoWalkTime(RaptorRouterTestBuilder builder) {
            RaptorAlgorithm raptor = builder.buildWithDefaults();

            Map<String, Integer> sourceStops = Map.of(STOP_A, 0);
            Map<String, LocalDateTime> targetStops = Map.of(STOP_F, NINE_AM, STOP_S, NINE_AM);

            // fastest and only connection should be A -> F
            List<Connection> connections = RaptorRouterTestHelpers.routeLatestDeparture(raptor, sourceStops,
                    targetStops);
            assertEquals(1, connections.size());
            RaptorRouterTestHelpers.assertLatestDepartureConnection(connections.getFirst(), STOP_A, STOP_F, NINE_AM, 0,
                    0, 1, raptor);
        }

        @Test
        void routeFromToTargetStopsWithDifferentArrivalTimes(RaptorRouterTestBuilder builder) {
            RaptorAlgorithm raptor = builder.buildWithDefaults();

            Map<String, Integer> sourceStops = Map.of(STOP_A, 0);
            // Add one-hour walk time to target from stop F and no extra walk time from stop S
            Map<String, LocalDateTime> targetStops = Map.of(STOP_F, EIGHT_AM, STOP_S, NINE_AM);

            // since F is closer to A than S, the fastest connection should be A -> F, but because of the hour
            // earlier arrival time, the connection A -> S should be faster (no additional walk time)
            List<Connection> connections = RaptorRouterTestHelpers.routeLatestDeparture(raptor, sourceStops,
                    targetStops);
            assertEquals(2, connections.size());
            RaptorRouterTestHelpers.assertLatestDepartureConnection(connections.getFirst(), STOP_A, STOP_F, EIGHT_AM, 0,
                    0, 1, raptor);
            RaptorRouterTestHelpers.assertLatestDepartureConnection(connections.get(1), STOP_A, STOP_S, NINE_AM, 1, 0,
                    2, raptor);
        }

        @Test
        void notFindConnectionBetweenNotLinkedStops(RaptorRouterTestBuilder builder) {
            // Omit route R2/R4 and transfers to make stop Q (on R3) unreachable from A (on R1)
            RaptorAlgorithm raptor = builder.withAddRoute1_AG().withAddRoute3_MQ().build();

            List<Connection> connections = RaptorRouterTestHelpers.routeLatestDeparture(raptor, STOP_A, STOP_Q,
                    NINE_AM);
            assertTrue(connections.isEmpty(), "No connection should be found");
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
            LocalDateTime arrivalTime = EIGHT_AM.plusMinutes(35);
            List<Connection> connections = RaptorRouterTestHelpers.routeLatestDeparture(raptor, STOP_A, STOP_G,
                    arrivalTime);

            assertEquals(1, connections.size());
            RaptorRouterTestHelpers.assertLatestDepartureConnection(connections.getFirst(), STOP_A, STOP_G, arrivalTime,
                    0, 0, 1, raptor);
            // check departure at 8:12
            Connection connection = connections.getFirst();
            assertEquals(EIGHT_AM.plusMinutes(12), connection.getDepartureTime());
            // check arrival time at 8:35
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
            List<Connection> connections = RaptorRouterTestHelpers.routeEarliestArrival(raptor, STOP_A, STOP_G,
                    EIGHT_AM);

            // Route R1 leaves at 8:00 at Stop A and arrives at G at 8:35 whereas R1X leaves at 7:45 from Stop A and
            // arrives at G at 8:08. R1 should be used.
            LocalDateTime arrivalTime = EIGHT_AM.plusMinutes(35);
            assertEquals(1, connections.size());
            RaptorRouterTestHelpers.assertLatestDepartureConnection(connections.getFirst(), STOP_A, STOP_G, arrivalTime,
                    0, 0, 1, raptor);
            // check departure at 8:00
            Connection connection = connections.getFirst();
            assertEquals(EIGHT_AM, connection.getDepartureTime());
            // check arrival time at 8:35
            assertEquals(arrivalTime, connection.getArrivalTime());
            // check that R1(-F for forward) route was used
            assertEquals("R1-F", connection.getRouteLegs().getFirst().getRouteId());
        }
    }

    @Nested
    class WalkTransfers {

        @Test
        void findConnectionBetweenOnlyFootpath(RaptorRouterTestBuilder builder) {
            RaptorAlgorithm raptor = builder.withAddRoute1_AG()
                    .withAddRoute2_HL()
                    .withAddRoute3_MQ()
                    .withAddRoute4_RS()
                    .withAddTransfer1_ND(1)
                    .withAddTransfer2_LR()
                    .build();

            List<Connection> connections = RaptorRouterTestHelpers.routeEarliestArrival(raptor, STOP_N, STOP_D,
                    EIGHT_AM);
            assertEquals(1, connections.size());
            RaptorRouterTestHelpers.assertEarliestArrivalConnection(connections.getFirst(), STOP_N, STOP_D, EIGHT_AM, 0,
                    1, 0, raptor);
        }

        @Test
        void findConnectionBetweenWithFootpath(RaptorRouterTestBuilder builder) {
            RaptorAlgorithm raptor = builder.withAddRoute1_AG().withAddRoute3_MQ().withAddTransfer1_ND().build();

            // Should return connection with two route legs and one walk transfer
            //  - Route R1-F from A to D
            //  - Foot Transfer from D to N
            //  - Route R3-F from N to Q
            List<Connection> connections = RaptorRouterTestHelpers.routeEarliestArrival(raptor, STOP_A, STOP_Q,
                    EIGHT_AM);

            assertEquals(1, connections.size());
            RaptorRouterTestHelpers.assertEarliestArrivalConnection(connections.getFirst(), STOP_A, STOP_Q, EIGHT_AM, 0,
                    1, 2, raptor);
        }

        @Test
        void findConnectionWithZeroTravelTimeTripsAndConsequentWalkTransfer(RaptorRouterTestBuilder builder) {
            // There are connections on local public transport where the travel time between stops is zero (in reality
            // 30 seconds or so, but rounded to the closest minute). This test ensures that the walk transfer is not
            // sorted before/after such a leg when rebuilding the connection.
            RaptorAlgorithm raptor = builder.withAddRoute1_AG(0, RaptorRouterTestBuilder.DEFAULT_HEADWAY_TIME, 0,
                            RaptorRouterTestBuilder.DEFAULT_DWELL_TIME)
                    .withAddRoute3_MQ(0, RaptorRouterTestBuilder.DEFAULT_HEADWAY_TIME, 0,
                            RaptorRouterTestBuilder.DEFAULT_DWELL_TIME)
                    .withAddTransfer1_ND()
                    .build();

            // Connection C <-> O will be 1-stop leg from C to D, a walk transfer to N and a 1-stop leg from N to O.
            // I.e. departure time at C will be equal to walk transfer departure at D and arrival time at O will be equal
            // to departure time at N.
            List<Connection> connections = RaptorRouterTestHelpers.routeEarliestArrival(raptor, STOP_C, STOP_O,
                    EIGHT_AM);
            assertEquals(1, connections.size());
            Connection connection = connections.getFirst();
            Leg firstRouteLeg = connection.getLegs().getFirst();
            Leg walkTransferLeg = connection.getLegs().get(1);
            assertEquals(Leg.Type.ROUTE, firstRouteLeg.getType());
            assertEquals(Leg.Type.WALK_TRANSFER, walkTransferLeg.getType());
            assertEquals(firstRouteLeg.getDepartureTime(), firstRouteLeg.getArrivalTime(),
                    "Departure time at C should be equal to arrival time at D");
            assertEquals(firstRouteLeg.getDepartureTime(), walkTransferLeg.getDepartureTime(),
                    "Departure time at C should be equal to walk departure time at D");
            RaptorRouterTestHelpers.assertEarliestArrivalConnection(connections.getFirst(), STOP_C, STOP_O, EIGHT_AM, 0,
                    1, 2, raptor);
        }

        @Test
        void ensureUnnecessaryWalkTransferIsNotAdded(RaptorRouterTestBuilder builder) {
            // This test should ensure that the router does not add a walk transfer at the beginning of a trip only to
            // reduce the earliest arrival time at the next stop when the following route leg could have also been entered
            // at the previous stop (same overall arrival time only more walking and earlier departure time).
            RaptorAlgorithm raptor = builder.withAddRoute1_AG().withAddTransfer("A", "B", 15).build();

            // Route connection from A <-> C can be connected by a route trip starting at 8:15 at A, arriving at B at
            // 8:20 and then at C at 8:26.
            // Since the earliest arrival request is set to depart at 08:01 and the walk to B takes 15 minutes, the
            // earliest arrival at B is 8:16. However, in this case the traveller still has to wait until 8:21 to depart
            // from B. The walk transfer should not be added in this case.
            LocalDateTime requestedDepartureTime = EIGHT_AM.plusMinutes(1);
            List<Connection> connections = RaptorRouterTestHelpers.routeEarliestArrival(raptor, STOP_A, STOP_C,
                    requestedDepartureTime);

            assertEquals(1, connections.size());
            RaptorRouterTestHelpers.assertEarliestArrivalConnection(connections.getFirst(), STOP_A, STOP_C,
                    requestedDepartureTime, 0, 0, 1, raptor);
        }

        @Test
        void ensureFinalLegDoesNotFavorWalkTransferBecauseOfSameStopTransferTime(RaptorRouterTestBuilder builder) {
            // This test is intended to ensure that due to the subtraction of the same transfer time from the arrival time
            // of a walk transfer, the walk transfer is not falsely favored over a route leg arriving at the final stop.
            RaptorAlgorithm raptor = builder.withAddRoute1_AG()
                    .withAddTransfer("B", "C", 7)
                    .withSameStopTransferTime(120)
                    .build();

            // Route connection from A <-> C can be connected by a route trip starting at 8:00 at A, arriving at B at
            // 8:05 and then at C at 8:11. If the walk from B to C takes 7 minutes the "comparable" arrival time at C
            // will be also 8:10. However, since the "real" arrival time will be 8:12, the walk transfer should not be
            // favored.
            List<Connection> connections = RaptorRouterTestHelpers.routeEarliestArrival(raptor, STOP_A, STOP_C,
                    EIGHT_AM);
            RaptorRouterTestHelpers.assertEarliestArrivalConnection(connections.getFirst(), STOP_A, STOP_C, EIGHT_AM, 0,
                    0, 1, raptor);
        }

        @Test
        void ensureFinalWalkTransferIsAddedIfArrivesEarlierThanRouteLeg(RaptorRouterTestBuilder builder) {
            // This test is intended to ensure that a walk transfer is added at the final stop if it arrives earlier than
            // the route leg.
            RaptorAlgorithm raptor = builder.withAddRoute1_AG()
                    .withAddTransfer("B", "C", 5)
                    .withSameStopTransferTime(120)
                    .build();

            // Route connection from A <-> C can be connected by a route trip starting at 8:00 at A, arriving at B at
            // 8:05 and then at C at 8:11. If the walk from B to C takes 5 minutes the arrival time at C is 8:10 and
            // should be favored over the route leg arriving at 8:11.
            List<Connection> connections = RaptorRouterTestHelpers.routeEarliestArrival(raptor, STOP_A, STOP_C,
                    EIGHT_AM);
            RaptorRouterTestHelpers.assertEarliestArrivalConnection(connections.getFirst(), STOP_A, STOP_C, EIGHT_AM, 0,
                    1, 1, raptor);
        }

        @Test
        void initialWalkTransferShouldLeaveAsLateAsPossible(RaptorRouterTestBuilder builder) {
            // This test tests that the walk transfer at a beginning of a trip leaves as late as possible, i.e. arriving
            // at the next stop at the same time as the route leg departs.
            RaptorAlgorithm raptor = builder.withAddRoute1_AG().withAddRoute3_MQ().withAddTransfer1_ND(15).build();

            // Connection from N to E can be connected by a walk transfer from N to D and then a route trip from D to E.
            // The route trip from D to E leaves at 8:18. The walk transfer requires 15 minutes of walking, thus should
            // leave N at 8:03 to reach D on time (8:18). The requested earliest departure time is 8:00.
            List<Connection> connections = RaptorRouterTestHelpers.routeEarliestArrival(raptor, STOP_N, STOP_E,
                    EIGHT_AM);
            assertEquals(1, connections.size());
            assertEquals(EIGHT_AM.plusMinutes(3), connections.getFirst().getDepartureTime());
            RaptorRouterTestHelpers.assertEarliestArrivalConnection(connections.getFirst(), STOP_N, STOP_E, EIGHT_AM, 0,
                    1, 1, raptor);
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
            List<Connection> connections = RaptorRouterTestHelpers.routeEarliestArrival(raptor, STOP_A, STOP_H,
                    FIVE_AM);
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
            List<Connection> connections = RaptorRouterTestHelpers.routeEarliestArrival(raptor, STOP_A, STOP_H,
                    FIVE_AM);

            assertEquals(1, connections.size());

            assertEquals(FIVE_AM.plusMinutes(19), connections.getFirst().getDepartureTime());
            assertNotEquals(FIVE_AM.plusMinutes(24), connections.getFirst().getLegs().get(1).getDepartureTime());
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
            List<Connection> connections = RaptorRouterTestHelpers.routeEarliestArrival(raptor, STOP_A, STOP_H,
                    FIVE_AM);

            assertEquals(1, connections.size());
            assertEquals(FIVE_AM.plusMinutes(19), connections.getFirst().getDepartureTime());
            assertEquals(FIVE_AM.plusMinutes(24), connections.getFirst().getLegs().get(1).getDepartureTime());
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
            List<Connection> connections = RaptorRouterTestHelpers.routeEarliestArrival(raptor, STOP_A, STOP_H,
                    FIVE_AM);

            assertEquals(1, connections.size());
            assertEquals(FIVE_AM.plusMinutes(17), connections.getFirst().getDepartureTime());
            assertEquals(FIVE_AM.plusMinutes(24), connections.getFirst().getLegs().get(1).getDepartureTime());
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
            List<Connection> connections = RaptorRouterTestHelpers.routeEarliestArrival(raptor, STOP_A, STOP_Q,
                    EIGHT_AM, queryConfig);

            // check if 2 connections were found
            assertEquals(2, connections.size());
            RaptorRouterTestHelpers.assertEarliestArrivalConnection(connections.getFirst(), STOP_A, STOP_Q, EIGHT_AM, 0,
                    1, 2, raptor);
            RaptorRouterTestHelpers.assertEarliestArrivalConnection(connections.get(1), STOP_A, STOP_Q, EIGHT_AM, 2, 0,
                    3, raptor);
            RaptorRouterTestHelpers.checkIfConnectionsAreParetoOptimal(connections);
        }

        @Test
        void notFindWalkableTransferWithMaxWalkingTime(RaptorRouterTestBuilder builder) {
            RaptorAlgorithm raptor = builder.buildWithDefaults();
            QueryConfig queryConfig = new QueryConfig();
            queryConfig.setMaximumWalkingDuration(RaptorRouterTestBuilder.SECONDS_IN_HOUR / 4); // 15 minutes

            // Should only find three route leg connections, since direct transfer between D and N is longer than
            // allowed maximum walking distance (60 minutes):
            List<Connection> connections = RaptorRouterTestHelpers.routeEarliestArrival(raptor, STOP_A, STOP_Q,
                    EIGHT_AM, queryConfig);
            assertEquals(1, connections.size());
            RaptorRouterTestHelpers.assertEarliestArrivalConnection(connections.getFirst(), STOP_A, STOP_Q, EIGHT_AM, 2,
                    0, 3, raptor);
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
            List<Connection> connections = RaptorRouterTestHelpers.routeEarliestArrival(raptor, STOP_A, STOP_Q,
                    EIGHT_AM, queryConfig);
            assertEquals(1, connections.size());
            RaptorRouterTestHelpers.assertEarliestArrivalConnection(connections.getFirst(), STOP_A, STOP_Q, EIGHT_AM, 0,
                    1, 2, raptor);
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
            List<Connection> connections = RaptorRouterTestHelpers.routeEarliestArrival(raptor, STOP_A, STOP_Q,
                    EIGHT_AM, queryConfig);
            assertEquals(1, connections.size());
            RaptorRouterTestHelpers.assertEarliestArrivalConnection(connections.getFirst(), STOP_A, STOP_Q, EIGHT_AM, 2,
                    0, 3, raptor);
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
            List<Connection> connections = RaptorRouterTestHelpers.routeEarliestArrival(raptor, STOP_A, STOP_H, FIVE_AM,
                    queryConfig);

            assertEquals(1, connections.size());
            assertEquals(FIVE_AM.plusMinutes(19), connections.getFirst().getDepartureTime());
            assertEquals(FIVE_AM.plusMinutes(39), connections.getFirst().getLegs().get(1).getDepartureTime());
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
            List<Connection> connections = RaptorRouterTestHelpers.routeEarliestArrival(raptor, STOP_A, STOP_H, FIVE_AM,
                    queryConfig);

            assertEquals(1, connections.size());
            assertEquals(FIVE_AM.plusMinutes(19), connections.getFirst().getDepartureTime());
            assertEquals(FIVE_AM.plusMinutes(54), connections.getFirst().getLegs().get(1).getDepartureTime());
        }

        @Test
        void addMinimumTransferTimeToWalkTransferDuration(RaptorRouterTestBuilder builder) {
            QueryConfig queryConfig = new QueryConfig();
            queryConfig.setMinimumTransferDuration(20 * 60); // 20 minutes

            RaptorAlgorithm raptor = builder.buildWithDefaults();
            List<Connection> connections = RaptorRouterTestHelpers.routeEarliestArrival(raptor, STOP_A, STOP_Q,
                    EIGHT_AM, queryConfig);

            assertEquals(2, connections.size());
            Connection firstConnection = connections.getFirst();
            RaptorRouterTestHelpers.assertEarliestArrivalConnection(firstConnection, STOP_A, STOP_Q, EIGHT_AM, 0, 1, 2,
                    raptor);

            // The walk transfer from D to N takes 60 minutes and the route from N to Q leaves every 75 minutes.
            Leg firstLeg = firstConnection.getRouteLegs().getFirst();
            Leg secondLeg = firstConnection.getRouteLegs().getLast();
            int timeDiff = (int) Duration.between(firstLeg.getArrivalTime(), secondLeg.getDepartureTime()).toMinutes();
            assertTrue(timeDiff >= 75, "Time between trips should be at least 75 minutes");
        }

    }

    @Nested
    class IsoLines {

        @Test
        void createIsoLinesToAllStops(RaptorRouterTestBuilder builder) {
            RaptorAlgorithm raptor = builder.buildWithDefaults();
            Map<String, Connection> isoLines = RaptorRouterTestHelpers.getIsoLines(raptor, Map.of(STOP_A, EIGHT_AM));

            int stopsInSystem = 19;
            int expectedIsoLines = stopsInSystem - 1;
            RaptorRouterTestHelpers.assertIsoLines(isoLines, STOP_A, EIGHT_AM, expectedIsoLines);
        }

        @Test
        void createIsoLinesToSomeStopsNotAllConnected(RaptorRouterTestBuilder builder) {
            // Route 1 and 3 are not connected, thus all Stops of Route 3 should not be reachable from A
            RaptorAlgorithm raptor = builder.withAddRoute1_AG().withAddRoute3_MQ().build();
            Map<String, Connection> isoLines = RaptorRouterTestHelpers.getIsoLines(raptor, Map.of(STOP_A, EIGHT_AM));

            List<String> reachableStops = List.of(STOP_B, STOP_C, STOP_D, STOP_E, STOP_F, STOP_G);
            // Not Reachable Stops: M, K, N, O, P, Q

            RaptorRouterTestHelpers.assertIsoLines(isoLines, STOP_A, EIGHT_AM, reachableStops.size());

            for (String stop : reachableStops) {
                assertTrue(isoLines.containsKey(stop), "Stop " + stop + " should be reachable");
            }
        }

        @Test
        void createIsoLinesToStopsOfOtherLineOnlyConnectedByFootpath(RaptorRouterTestBuilder builder) {
            // Route 1 and Route 3 are only connected by Footpath between Stops D and N
            RaptorAlgorithm raptor = builder.withAddRoute1_AG().withAddRoute3_MQ().withAddTransfer1_ND().build();
            Map<String, Connection> isoLines = RaptorRouterTestHelpers.getIsoLines(raptor, Map.of(STOP_A, EIGHT_AM));

            List<String> reachableStops = List.of(STOP_B, STOP_C, STOP_D, STOP_E, STOP_F, STOP_G, STOP_M, STOP_K,
                    STOP_N, STOP_O, STOP_P, STOP_Q);

            RaptorRouterTestHelpers.assertIsoLines(isoLines, STOP_A, EIGHT_AM, reachableStops.size());

            for (String stop : reachableStops) {
                assertTrue(isoLines.containsKey(stop), "Stop " + stop + " should be reachable");
            }
        }

        @Test
        void createIsoLinesFromTwoNotConnectedSourceStops(RaptorRouterTestBuilder builder) {
            RaptorAlgorithm raptor = builder.withAddRoute1_AG().withAddRoute3_MQ().build();

            List<String> reachableStopsFromStopA = List.of(STOP_B, STOP_C, STOP_D, STOP_E, STOP_F, STOP_G);
            Map<String, LocalDateTime> sourceStops = Map.of(STOP_A, START_OF_DAY.plusHours(8), STOP_M,
                    START_OF_DAY.plusHours(16));
            List<String> reachableStopsFromStopM = List.of(STOP_K, STOP_N, STOP_O, STOP_P, STOP_Q);

            Map<String, Connection> isoLines = RaptorRouterTestHelpers.getIsoLines(raptor, sourceStops);

            assertEquals(reachableStopsFromStopA.size() + reachableStopsFromStopM.size(), isoLines.size());

            Map<String, List<String>> sourceTargets = Map.of(STOP_A, reachableStopsFromStopA, STOP_M,
                    reachableStopsFromStopM);

            for (Map.Entry<String, List<String>> entry : sourceTargets.entrySet()) {
                String sourceStop = entry.getKey();
                List<String> reachableStops = entry.getValue();
                LocalDateTime requestedDepartureTime = sourceStops.get(sourceStop);
                for (String stop : reachableStops) {
                    assertTrue(isoLines.containsKey(stop), "Stop " + stop + " should be reachable from " + sourceStop);
                    Connection connection = isoLines.get(stop);
                    assertFalse(connection.getDepartureTime().isBefore(requestedDepartureTime),
                            String.format("Connection should have departure time equal or after %d:00",
                                    requestedDepartureTime.getHour()));
                    assertNotNull(connection.getArrivalTime(), "Connection should have arrival time");
                    assertNotNull(connection.getDepartureTime(), "Connection should have departure time");
                    assertEquals(connection.getFromStopId(), sourceStop, "From stop should be " + sourceStop);
                    assertEquals(connection.getToStopId(), stop, "To stop should be " + stop);
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
                    () -> RaptorRouterTestHelpers.routeEarliestArrival(raptor, sourceStop, STOP_A, EIGHT_AM),
                    "Source stop has to exists");
        }

        @Test
        void throwErrorWhenTargetStopNotExists() {
            String targetStop = "NonExistentStop";
            assertThrows(IllegalArgumentException.class,
                    () -> RaptorRouterTestHelpers.routeEarliestArrival(raptor, STOP_A, targetStop, EIGHT_AM),
                    "Target stop has to exists");
        }

        @Test
        void notThrowErrorForValidAndNonExistingSourceStop() {
            Map<String, LocalDateTime> sourceStops = Map.of(STOP_A, EIGHT_AM, "NonExistentStop", EIGHT_AM);
            Map<String, Integer> targetStops = Map.of(STOP_H, 0);

            assertDoesNotThrow(() -> RaptorRouterTestHelpers.routeEarliestArrival(raptor, sourceStops, targetStops),
                    "Source stops can contain non-existing stops, if one entry is valid");
        }

        @Test
        void notThrowErrorForValidAndNonExistingTargetStop() {
            Map<String, LocalDateTime> sourceStops = Map.of(STOP_H, EIGHT_AM);
            Map<String, Integer> targetStops = Map.of(STOP_A, 0, "NonExistentStop", 0);

            assertDoesNotThrow(() -> RaptorRouterTestHelpers.routeEarliestArrival(raptor, sourceStops, targetStops),
                    "Target stops can contain non-existing stops, if one entry is valid");
        }

        @Test
        void throwErrorForInvalidWalkToTargetTimeFromOneOfManyTargetStops() {
            Map<String, LocalDateTime> sourceStops = Map.of(STOP_H, EIGHT_AM);
            Map<String, Integer> targetStops = Map.of(STOP_A, 0, STOP_B, -1);

            assertThrows(IllegalArgumentException.class,
                    () -> RaptorRouterTestHelpers.routeEarliestArrival(raptor, sourceStops, targetStops),
                    "Departure time has to be valid for all valid source stops");
        }

        @Test
        void throwErrorNullSourceStops() {
            Map<String, Integer> targetStops = Map.of(STOP_H, 0);

            assertThrows(IllegalArgumentException.class,
                    () -> RaptorRouterTestHelpers.routeEarliestArrival(raptor, null, targetStops),
                    "Source stops cannot be null");
        }

        @Test
        void throwErrorNullTargetStops() {
            Map<String, LocalDateTime> sourceStops = Map.of(STOP_A, START_OF_DAY);

            assertThrows(IllegalArgumentException.class,
                    () -> RaptorRouterTestHelpers.routeEarliestArrival(raptor, sourceStops, null),
                    "Target stops cannot be null");
        }

        @Test
        void throwErrorEmptyMapSourceStops() {
            Map<String, LocalDateTime> sourceStops = Map.of();
            Map<String, Integer> targetStops = Map.of(STOP_H, 0);

            assertThrows(IllegalArgumentException.class,
                    () -> RaptorRouterTestHelpers.routeEarliestArrival(raptor, sourceStops, targetStops),
                    "Source and target stops cannot be null");
        }

        @Test
        void throwErrorEmptyMapTargetStops() {
            Map<String, LocalDateTime> sourceStops = Map.of(STOP_A, START_OF_DAY);
            Map<String, Integer> targetStops = Map.of();

            assertThrows(IllegalArgumentException.class,
                    () -> RaptorRouterTestHelpers.routeEarliestArrival(raptor, sourceStops, targetStops),
                    "Source and target stops cannot be null");
        }

        @Test
        void throwErrorWhenRequestBetweenSameStop() {
            assertThrows(IllegalArgumentException.class,
                    () -> RaptorRouterTestHelpers.routeEarliestArrival(raptor, STOP_A, STOP_A, EIGHT_AM),
                    "Stops cannot be the same");
        }

    }

}
