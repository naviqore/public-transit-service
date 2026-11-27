package org.naviqore.raptor.router;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.naviqore.raptor.Connection;
import org.naviqore.raptor.QueryConfig;
import org.naviqore.raptor.RaptorAlgorithm;
import org.naviqore.raptor.TimeType;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for the range query of the raptor algorithm (rRAPTOR): the connections that leave in a time window (arrive
 * in it, for an arrival query) and that no other connection in the window beats in its departure, its arrival and its
 * number of trips.
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
    private static final OffsetDateTime NINE_AM = EIGHT_AM.plusHours(1);

    private static final Duration TEN_MINUTES = Duration.ofMinutes(10);
    private static final Duration FIFTEEN_MINUTES = Duration.ofMinutes(15);
    private static final Duration HALF_AN_HOUR = Duration.ofMinutes(30);
    private static final Duration HOUR = Duration.ofHours(1);

    /**
     * Builds a router with a low frequency fast connection and a high frequency slower connection from A to K.
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
                .withMaxDaysToScan(1)
                .build();
    }

    /**
     * The routes of the test schedule at other headways, starting at other minutes and taking other times between
     * stops, so that the best connections depend on when one leaves, with walks from the source, between the routes and
     * to another route. Trips start only in the hour from 08:00 to 09:00, so that no connection leaves the source after
     * the window (arrives at it before the window, routing backward), and the queries at every minute of the window
     * find the same connections as the range query.
     */
    static RaptorAlgorithm buildIrregularSchedule(RaptorRouterTestBuilder builder) {
        return builder.withServiceDayRange(8, 9)
                .withAddRoute1_AG(0, 15, 5, 1)
                .withAddRoute2_HL(3, 20, 4, 1)
                .withAddRoute3_MQ(7, 12, 6, 1)
                .withAddRoute4_RS(11, 25, 3, 1)
                .withAddTransfer("A", "H", 3)
                .withAddTransfer("N", "D", 4)
                .withAddTransfer("L", "R", 2)
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

    /**
     * Routes the connections from one stop to another over a time window that starts at a departure time, or for an
     * arrival query, ends at an arrival time.
     */
    static List<Connection> routeOverWindow(RaptorAlgorithm raptor, String fromStopId, String toStopId,
                                            OffsetDateTime time, TimeType timeType, Duration timeWindow) {
        return timeType == TimeType.DEPARTURE ? raptor.connectionRangeQuery(Map.of(fromStopId, time),
                Map.of(toStopId, 0), timeType, timeWindow, QueryConfig.defaults()) : raptor.connectionRangeQuery(
                Map.of(toStopId, time), Map.of(fromStopId, 0), timeType, timeWindow, QueryConfig.defaults());
    }

    /**
     * Routes the connections from one stop to another at a time, as a query without a window does.
     */
    private static List<Connection> route(RaptorAlgorithm raptor, String fromStopId, String toStopId,
                                          OffsetDateTime time, TimeType timeType) {
        return timeType == TimeType.DEPARTURE ? RaptorRouterTestHelpers.routeEarliestArrival(raptor, fromStopId,
                toStopId, time) : RaptorRouterTestHelpers.routeLatestDeparture(raptor, fromStopId, toStopId, time);
    }

    private static Set<Criteria> paretoSet(Collection<Criteria> criteria) {
        Set<Criteria> paretoSet = new HashSet<>();
        for (Criteria candidate : criteria) {
            if (criteria.stream().noneMatch(other -> other.beats(candidate))) {
                paretoSet.add(candidate);
            }
        }

        return paretoSet;
    }

    @Test
    void findDepartureConnections(RaptorRouterTestBuilder builder) {
        int headway_route_1 = 15;
        int headway_route_2 = 30;

        int offset_route_2 = 15;

        RaptorAlgorithm raptor = builder.withAddRoute1_AG(RaptorRouterTestBuilder.DEFAULT_OFFSET, headway_route_1,
                        RaptorRouterTestBuilder.DEFAULT_TIME_BETWEEN_STOPS, RaptorRouterTestBuilder.DEFAULT_DWELL_TIME)
                .withAddRoute2_HL(offset_route_2, headway_route_2, RaptorRouterTestBuilder.DEFAULT_TIME_BETWEEN_STOPS,
                        RaptorRouterTestBuilder.DEFAULT_DWELL_TIME)
                .withSameStopTransferTime(0)
                .withMaxDaysToScan(1)
                .build();

        // Find connections from "A" to "I" departing from 8:00 am for 30 minutes.
        // Route 1 leaves "A" at 8:00, 8:15, 8:30, 8:45... and arrives at "B" 5 minutes later.
        // Route 2 leaves "H" at 8:15, 8:45, 9:15... and arrives at "B" 5 minutes later and continues on to "I" one
        // minute after arriving, the trip from "B" to "I" requires 5 minutes. Therefore, the shortest possible travel
        // time is 11 minutes. Since the headway of route 1 is 15 minutes and the headway of route 2 is 30 minutes,
        // leaving at 8:15 arrives as early as leaving at 8:00, and the range query reports only the connection at
        // 8:15. A query without a window reports the connection at 8:00.
        List<Connection> connections = routeOverWindow(raptor, STOP_A, STOP_I, EIGHT_AM, TimeType.DEPARTURE,
                HALF_AN_HOUR);

        OffsetDateTime expectedDepartureTime = EIGHT_AM.plusMinutes(15);
        OffsetDateTime expectedArrivalTime = expectedDepartureTime.plusMinutes(
                2 * RaptorRouterTestBuilder.DEFAULT_TIME_BETWEEN_STOPS + RaptorRouterTestBuilder.DEFAULT_DWELL_TIME);

        assertEquals(1, connections.size());
        RangeRaptorHelpers.assertConnection(connections.getFirst(), expectedDepartureTime, expectedArrivalTime);

        // confirm that a query without a window suggests the connection at 8:00
        connections = RaptorRouterTestHelpers.routeEarliestArrival(raptor, STOP_A, STOP_I, EIGHT_AM);
        expectedDepartureTime = EIGHT_AM;
        // expected arrival time is the same as with the range query (only 15 minute idle time and transfer stop)
        assertEquals(1, connections.size());
        RangeRaptorHelpers.assertConnection(connections.getFirst(), expectedDepartureTime, expectedArrivalTime);

        // a range query over 10 minutes (shorter than the 15-minute headway of route 1) leaves only at 8:00, which the
        // connection at 8:15 beats: no connection leaving in the window is worth taking
        connections = routeOverWindow(raptor, STOP_A, STOP_I, EIGHT_AM, TimeType.DEPARTURE, TEN_MINUTES);
        assertTrue(connections.isEmpty());
    }

    @Test
    void findArrivalConnections(RaptorRouterTestBuilder builder) {
        int headway_route_1 = 30;
        int headway_route_2 = 15;

        int offset_route_1 = 15;

        // Find connections from "A" to "I" arriving from 8:11 am until 8:41 am.
        // Route 1 leaves "A" at 7:45, 8:15, 8:45 ... and arrives at "B" 5 minutes later.
        // Route 2 leaves "H" at 8:00, 8:15, 8:30, 8:45 ... and arrives at "B" 5 minutes later and continues on to "I"
        // one minute after arriving, the trip from "B" to "I" requires 5 minutes. Therefore, the shortest possible
        // travel time is 11 minutes. Since the headway of route 1 is 30 minutes and the headway of route 2 is 15
        // minutes, the connection arriving at 8:26 leaves as late as the one arriving at 8:41, and the range query
        // reports only the connection arriving at 8:26. A query without a window reports the connection arriving at
        // 8:41.
        OffsetDateTime arrivalTime = EIGHT_AM.plusMinutes(41);

        RaptorAlgorithm raptor = builder.withAddRoute1_AG(offset_route_1, headway_route_1,
                        RaptorRouterTestBuilder.DEFAULT_TIME_BETWEEN_STOPS, RaptorRouterTestBuilder.DEFAULT_DWELL_TIME)
                .withAddRoute2_HL(RaptorRouterTestBuilder.DEFAULT_OFFSET, headway_route_2,
                        RaptorRouterTestBuilder.DEFAULT_TIME_BETWEEN_STOPS, RaptorRouterTestBuilder.DEFAULT_DWELL_TIME)
                .withSameStopTransferTime(0)
                .withMaxDaysToScan(1)
                .build();

        List<Connection> connections = routeOverWindow(raptor, STOP_A, STOP_I, arrivalTime, TimeType.ARRIVAL,
                HALF_AN_HOUR);
        OffsetDateTime expectedArrivalTime = EIGHT_AM.plusMinutes(26);
        OffsetDateTime expectedDepartureTime = expectedArrivalTime.minusMinutes(
                2 * RaptorRouterTestBuilder.DEFAULT_TIME_BETWEEN_STOPS + RaptorRouterTestBuilder.DEFAULT_DWELL_TIME);

        assertEquals(1, connections.size());
        RangeRaptorHelpers.assertConnection(connections.getFirst(), expectedDepartureTime, expectedArrivalTime);

        // confirm that a query without a window suggests the connection arriving at 8:41
        connections = RaptorRouterTestHelpers.routeLatestDeparture(raptor, STOP_A, STOP_I, arrivalTime);
        expectedArrivalTime = EIGHT_AM.plusMinutes(41);
        // expected departure time is the same as with the range query (only 15 minute idle time and transfer stop)
        assertEquals(1, connections.size());
        RangeRaptorHelpers.assertConnection(connections.getFirst(), expectedDepartureTime, expectedArrivalTime);

        // a range query over 10 minutes (shorter than the 15-minute headway of route 2) arrives only at 8:41, which the
        // connection arriving at 8:26 beats: no connection arriving in the window is worth taking
        connections = routeOverWindow(raptor, STOP_A, STOP_I, arrivalTime, TimeType.ARRIVAL, TEN_MINUTES);
        assertTrue(connections.isEmpty());
    }

    @Test
    void findDepartureConnections_everyDepartureOfTheWindow(RaptorRouterTestBuilder builder) {
        RaptorAlgorithm raptor = builder.withAddRoute1_AG()
                .withAddRoute2_HL()
                .withSameStopTransferTime(0)
                .withMaxDaysToScan(1)
                .build();

        // Find connections from "A" to "I" departing from 8:00 am for 30 minutes.
        // Route 1 leaves "A" at 8:00, 8:15... and arrives at "B" 5 minutes later.
        // Route 2 leaves "H" at 8:00, 8:15... and arrives at "B" 5 minutes later and continues on to "I" one minute
        // after arriving, the trip from "B" to "I" requires 5 minutes. Since both routes have the same headway, each
        // departure in the window arrives earlier than the next one: the range query reports both, the first of which
        // is the connection of a query without a window.
        List<Connection> connections = routeOverWindow(raptor, STOP_A, STOP_I, EIGHT_AM, TimeType.DEPARTURE,
                HALF_AN_HOUR);

        int betweenStops = RaptorRouterTestBuilder.DEFAULT_TIME_BETWEEN_STOPS;
        int travelTime = 2 * betweenStops + RaptorRouterTestBuilder.DEFAULT_DWELL_TIME;
        assertEquals(2, connections.size());
        RangeRaptorHelpers.assertConnection(connections.get(0), EIGHT_AM, EIGHT_AM.plusMinutes(travelTime));
        RangeRaptorHelpers.assertConnection(connections.get(1), EIGHT_AM.plusMinutes(15),
                EIGHT_AM.plusMinutes(15 + travelTime));

        // check that the query without a window finds the first
        connections = RaptorRouterTestHelpers.routeEarliestArrival(raptor, STOP_A, STOP_I, EIGHT_AM);

        assertEquals(1, connections.size());
        RangeRaptorHelpers.assertConnection(connections.getFirst(), EIGHT_AM, EIGHT_AM.plusMinutes(travelTime));
    }

    @Test
    void findArrivalConnections_everyArrivalOfTheWindow(RaptorRouterTestBuilder builder) {
        OffsetDateTime arrivalTime = EIGHT_AM.plusMinutes(11);

        RaptorAlgorithm raptor = builder.withAddRoute1_AG()
                .withAddRoute2_HL()
                .withSameStopTransferTime(0)
                .withMaxDaysToScan(1)
                .build();

        // Find connections from "A" to "I" arriving from 7:41 until 8:11 am: those leaving "A" at 7:45 and 8:00, which
        // a query without a window finds at 8:00.
        List<Connection> rangeConnections = routeOverWindow(raptor, STOP_A, STOP_I, arrivalTime, TimeType.ARRIVAL,
                HALF_AN_HOUR);
        List<Connection> connections = RaptorRouterTestHelpers.routeLatestDeparture(raptor, STOP_A, STOP_I,
                arrivalTime);

        assertEquals(2, rangeConnections.size());
        RangeRaptorHelpers.assertConnection(rangeConnections.get(0), EIGHT_AM.minusMinutes(15),
                arrivalTime.minusMinutes(15));
        RangeRaptorHelpers.assertConnection(rangeConnections.get(1), EIGHT_AM, arrivalTime);

        assertEquals(1, connections.size());
        RangeRaptorHelpers.assertConnection(connections.getFirst(), EIGHT_AM, arrivalTime);
    }

    @Test
    void findDepartureConnections_withSourceTransferFirst() {
        int headway_route_1 = 15;
        int headway_route_2 = 30;

        RaptorAlgorithm raptor = new RaptorRouterTestBuilder().withAddRoute1_AG(RaptorRouterTestBuilder.DEFAULT_OFFSET,
                        headway_route_1, RaptorRouterTestBuilder.DEFAULT_TIME_BETWEEN_STOPS,
                        RaptorRouterTestBuilder.DEFAULT_DWELL_TIME)
                .withAddRoute2_HL(RaptorRouterTestBuilder.DEFAULT_OFFSET, headway_route_2,
                        RaptorRouterTestBuilder.DEFAULT_TIME_BETWEEN_STOPS, RaptorRouterTestBuilder.DEFAULT_DWELL_TIME)
                .withAddRoute3_MQ() // to create stop N
                .withAddTransfer(STOP_N, STOP_A, 15)
                .withSameStopTransferTime(0)
                .withMaxDaysToScan(1)
                .build();

        // Find connections from "N" to "I" departing from 8:00 am for 30 minutes.
        // It takes 15 minutes to walk from "N" to "A", thus earliest arrival at "A" is 8:15.
        // Route 1 leaves "A" at 8:00, 8:15, 8:30, 8:45... and arrives at "B" 5 minutes later.
        // Route 2 leaves "H" at 8:00, 8:30, 9:00... and arrives at "B" 5 minutes later and continues on to "I" one
        // minute after arriving, the trip from "B" to "I" requires 5 minutes. Therefore, the shortest possible travel
        // is 26 minutes, but since Route 2 only arrives at "B" at 8:35, the connection leaving "A" at 8:15 does not
        // make sense and the connection leaving at 8:30 is the best option --> the connection leaving earliest in the
        // window leaves "N" at 8:15.
        List<Connection> connections = routeOverWindow(raptor, STOP_N, STOP_I, EIGHT_AM, TimeType.DEPARTURE,
                HALF_AN_HOUR);

        OffsetDateTime expectedDepartureTime = EIGHT_AM.plusMinutes(15);
        OffsetDateTime expectedArrivalTime = expectedDepartureTime.plusMinutes(26);

        // the connection departing first is the one including the transfer
        RangeRaptorHelpers.assertConnection(connections.getFirst(), expectedDepartureTime, expectedArrivalTime, 3,
                STOP_N, STOP_I);
    }

    @Test
    void findArrivalConnections_withSourceTransferFirst() {
        int headway_route_1 = 30;
        int headway_route_2 = 15;

        RaptorAlgorithm raptor = new RaptorRouterTestBuilder().withAddRoute1_AG(RaptorRouterTestBuilder.DEFAULT_OFFSET,
                        headway_route_1, RaptorRouterTestBuilder.DEFAULT_TIME_BETWEEN_STOPS,
                        RaptorRouterTestBuilder.DEFAULT_DWELL_TIME)
                .withAddRoute2_HL(RaptorRouterTestBuilder.DEFAULT_OFFSET, headway_route_2,
                        RaptorRouterTestBuilder.DEFAULT_TIME_BETWEEN_STOPS, RaptorRouterTestBuilder.DEFAULT_DWELL_TIME)
                .withAddRoute3_MQ() // to create stop N
                .withAddTransfer(STOP_N, STOP_I, 15)
                .withSameStopTransferTime(0)
                .withMaxDaysToScan(1)
                .build();

        // Find connections from "A" to "N" arriving from 8:11 until 8:41 am.
        // It takes 15 minutes to walk from "I" to "N", thus the latest departure at "N" is 8:26.
        // Route 1 leaves "A" at 8:00, 8:30, 9:00... and arrives at "B" 5 minutes later.
        // Route 2 leaves "H" at 8:00, 8:15, 8:30... and arrives at "B" 5 minutes later and continues on to "I" one
        // minute after arriving, the trip from "B" to "I" requires 5 minutes. Therefore, the shortest possible travel
        // is 26 minutes, but since Route 1 only arrives at "B" at 8:05, the connection leaving "B" at 8:06 to "I" makes
        // more sense than waiting for the 8:21 Route 2 connection, thus the arrival time at "N" should be 8:26.
        List<Connection> connections = routeOverWindow(raptor, STOP_A, STOP_N, EIGHT_AM.plusMinutes(41),
                TimeType.ARRIVAL, HALF_AN_HOUR);

        OffsetDateTime expectedDepartureTime = EIGHT_AM;
        OffsetDateTime expectedArrivalTime = expectedDepartureTime.plusMinutes(26);

        // the connection departing first is the one including the transfer
        RangeRaptorHelpers.assertConnection(connections.getFirst(), expectedDepartureTime, expectedArrivalTime, 3,
                STOP_A, STOP_N);
    }

    @Test
    void ensureParetoOptimalConnections() {
        // this test is based on a previous bug, where the range query returned connections which were not pareto
        // optimal (later arrival time and more rounds). This is owed to the fact that the range query runs passes at
        // different times and potentially finds earliest arrival connections for the given time with more rounds than
        // the final best arrival time.
        // this test reproduces this case by introducing a low frequency fast connection and a high frequency slower
        // connection.
        RaptorAlgorithm raptor = buildRouterWithFastAndSlowConnection();

        // a window of 15 minutes, which ends before the departure at 08:15, yields only one fastest connection
        // 08:00 A --> R1 --> 08:05 B
        // 08:05 B --> R2 --> 08:20 K
        List<Connection> connections = routeOverWindow(raptor, STOP_A, STOP_K, EIGHT_AM, TimeType.DEPARTURE,
                FIFTEEN_MINUTES);
        assertEquals(1, connections.size());
        RangeRaptorHelpers.assertConnection(connections.getFirst(), EIGHT_AM, EIGHT_AM.plusMinutes(20), 2, STOP_A,
                STOP_K);

        // a window of 30 minutes leaves at 08:15 too, which arrives later; no connection of the window beats another
        // 08:15 A --> R1 --> 08:40 F
        // 08:40 F --> R4 --> 08:45 P
        // 08:45 P --> R3 --> 09:00 K
        connections = routeOverWindow(raptor, STOP_A, STOP_K, EIGHT_AM, TimeType.DEPARTURE, HALF_AN_HOUR);
        List<Criteria> criteria = connections.stream().map(Criteria::of).toList();
        assertEquals(paretoSet(criteria), new HashSet<>(criteria));
        assertTrue(criteria.contains(new Criteria(EIGHT_AM, EIGHT_AM.plusMinutes(20), 2)));
        assertTrue(criteria.contains(new Criteria(EIGHT_AM.plusMinutes(15), NINE_AM, 3)));
    }

    @Test
    void keepTheConnectionWithFewerTrips_whenLaterDeparturesWithMoreTripsArriveEarlier(
            RaptorRouterTestBuilder builder) {
        // a direct route leaves A hourly and reaches G after 70 minutes; with a transfer at C, a trip every 5 minutes
        // to C and every 10 minutes on to G arrives in 40 minutes. Each pass of the range query departs earlier than
        // the one before, and the direct connection at 8:00, of fewer trips, must not be pruned by the arrivals of the
        // later passes with a transfer, which are earlier but take more trips: the best time of a round counts only the
        // connections of as many trips or fewer.
        RaptorAlgorithm raptor = builder.withAddRoute("D", RaptorRouterTestBuilder.DEFAULT_ZONE_ID, List.of("A", "G"),
                        0, 60, 70, 1)
                .withAddRoute("R1", RaptorRouterTestBuilder.DEFAULT_ZONE_ID, List.of("A", "C"), 0, 5, 10, 1)
                .withAddRoute("R2", RaptorRouterTestBuilder.DEFAULT_ZONE_ID, List.of("C", "G"), 0, 10, 20, 1)
                .build();

        List<Criteria> connections = routeOverWindow(raptor, STOP_A, "G", EIGHT_AM, TimeType.DEPARTURE,
                HALF_AN_HOUR).stream().map(Criteria::of).toList();

        assertEquals(List.of(new Criteria(EIGHT_AM, EIGHT_AM.plusMinutes(70), 1),
                new Criteria(EIGHT_AM.plusMinutes(5), EIGHT_AM.plusMinutes(40), 2),
                new Criteria(EIGHT_AM.plusMinutes(15), EIGHT_AM.plusMinutes(50), 2),
                new Criteria(EIGHT_AM.plusMinutes(25), NINE_AM, 2)), connections);
    }

    @Test
    void ensureIsolinesTakeTheBestLabelOfAllRounds() {
        // the isoline counterpart of ensureParetoOptimalConnections: K is reached in round 2 at 08:20, and the isoline
        // must report it, also over a window in which leaving later takes three trips
        RaptorAlgorithm raptor = buildRouterWithFastAndSlowConnection();
        Map<String, Connection> isolines = RaptorRouterTestHelpers.getIsoLines(raptor, Map.of(STOP_A, EIGHT_AM));

        RangeRaptorHelpers.assertConnection(isolines.get(STOP_K), EIGHT_AM, EIGHT_AM.plusMinutes(20), 2, STOP_A,
                STOP_K);

        isolines = raptor.isolineRangeQuery(Map.of(STOP_A, EIGHT_AM), TimeType.DEPARTURE, HALF_AN_HOUR,
                QueryConfig.defaults());
        RangeRaptorHelpers.assertConnection(isolines.get(STOP_K), EIGHT_AM, EIGHT_AM.plusMinutes(20), 2, STOP_A,
                STOP_K);
    }

    @Test
    void findDepartureConnectionsOfRouteNotRunningOnPreviousDay() {
        // R leaves A at 5:00, but not on the previous day, S leaves A at 6:00 on both days and is slower, and N runs
        // between two other stops until 2:30, so that the previous day is scanned too when departing at 2:00
        RaptorRouterMultiDayTest.RoutePerDayMasker masker = new RaptorRouterMultiDayTest.RoutePerDayMasker();
        masker.deactivateRouteOnDate("R", RaptorRouterTestBuilder.DEFAULT_REFERENCE_DATE.minusDays(1));
        RaptorRouterBuilder builder = RaptorRouter.builder(
                RaptorConfig.builder().maskProvider(masker).daysToScan(3).stopTimeCacheSize(3).build());
        List.of(STOP_A, STOP_B, "X", "Y").forEach(builder::addStop);
        addRouteWithTrips(builder, "R-F", List.of(STOP_A, STOP_B), List.of(List.of(5 * 60, 5 * 60 + 30)));
        addRouteWithTrips(builder, "S-F", List.of(STOP_A, STOP_B), List.of(List.of(6 * 60, 7 * 60)));
        addRouteWithTrips(builder, "N-F", List.of("X", "Y"), List.of(List.of(25 * 60, 26 * 60 + 30)));
        RaptorAlgorithm raptor = builder.build();

        // the departures of both routes leaving A are passes of the range query, the earliest of R
        List<Connection> connections = routeOverWindow(raptor, STOP_A, STOP_B, START_OF_DAY.plusHours(2),
                TimeType.DEPARTURE, Duration.ofHours(6));

        assertEquals(2, connections.size());
        RangeRaptorHelpers.assertConnection(connections.get(0), START_OF_DAY.plusHours(5),
                START_OF_DAY.plusHours(5).plusMinutes(30), 1, STOP_A, STOP_B);
        RangeRaptorHelpers.assertConnection(connections.get(1), START_OF_DAY.plusHours(6), START_OF_DAY.plusHours(7), 1,
                STOP_A, STOP_B);
    }

    @Test
    void findLatestDepartureOfTripOvertakingEarlierTripOfSameRoute() {
        // the first trip of R leaves A at 8:00, the second five minutes later, overtakes it at B and arrives at C at
        // the same time: RAPTOR scans the trips as two routes, but their departures are passes of one range query
        RaptorRouterBuilder builder = RaptorRouter.builder(RaptorConfig.defaults());
        List.of(STOP_A, STOP_B, STOP_C).forEach(builder::addStop);
        addRouteWithTrips(builder, "R", List.of(STOP_A, STOP_B, STOP_C),
                List.of(List.of(8 * 60, 8 * 60 + 20, 8 * 60 + 40), List.of(8 * 60 + 5, 8 * 60 + 15, 8 * 60 + 40)));
        RaptorAlgorithm raptor = builder.build();

        // the range query reports the latest departure of the earliest arrival
        List<Connection> connections = routeOverWindow(raptor, STOP_A, STOP_C, EIGHT_AM, TimeType.DEPARTURE,
                TEN_MINUTES);

        assertEquals(1, connections.size());
        RangeRaptorHelpers.assertConnection(connections.getFirst(), EIGHT_AM.plusMinutes(5), EIGHT_AM.plusMinutes(40),
                1, STOP_A, STOP_C);
    }

    @Test
    void connectionBeatenByOneLeavingAfterTheWindow_isNotReported(RaptorRouterTestBuilder builder) {
        // route 1 leaves A at 8:00 and reaches G at 8:35; a direct route leaves A at 8:12 and reaches G at 8:22, which
        // beats the connection at 8:00 but leaves after a window of 10 minutes: none is reported, as the queries at
        // 8:00 and at every minute of the window find the one at 8:12
        RaptorAlgorithm raptor = builder.withAddRoute1_AG()
                .withAddRoute("X", RaptorRouterTestBuilder.DEFAULT_ZONE_ID, List.of("A", "G"), 12, 15, 10, 1)
                .build();

        List<Connection> connections = routeOverWindow(raptor, STOP_A, "G", EIGHT_AM, TimeType.DEPARTURE, TEN_MINUTES);
        assertTrue(connections.isEmpty());

        connections = routeOverWindow(raptor, STOP_A, "G", EIGHT_AM, TimeType.DEPARTURE, FIFTEEN_MINUTES);
        assertEquals(1, connections.size());
        RangeRaptorHelpers.assertConnection(connections.getFirst(), EIGHT_AM.plusMinutes(12), EIGHT_AM.plusMinutes(22),
                1, STOP_A, "G");
    }

    @Test
    void connectionTiedWithOneOutsideTheWindow_isNotReported() {
        // X leaves A at 8:00 and Y at 8:15, both reach G at 9:00: Y, leaving after a window of 10 minutes, beats X; and
        // routing backward, P and Q leave A at 8:30, P reaches G at 9:25, in a window of 10 minutes before 9:30, and Q
        // at 9:10, before the window, which beats P
        RaptorRouterBuilder builder = RaptorRouter.builder(RaptorConfig.defaults());
        List.of(STOP_A, "G").forEach(builder::addStop);
        addRouteWithTrips(builder, "X", List.of(STOP_A, "G"), List.of(List.of(8 * 60, 9 * 60)));
        addRouteWithTrips(builder, "Y", List.of(STOP_A, "G"), List.of(List.of(8 * 60 + 15, 9 * 60)));
        addRouteWithTrips(builder, "P", List.of(STOP_A, "G"), List.of(List.of(8 * 60 + 30, 9 * 60 + 25)));
        addRouteWithTrips(builder, "Q", List.of(STOP_A, "G"), List.of(List.of(8 * 60 + 30, 9 * 60 + 10)));
        RaptorAlgorithm raptor = builder.build();

        assertTrue(routeOverWindow(raptor, STOP_A, "G", EIGHT_AM, TimeType.DEPARTURE, TEN_MINUTES).isEmpty());
        assertTrue(
                routeOverWindow(raptor, STOP_A, "G", NINE_AM.plusMinutes(30), TimeType.ARRIVAL, TEN_MINUTES).isEmpty());
    }

    @Test
    void connectionsOfConsecutiveWindows_areThoseOfTheirUnion(RaptorRouterTestBuilder builder) {
        RaptorAlgorithm raptor = buildIrregularSchedule(builder);

        for (TimeType timeType : TimeType.values()) {
            for (String target : List.of("G", "I", "Q", "S")) {
                OffsetDateTime start = timeType == TimeType.DEPARTURE ? EIGHT_AM : NINE_AM;
                OffsetDateTime middle = timeType == TimeType.DEPARTURE ? EIGHT_AM.plusMinutes(
                        30) : NINE_AM.minusMinutes(30);
                Set<Criteria> halves = new HashSet<>();
                for (OffsetDateTime time : List.of(start, middle)) {
                    routeOverWindow(raptor, STOP_A, target, time, timeType, HALF_AN_HOUR).stream()
                            .filter(connection -> time.equals(start) || !connection.getRouteLegs().isEmpty())
                            .map(Criteria::of)
                            .forEach(halves::add);
                }
                Set<Criteria> whole = new HashSet<>(
                        routeOverWindow(raptor, STOP_A, target, start, timeType, HOUR).stream()
                                .map(Criteria::of)
                                .toList());

                assertEquals(whole, halves, timeType + " to " + target);
            }
        }
    }

    @Test
    void connectionOnFootAlone_isReportedOnceAtTheStartOfTheWindow(RaptorRouterTestBuilder builder) {
        // A and H are a walk of 3 minutes apart, and a walk can leave at any time
        RaptorAlgorithm raptor = buildIrregularSchedule(builder);

        List<Connection> connections = routeOverWindow(raptor, STOP_A, "H", EIGHT_AM, TimeType.DEPARTURE, HOUR);

        List<Connection> walks = connections.stream()
                .filter(connection -> connection.getRouteLegs().isEmpty())
                .toList();
        assertEquals(1, walks.size());
        assertEquals(EIGHT_AM, walks.getFirst().getDepartureTime());
    }

    @ParameterizedTest
    @EnumSource(TimeType.class)
    void rangeQueryFindsTheBestOfTheQueriesEveryMinute(TimeType timeType, RaptorRouterTestBuilder builder) {
        RaptorAlgorithm raptor = buildIrregularSchedule(builder);
        OffsetDateTime windowEdge = timeType == TimeType.DEPARTURE ? EIGHT_AM : NINE_AM;

        // the trips and walks of the schedule are whole minutes, so connections leave and arrive at whole minutes; a
        // connection on foot alone is compared at the start of the window (end, routing backward), where the range
        // query reports it
        for (String target : List.of("B", "D", "G", "I", "L", "N", "O", "Q", "R", "S")) {
            List<Criteria> found = new ArrayList<>();
            for (int minute = 0; minute < HOUR.toMinutes(); minute++) {
                OffsetDateTime time = timeType == TimeType.DEPARTURE ? EIGHT_AM.plusMinutes(
                        minute) : NINE_AM.minusMinutes(minute);
                boolean atWindowEdge = minute == 0;
                route(raptor, STOP_A, target, time, timeType).stream()
                        .filter(connection -> atWindowEdge || !connection.getRouteLegs().isEmpty())
                        .map(Criteria::of)
                        .forEach(found::add);
            }

            List<Criteria> rangeQuery = routeOverWindow(raptor, STOP_A, target, windowEdge, timeType, HOUR).stream()
                    .map(Criteria::of)
                    .toList();
            assertEquals(paretoSet(found), new HashSet<>(rangeQuery), "connections to " + target);
            assertEquals(rangeQuery.size(), new HashSet<>(rangeQuery).size(), "each connection once, to " + target);
        }
    }

    @Test
    void windowShorterThanASecond_isRejected(RaptorRouterTestBuilder builder) {
        RaptorAlgorithm raptor = buildIrregularSchedule(builder);

        for (Duration window : List.of(Duration.ZERO, Duration.ofMillis(999), Duration.ofSeconds(-1))) {
            assertThrows(IllegalArgumentException.class,
                    () -> routeOverWindow(raptor, STOP_A, "G", EIGHT_AM, TimeType.DEPARTURE, window));
        }
    }

    /**
     * The criteria a range query compares connections by: departure, arrival and number of trips.
     */
    private record Criteria(OffsetDateTime departure, OffsetDateTime arrival, int trips) {

        static Criteria of(Connection connection) {
            return new Criteria(connection.getDepartureTime(), connection.getArrivalTime(),
                    connection.getRouteLegs().size());
        }

        boolean beats(Criteria other) {
            boolean noWorseInTime = !departure.isBefore(other.departure) && !arrival.isAfter(other.arrival);
            return noWorseInTime && trips <= other.trips && !equals(other);
        }
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
