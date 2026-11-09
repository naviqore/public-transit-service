package org.naviqore.raptor.router;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.naviqore.raptor.Connection;
import org.naviqore.raptor.QueryConfig;
import org.naviqore.raptor.RaptorAlgorithm;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test class for the raptor algorithm in range RAPTOR mode.
 */
@ExtendWith(RaptorRouterTestExtension.class)
public class RaptorRouterRangeTest {

    private static final String STOP_A = "A";
    private static final String STOP_B = "B";
    private static final String STOP_C = "C";
    private static final String STOP_I = "I";
    private static final String STOP_K = "K";
    private static final String STOP_N = "N";

    private static final OffsetDateTime START_OF_DAY = RaptorRouterTestBuilder.DEFAULT_REFERENCE_DATE.atStartOfDay(
            RaptorRouterTestBuilder.DEFAULT_ZONE_ID).toOffsetDateTime();
    private static final OffsetDateTime EIGHT_AM = START_OF_DAY.plusHours(8);

    /**
     * Builds a range raptor router with a low frequency fast connection and a high frequency slower connection from A
     * to K, and a range of 15 minutes, which covers the departures from A at 08:00 and 08:15.
     */
    private static RaptorAlgorithm buildRouterWithFastAndSlowConnection() {
        int headwayRoute1 = 15;
        int headwayRoute2 = 60;
        int headwayRoute3and4 = 5;

        int dwellTime = 0; // simplification to better calculate times by hand

        return new RaptorRouterTestBuilder().withAddRoute1_AG(RaptorRouterTestBuilder.DEFAULT_OFFSET, headwayRoute1,
                        RaptorRouterTestBuilder.DEFAULT_TIME_BETWEEN_STOPS, dwellTime)
                .withAddRoute2_HL(RaptorRouterTestBuilder.DEFAULT_OFFSET, headwayRoute2,
                        RaptorRouterTestBuilder.DEFAULT_TIME_BETWEEN_STOPS, dwellTime)
                .withAddRoute3_MQ(RaptorRouterTestBuilder.DEFAULT_OFFSET, headwayRoute3and4,
                        RaptorRouterTestBuilder.DEFAULT_TIME_BETWEEN_STOPS, dwellTime)
                .withAddRoute4_RS(RaptorRouterTestBuilder.DEFAULT_OFFSET, headwayRoute3and4,
                        RaptorRouterTestBuilder.DEFAULT_TIME_BETWEEN_STOPS, dwellTime)
                .withSameStopTransferTime(0)
                .withRaptorRange(900) // departures at 08:00 and 08:15
                .withMaxDaysToScan(1)
                .build();
    }

    /**
     * Adds a route with trips calling at its stops the given minutes after the start of the service day.
     */
    private static void addRouteWithTrips(RaptorRouterBuilder builder, String routeId, List<String> stops,
                                          List<List<Integer>> minutesOfTrips) {
        builder.addRoute(routeId, RaptorRouterTestBuilder.DEFAULT_ZONE_ID, stops);
        for (int trip = 0; trip < minutesOfTrips.size(); trip++) {
            String tripId = routeId + "-" + trip;
            builder.addTrip(tripId, routeId);
            for (int i = 0; i < stops.size(); i++) {
                int seconds = minutesOfTrips.get(trip).get(i) * 60;
                builder.addStopTime(routeId, tripId, i, stops.get(i), seconds, seconds);
            }
        }
    }

    @Test
    void findDepartureConnections(RaptorRouterTestBuilder builder) {
        int headway_route_1 = 15;
        int headway_route_2 = 30;

        int offset_route_2 = 15;

        OffsetDateTime departureTime = EIGHT_AM;

        RaptorAlgorithm rangeRaptor = builder.withAddRoute1_AG(RaptorRouterTestBuilder.DEFAULT_OFFSET, headway_route_1,
                        RaptorRouterTestBuilder.DEFAULT_TIME_BETWEEN_STOPS, RaptorRouterTestBuilder.DEFAULT_DWELL_TIME)
                .withAddRoute2_HL(offset_route_2, headway_route_2, RaptorRouterTestBuilder.DEFAULT_TIME_BETWEEN_STOPS,
                        RaptorRouterTestBuilder.DEFAULT_DWELL_TIME)
                .withSameStopTransferTime(0)
                .withRaptorRange(1800)
                .withMaxDaysToScan(1)
                .build();

        // Find connections from "A" to "I" departing at 8:00 am.
        // Route 1 leaves "A" at 8:00, 8:15, 8:30, 8:45... and arrives at "B" 5 minutes later.
        // Route 2 leaves "H" at 8:15, 8:45, 9:15... and arrives at "B" 5 minutes later and continues on to "I" one
        // minute after arriving, the trip from "B" to "I" requires 5 minutes. Therefore, the shortest possible travel
        // time is 11 minutes. Since the headway of route 1 is 15 minutes and the headway of route 2 is 30 minutes,
        // the ideal connection leaves at 8:15, which should be suggested by the range raptor. A "normal" raptor
        // algorithm would suggest the connection at 8:00.
        List<Connection> connections = RaptorRouterTestHelpers.routeEarliestArrival(rangeRaptor, STOP_A, STOP_I,
                departureTime);

        OffsetDateTime expectedDepartureTime = EIGHT_AM.plusMinutes(15);
        OffsetDateTime expectedArrivalTime = expectedDepartureTime.plusMinutes(
                2 * RaptorRouterTestBuilder.DEFAULT_TIME_BETWEEN_STOPS + RaptorRouterTestBuilder.DEFAULT_DWELL_TIME);

        assertEquals(1, connections.size());
        RangeRaptorHelpers.assertConnection(connections.getFirst(), expectedDepartureTime, expectedArrivalTime);

        // confirm that simple raptor would suggest the connection at 8:00
        RaptorAlgorithm simpleRaptor = builder.withRaptorRange(-1).build();
        connections = RaptorRouterTestHelpers.routeEarliestArrival(simpleRaptor, STOP_A, STOP_I, departureTime);
        expectedDepartureTime = EIGHT_AM;
        // expected arrival time is the same as with range raptor (only 15 minute idle time and transfer stop)
        assertEquals(1, connections.size());
        RangeRaptorHelpers.assertConnection(connections.getFirst(), expectedDepartureTime, expectedArrivalTime);

        // confirm that range raptor with 10 minute range (smaller than the 15-minute headway of route 1) would suggest
        // the connection at 8:00
        rangeRaptor = builder.withRaptorRange(600).build();
        connections = RaptorRouterTestHelpers.routeEarliestArrival(rangeRaptor, STOP_A, STOP_I, departureTime);
        assertEquals(1, connections.size());
        RangeRaptorHelpers.assertConnection(connections.getFirst(), expectedDepartureTime, expectedArrivalTime);
    }

    @Test
    void findArrivalConnections(RaptorRouterTestBuilder builder) {
        int headway_route_1 = 30;
        int headway_route_2 = 15;

        int offset_route_1 = 15;

        // Find connections from "A" to "I" arriving at 8:41 am.
        // Route 1 leaves "A" at 7:45, 8:15, 8:45 ... and arrives at "B" 5 minutes later.
        // Route 2 leaves "H" at 8:00, 8:15, 8:30, 8:45 ... and arrives at "B" 5 minutes later and continues on to "I"
        // one minute after arriving, the trip from "B" to "I" requires 5 minutes. Therefore, the shortest possible
        // travel time is 11 minutes. Since the headway of route 1 is 30 minutes and the headway of route 2 is 15
        // minutes, the ideal connection arrives at 8:26, which should be suggested by the range raptor. A "normal"
        // raptor algorithm would suggest the connection arriving at 8:41.

        OffsetDateTime arrivalTime = EIGHT_AM.plusMinutes(41);

        RaptorAlgorithm rangeRaptor = builder.withAddRoute1_AG(offset_route_1, headway_route_1,
                        RaptorRouterTestBuilder.DEFAULT_TIME_BETWEEN_STOPS, RaptorRouterTestBuilder.DEFAULT_DWELL_TIME)
                .withAddRoute2_HL(RaptorRouterTestBuilder.DEFAULT_OFFSET, headway_route_2,
                        RaptorRouterTestBuilder.DEFAULT_TIME_BETWEEN_STOPS, RaptorRouterTestBuilder.DEFAULT_DWELL_TIME)
                .withSameStopTransferTime(0)
                .withRaptorRange(1800)
                .withMaxDaysToScan(1)
                .build();

        List<Connection> connections = RaptorRouterTestHelpers.routeLatestDeparture(rangeRaptor, STOP_A, STOP_I,
                arrivalTime);
        OffsetDateTime expectedArrivalTime = EIGHT_AM.plusMinutes(26);
        OffsetDateTime expectedDepartureTime = expectedArrivalTime.minusMinutes(
                2 * RaptorRouterTestBuilder.DEFAULT_TIME_BETWEEN_STOPS + RaptorRouterTestBuilder.DEFAULT_DWELL_TIME);

        assertEquals(1, connections.size());
        RangeRaptorHelpers.assertConnection(connections.getFirst(), expectedDepartureTime, expectedArrivalTime);

        // Confirm that simple raptor would suggest the connection arriving at 8:41
        RaptorAlgorithm simpleRaptor = builder.withRaptorRange(-1).build();
        connections = RaptorRouterTestHelpers.routeLatestDeparture(simpleRaptor, STOP_A, STOP_I, arrivalTime);
        expectedArrivalTime = EIGHT_AM.plusMinutes(41);
        // expected departure time is the same as with range raptor (only 15 minute idle time and transfer stop)
        assertEquals(1, connections.size());
        RangeRaptorHelpers.assertConnection(connections.getFirst(), expectedDepartureTime, expectedArrivalTime);

        // Confirm that range raptor with 10 minute range (smaller than the 15-minute headway of route 2) would suggest
        // the connection arriving at 8:41
        rangeRaptor = builder.withRaptorRange(600).build();
        connections = RaptorRouterTestHelpers.routeLatestDeparture(rangeRaptor, STOP_A, STOP_I, arrivalTime);
        assertEquals(1, connections.size());
        RangeRaptorHelpers.assertConnection(connections.getFirst(), expectedDepartureTime, expectedArrivalTime);
    }

    @Test
    void findDepartureConnections_thatWouldBeSameAsSimpleRaptor(RaptorRouterTestBuilder builder) {

        OffsetDateTime departureTime = EIGHT_AM;

        RaptorAlgorithm rangeRaptor = builder.withAddRoute1_AG()
                .withAddRoute2_HL()
                .withSameStopTransferTime(0)
                .withRaptorRange(1800)
                .withMaxDaysToScan(1)
                .build();

        // Find connections from "A" to "I" departing at 8:00 am.
        // Route 1 leaves "A" at 8:00, 8:15... and arrives at "B" 5 minutes later.
        // Route 2 leaves "H" at 8:00, 8:15... and arrives at "B" 5 minutes later and continues on to "I" one minute
        // after arriving, the trip from "B" to "I" requires 5 minutes. Therefore, the shortest possible travel
        // time is 11 minutes. Since both routes have the same headway the range raptor should return the same
        // connection as a simple raptor implementation.
        List<Connection> connections = RaptorRouterTestHelpers.routeEarliestArrival(rangeRaptor, STOP_A, STOP_I,
                departureTime);

        OffsetDateTime expectedArrivalTime = EIGHT_AM.plusMinutes(
                2 * RaptorRouterTestBuilder.DEFAULT_TIME_BETWEEN_STOPS + RaptorRouterTestBuilder.DEFAULT_DWELL_TIME);

        assertEquals(1, connections.size());
        Connection rangeRaptorConnection = connections.getFirst();
        RangeRaptorHelpers.assertConnection(rangeRaptorConnection, EIGHT_AM, expectedArrivalTime);

        // check that simple raptor connection is the same
        RaptorAlgorithm simpleRaptor = builder.withRaptorRange(-1).build();
        connections = RaptorRouterTestHelpers.routeEarliestArrival(simpleRaptor, STOP_A, STOP_I, departureTime);

        assertEquals(1, connections.size());
        Connection simpleRaptorConnection = connections.getFirst();
        RangeRaptorHelpers.assertConnection(simpleRaptorConnection, EIGHT_AM, expectedArrivalTime);
    }

    @Test
    void findArrivalConnections_thatWouldBeSameAsSimpleRaptor(RaptorRouterTestBuilder builder) {
        OffsetDateTime arrivalTime = EIGHT_AM.plusMinutes(11);
        OffsetDateTime expectedDepartureTime = EIGHT_AM;

        RaptorAlgorithm rangeRaptor = builder.withAddRoute1_AG()
                .withAddRoute2_HL()
                .withSameStopTransferTime(0)
                .withRaptorRange(1800)
                .withMaxDaysToScan(1)
                .build();

        RaptorAlgorithm simpleRaptor = builder.withRaptorRange(-1).build();

        // Find connections from "A" to "I" arriving at 8:11 am. Should leave at "A" at 8:00 for both range and simple
        // raptor.
        List<Connection> rangeRaptorConnections = RaptorRouterTestHelpers.routeLatestDeparture(rangeRaptor, STOP_A,
                STOP_I, arrivalTime);
        List<Connection> simpleRaptorConnections = RaptorRouterTestHelpers.routeLatestDeparture(simpleRaptor, STOP_A,
                STOP_I, arrivalTime);

        assertEquals(1, rangeRaptorConnections.size());
        assertEquals(1, simpleRaptorConnections.size());

        RangeRaptorHelpers.assertConnection(rangeRaptorConnections.getFirst(), expectedDepartureTime, arrivalTime);
        RangeRaptorHelpers.assertConnection(simpleRaptorConnections.getFirst(), expectedDepartureTime, arrivalTime);
    }

    @Test
    void findDepartureConnections_withSourceTransferFirst() {
        int headway_route_1 = 15;
        int headway_route_2 = 30;

        RaptorAlgorithm rangeRaptor = new RaptorRouterTestBuilder().withAddRoute1_AG(
                        RaptorRouterTestBuilder.DEFAULT_OFFSET, headway_route_1,
                        RaptorRouterTestBuilder.DEFAULT_TIME_BETWEEN_STOPS, RaptorRouterTestBuilder.DEFAULT_DWELL_TIME)
                .withAddRoute2_HL(RaptorRouterTestBuilder.DEFAULT_OFFSET, headway_route_2,
                        RaptorRouterTestBuilder.DEFAULT_TIME_BETWEEN_STOPS, RaptorRouterTestBuilder.DEFAULT_DWELL_TIME)
                .withAddRoute3_MQ() // to create stop N
                .withAddTransfer(STOP_N, STOP_A, 15)
                .withSameStopTransferTime(0)
                .withRaptorRange(1800)
                .withMaxDaysToScan(1)
                .build();

        // Find connections from "N" to "I" departing at 8:00 am.
        // It takes 15 minutes to walk from "N" to "A", thus earliest arrival at "A" is 8:15.
        // Route 1 leaves "A" at 8:00, 8:15, 8:30, 8:45... and arrives at "B" 5 minutes later.
        // Route 2 leaves "H" at 8:00, 8:30, 9:00... and arrives at "B" 5 minutes later and continues on to "I" one
        // minute after arriving, the trip from "B" to "I" requires 5 minutes. Therefore, the shortest possible travel
        // is 26 minutes, but since Route 2 only arrives at "B" at 8:35, the connection leaving "A" at 8:15  does not
        // make sense and the connection leaving at 8:30 is the best option (for range raptor) --> the proposed
        // connection should suggest leaving "N" at 8:15.
        List<Connection> connections = RaptorRouterTestHelpers.routeEarliestArrival(rangeRaptor, STOP_N, STOP_I,
                EIGHT_AM);

        OffsetDateTime expectedDepartureTime = EIGHT_AM.plusMinutes(15);
        OffsetDateTime expectedArrivalTime = expectedDepartureTime.plusMinutes(26);

        // first connection is the one with the least route legs --> including the transfer
        RangeRaptorHelpers.assertConnection(connections.getFirst(), expectedDepartureTime, expectedArrivalTime, 3,
                STOP_N, STOP_I);
    }

    @Test
    void findArrivalConnections_withSourceTransferFirst() {
        int headway_route_1 = 30;
        int headway_route_2 = 15;

        RaptorAlgorithm rangeRaptor = new RaptorRouterTestBuilder().withAddRoute1_AG(
                        RaptorRouterTestBuilder.DEFAULT_OFFSET, headway_route_1,
                        RaptorRouterTestBuilder.DEFAULT_TIME_BETWEEN_STOPS, RaptorRouterTestBuilder.DEFAULT_DWELL_TIME)
                .withAddRoute2_HL(RaptorRouterTestBuilder.DEFAULT_OFFSET, headway_route_2,
                        RaptorRouterTestBuilder.DEFAULT_TIME_BETWEEN_STOPS, RaptorRouterTestBuilder.DEFAULT_DWELL_TIME)
                .withAddRoute3_MQ() // to create stop N
                .withAddTransfer(STOP_N, STOP_I, 15)
                .withSameStopTransferTime(0)
                .withRaptorRange(1800)
                .withMaxDaysToScan(1)
                .build();

        // Find connections from "A" to "N" arriving at 8:41 am.
        // It takes 15 minutes to walk from "I" to "N", thus the latest departure at "N" is 8:26.
        // Route 1 leaves "A" at 8:00, 8:30, 9:00... and arrives at "B" 5 minutes later.
        // Route 2 leaves "H" at 8:00, 8:15, 8:30... and arrives at "B" 5 minutes later and continues on to "I" one
        // minute after arriving, the trip from "B" to "I" requires 5 minutes. Therefore, the shortest possible travel
        // is 26 minutes, but since Route 1 only arrives at "B" at 8:05, the connection leaving "B" at 8:06 to "I" makes
        // more sense than waiting for the 8:21 Route 2 connection, thus the arrival time at "N" should be 8:26.
        List<Connection> connections = RaptorRouterTestHelpers.routeLatestDeparture(rangeRaptor, STOP_A, STOP_N,
                EIGHT_AM.plusMinutes(41));

        OffsetDateTime expectedDepartureTime = EIGHT_AM;
        OffsetDateTime expectedArrivalTime = expectedDepartureTime.plusMinutes(26);

        // first connection is the one with the least route legs --> including the transfer
        RangeRaptorHelpers.assertConnection(connections.getFirst(), expectedDepartureTime, expectedArrivalTime, 3,
                STOP_A, STOP_N);
    }

    @Test
    void ensureParetoOptimalConnections() {
        // this test is based on a previous bug, where the range raptor router returned connections which were not
        // pareto optimal (later arrival time and more rounds). This is owed to the fact that the range raptor spawns
        // at different time points (range offsets) and potentially finds earliest arrival connections for the given
        // offset with more rounds than the final best arrival time.
        // this test reproduces this case by introducing a low frequency fast connection and a high frequency slower
        // connection.
        RaptorAlgorithm rangeRaptor = buildRouterWithFastAndSlowConnection();

        // departure at 8:00 will yield only one fastest connection
        // 08:00 A --> R1 --> 08:05 B
        // 08:05 B --> R2 --> 08:20 K
        OffsetDateTime expectedDepartureTime = EIGHT_AM;
        OffsetDateTime expectedArrivalTime = expectedDepartureTime.plusMinutes(20);

        // however since spawning at 08:15 (first range checked) will find following best solution, this test must
        // ensure that this connection is not returned as it is not pareto optimal.
        // 08:15 A --> R1 --> 08:40 F
        // 08:40 F --> R4 --> 08:45 P
        // 08:45 P --> R3 --> 09:00 K
        List<Connection> connections = RaptorRouterTestHelpers.routeEarliestArrival(rangeRaptor, STOP_A, STOP_K,
                EIGHT_AM);
        assertEquals(1, connections.size());
        RangeRaptorHelpers.assertConnection(connections.getFirst(), expectedDepartureTime, expectedArrivalTime, 2,
                STOP_A, STOP_K);
    }

    @Test
    void ensureIsolinesTakeTheBestLabelOfAllRounds() {
        // the isoline counterpart of ensureParetoOptimalConnections: spawning at 08:15 reaches K in round 3 at 09:00,
        // spawning at 08:00 then reaches K in round 2 at 08:20, and round 3 keeps its label of 09:00, since it cannot
        // improve on 08:20. The isoline must report the best label of all rounds, not the one of the highest round.
        Map<String, Connection> isolines = RaptorRouterTestHelpers.getIsoLines(buildRouterWithFastAndSlowConnection(),
                Map.of(STOP_A, EIGHT_AM));

        RangeRaptorHelpers.assertConnection(isolines.get(STOP_K), EIGHT_AM, EIGHT_AM.plusMinutes(20), 2, STOP_A,
                STOP_K);
    }

    /**
     * The range can be set per query, which takes precedence over the globally configured default range of the router.
     * This is used by the service layer to derive the range from the time window of a query.
     */
    @Test
    void applyQueryScopedRangeOverGlobalDefault(RaptorRouterTestBuilder builder) {
        int headwayRoute1 = 15;
        int headwayRoute2 = 30;
        int offsetRoute2 = 15;

        // the router is configured without a range, so Range-RAPTOR is globally disabled
        RaptorAlgorithm raptor = builder.withAddRoute1_AG(RaptorRouterTestBuilder.DEFAULT_OFFSET, headwayRoute1,
                        RaptorRouterTestBuilder.DEFAULT_TIME_BETWEEN_STOPS, RaptorRouterTestBuilder.DEFAULT_DWELL_TIME)
                .withAddRoute2_HL(offsetRoute2, headwayRoute2, RaptorRouterTestBuilder.DEFAULT_TIME_BETWEEN_STOPS,
                        RaptorRouterTestBuilder.DEFAULT_DWELL_TIME)
                .withSameStopTransferTime(0)
                .withRaptorRange(-1)
                .withMaxDaysToScan(1)
                .build();

        OffsetDateTime expectedArrivalTime = EIGHT_AM.plusMinutes(
                15 + 2 * RaptorRouterTestBuilder.DEFAULT_TIME_BETWEEN_STOPS + RaptorRouterTestBuilder.DEFAULT_DWELL_TIME);

        // without a query range the global default applies, which yields the departure at 08:00 and therefore an
        // unnecessary idle time of 15 minutes at the transfer stop
        List<Connection> connections = RaptorRouterTestHelpers.routeEarliestArrival(raptor, STOP_A, STOP_I, EIGHT_AM);
        assertEquals(1, connections.size());
        RangeRaptorHelpers.assertConnection(connections.getFirst(), EIGHT_AM, expectedArrivalTime);

        // a query scoped range enables Range-RAPTOR for this query only and finds the later departure at 08:15, which
        // arrives at the same time and hence has a shorter travel time
        QueryConfig queryConfig = QueryConfig.builder().raptorRange(1800).build();
        connections = RaptorRouterTestHelpers.routeEarliestArrival(raptor, STOP_A, STOP_I, EIGHT_AM, queryConfig);
        assertEquals(1, connections.size());
        RangeRaptorHelpers.assertConnection(connections.getFirst(), EIGHT_AM.plusMinutes(15), expectedArrivalTime);
    }

    @Test
    void findDepartureConnectionsOfRouteNotRunningOnPreviousDay() {
        // R leaves A at 5:00, but not on the previous day, S leaves A at 6:00 on both days and is slower, and N runs
        // between two other stops until 2:30, so that the previous day is scanned too when departing at 2:00
        RaptorRouterMultiDayTest.RoutePerDayMasker masker = new RaptorRouterMultiDayTest.RoutePerDayMasker();
        masker.deactivateRouteOnDate("R", RaptorRouterTestBuilder.DEFAULT_REFERENCE_DATE.minusDays(1));
        RaptorRouterBuilder builder = RaptorRouter.builder(RaptorConfig.builder()
                .maskProvider(masker)
                .daysToScan(3)
                .stopTimeCacheSize(3)
                .raptorRangeDefault(6 * RaptorRouterTestBuilder.SECONDS_IN_HOUR)
                .build());
        List.of(STOP_A, STOP_B, "X", "Y").forEach(builder::addStop);
        addRouteWithTrips(builder, "R-F", List.of(STOP_A, STOP_B), List.of(List.of(5 * 60, 5 * 60 + 30)));
        addRouteWithTrips(builder, "S-F", List.of(STOP_A, STOP_B), List.of(List.of(6 * 60, 7 * 60)));
        addRouteWithTrips(builder, "N-F", List.of("X", "Y"), List.of(List.of(25 * 60, 26 * 60 + 30)));
        RaptorAlgorithm rangeRaptor = builder.build();

        // the range offsets are those of both routes leaving A, the earliest of R
        List<Connection> connections = RaptorRouterTestHelpers.routeEarliestArrival(rangeRaptor, STOP_A, STOP_B,
                START_OF_DAY.plusHours(2));

        assertEquals(1, connections.size());
        RangeRaptorHelpers.assertConnection(connections.getFirst(), START_OF_DAY.plusHours(5),
                START_OF_DAY.plusHours(5).plusMinutes(30), 1, STOP_A, STOP_B);
    }

    @Test
    void findLatestDepartureOfTripOvertakingEarlierTripOfSameRoute() {
        // the first trip of R leaves A at 8:00, the second five minutes later, overtakes it at B and arrives at C at
        // the same time: RAPTOR scans the trips as two routes, but their departures are the range offsets of one
        RaptorRouterBuilder builder = RaptorRouter.builder(RaptorConfig.builder().raptorRangeDefault(600).build());
        List.of(STOP_A, STOP_B, STOP_C).forEach(builder::addStop);
        addRouteWithTrips(builder, "R", List.of(STOP_A, STOP_B, STOP_C),
                List.of(List.of(8 * 60, 8 * 60 + 20, 8 * 60 + 40), List.of(8 * 60 + 5, 8 * 60 + 15, 8 * 60 + 40)));
        RaptorAlgorithm rangeRaptor = builder.build();

        // range raptor reports the latest departure of the earliest arrival
        List<Connection> connections = RaptorRouterTestHelpers.routeEarliestArrival(rangeRaptor, STOP_A, STOP_C,
                EIGHT_AM);

        assertEquals(1, connections.size());
        RangeRaptorHelpers.assertConnection(connections.getFirst(), EIGHT_AM.plusMinutes(5), EIGHT_AM.plusMinutes(40),
                1, STOP_A, STOP_C);
    }

    static class RangeRaptorHelpers {

        static void assertConnection(Connection connection, OffsetDateTime expectedDepartureTime,
                                     OffsetDateTime expectedArrivalTime) {
            assertConnection(connection, expectedDepartureTime, expectedArrivalTime, 2, STOP_A, STOP_I);
        }

        static void assertConnection(Connection connection, OffsetDateTime expectedDepartureTime,
                                     OffsetDateTime expectedArrivalTime, int numLegs, String fromStopId,
                                     String toStopId) {
            assertEquals(numLegs, connection.getLegs().size());
            assertEquals(expectedDepartureTime, connection.getDepartureTime());
            assertEquals(expectedArrivalTime, connection.getArrivalTime());
            assertEquals(fromStopId, connection.getFromStopId());
            assertEquals(toStopId, connection.getToStopId());
        }

    }

}
