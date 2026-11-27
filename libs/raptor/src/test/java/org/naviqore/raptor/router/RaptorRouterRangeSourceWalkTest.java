package org.naviqore.raptor.router;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.naviqore.raptor.*;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Over a time window, a search may walk from a source stop in the window to the next stop of a trip that calls at the
 * source stop itself only after the window (routing backward: walk to a source stop from the stop before, where a trip
 * arrives that reaches the source stop only before the window). The connection reported keeps the walk, since riding
 * the trip from the source stop would leave after the window (arrive before it). Without a time window, the ride
 * replaces the walk. Walking between S and B takes ten minutes here.
 */
class RaptorRouterRangeSourceWalkTest {

    private static final OffsetDateTime START_OF_DAY = RaptorRouterTestBuilder.DEFAULT_REFERENCE_DATE.atStartOfDay(
            RaptorRouterTestBuilder.DEFAULT_ZONE_ID).toOffsetDateTime();
    private static final Duration HOUR = Duration.ofHours(1);

    private static OffsetDateTime at(int hour, int minute) {
        return START_OF_DAY.plusHours(hour).plusMinutes(minute);
    }

    /**
     * A router with a route of one trip calling at the given stops the given minutes after the start of the day.
     */
    private static RaptorAlgorithm build(List<String> stops, int... minutes) {
        RaptorRouterBuilder builder = RaptorRouter.builder(RaptorConfig.defaults());
        stops.forEach(builder::addStop);
        builder.addTransfer("S", "B", 600);
        builder.addTransfer("B", "S", 600);
        builder.addRoute("R", RaptorRouterTestBuilder.DEFAULT_ZONE_ID, stops);
        builder.addTrip("R-0", "R");
        for (int i = 0; i < stops.size(); i++) {
            builder.addStopTime("R", "R-0", i, stops.get(i), minutes[i] * 60, minutes[i] * 60);
        }
        return builder.build();
    }

    /**
     * S - B - C, leaving S at 09:03, B at 09:06 and reaching C at 09:15.
     */
    private static RaptorAlgorithm departing() {
        return build(List.of("S", "B", "C"), 9 * 60 + 3, 9 * 60 + 6, 9 * 60 + 15);
    }

    /**
     * X - B - S, leaving X at 07:45, B at 07:54 and reaching S at 07:57.
     */
    private static RaptorAlgorithm arriving() {
        return build(List.of("X", "B", "S"), 7 * 60 + 45, 7 * 60 + 54, 7 * 60 + 57);
    }

    private static void assertLeg(Leg leg, Leg.Type type, String from, OffsetDateTime departure, String to,
                                  OffsetDateTime arrival) {
        assertEquals(type, leg.getType());
        assertEquals(from, leg.getFromStopId());
        assertEquals(departure, leg.getDepartureTime());
        assertEquals(to, leg.getToStopId());
        assertEquals(arrival, leg.getArrivalTime());
    }

    @Test
    void isolinesDeparting_walkToATripLeavingTheSourceAfterTheWindow() {
        // the trip leaves S at 09:03, after the window from 08:00 to 09:00, and B at 09:06, ten minutes' walk away
        Map<String, Connection> isolines = departing().isolineRangeQuery(Map.of("S", at(8, 0)), TimeType.DEPARTURE,
                HOUR, QueryConfig.defaults());

        Connection toC = isolines.get("C");
        assertNotNull(toC);
        assertEquals(2, toC.getLegs().size());
        assertLeg(toC.getLegs().get(0), Leg.Type.WALK_TRANSFER, "S", at(8, 56), "B", at(9, 6));
        assertLeg(toC.getLegs().get(1), Leg.Type.ROUTE, "B", at(9, 6), "C", at(9, 15));
    }

    @Test
    void isolinesArriving_walkFromATripReachingTheSourceBeforeTheWindow() {
        // the trip reaches S at 07:57, before the window from 08:00 to 09:00, and B at 07:54, ten minutes' walk away
        Map<String, Connection> isolines = arriving().isolineRangeQuery(Map.of("S", at(9, 0)), TimeType.ARRIVAL, HOUR,
                QueryConfig.defaults());

        Connection fromX = isolines.get("X");
        assertNotNull(fromX);
        assertEquals(2, fromX.getLegs().size());
        assertLeg(fromX.getLegs().get(0), Leg.Type.ROUTE, "X", at(7, 45), "B", at(7, 54));
        assertLeg(fromX.getLegs().get(1), Leg.Type.WALK_TRANSFER, "B", at(7, 54), "S", at(8, 4));
    }

    @Test
    void isolinesOverAWindowTheTripLeavesIn_rideFromTheSource() {
        Map<String, Connection> departing = departing().isolineRangeQuery(Map.of("S", at(8, 0)), TimeType.DEPARTURE,
                Duration.ofMinutes(90), QueryConfig.defaults());
        Map<String, Connection> arriving = arriving().isolineRangeQuery(Map.of("S", at(8, 30)), TimeType.ARRIVAL,
                Duration.ofMinutes(90), QueryConfig.defaults());

        assertEquals(1, departing.get("C").getLegs().size());
        assertLeg(departing.get("C").getLegs().getFirst(), Leg.Type.ROUTE, "S", at(9, 3), "C", at(9, 15));
        assertEquals(1, arriving.get("X").getLegs().size());
        assertLeg(arriving.get("X").getLegs().getFirst(), Leg.Type.ROUTE, "X", at(7, 45), "S", at(7, 57));
    }

    @Test
    void queriesWithoutAWindow_rideFromTheSource() {
        // leaving S at 08:56, the walk to B reaches the trip there at 09:06, which waiting at S for 09:03 does too
        Connection toC = departing().isolineQuery(Map.of("S", at(8, 56)), TimeType.DEPARTURE, QueryConfig.defaults())
                .get("C");
        List<Connection> connections = departing().connectionQuery(Map.of("S", at(8, 56)), Map.of("C", 0),
                TimeType.DEPARTURE, QueryConfig.defaults());
        Connection fromX = arriving().isolineQuery(Map.of("S", at(8, 4)), TimeType.ARRIVAL, QueryConfig.defaults())
                .get("X");

        assertEquals(1, toC.getLegs().size());
        assertLeg(toC.getLegs().getFirst(), Leg.Type.ROUTE, "S", at(9, 3), "C", at(9, 15));
        assertEquals(1, connections.size());
        assertLeg(connections.getFirst().getLegs().getFirst(), Leg.Type.ROUTE, "S", at(9, 3), "C", at(9, 15));
        assertEquals(1, fromX.getLegs().size());
        assertLeg(fromX.getLegs().getFirst(), Leg.Type.ROUTE, "X", at(7, 45), "S", at(7, 57));
    }

    @Test
    void connectionsOverTheWindow_areBeatenByTheRideAfterIt() {
        // the ride from S at 09:03 leaves after the window and beats the walk to B in it, which arrives as late, so no
        // connection leaves in the window (routing backward: the ride reaching S at 07:57 beats the walk from B)
        assertTrue(departing().connectionRangeQuery(Map.of("S", at(8, 0)), Map.of("C", 0), TimeType.DEPARTURE, HOUR,
                QueryConfig.defaults()).isEmpty());
        assertTrue(arriving().connectionRangeQuery(Map.of("S", at(9, 0)), Map.of("X", 0), TimeType.ARRIVAL, HOUR,
                QueryConfig.defaults()).isEmpty());
    }
    /**
     * Source stops at different times: A at 08:00 and B at 08:05, two minutes' walk apart, so that the walk from A
     * reaches B earlier than B's own time (routing backward: B at 07:55 and A at 08:00, the walk from A reaching B
     * later). B keeps its own label next to the walk: to walk on from, since a walk does not continue a walk, and to
     * board within its own window.
     */
    @Nested
    class SourceStopReachedOnFootFromAnother {

        private static final Duration FIVE_MINUTES = Duration.ofMinutes(5);
        private static final Map<String, OffsetDateTime> DEPARTING_FROM_B = Map.of("B", at(8, 5));
        private static final Map<String, OffsetDateTime> DEPARTING_FROM_A_AND_B = Map.of("A", at(8, 0), "B",
                at(8, 5));
        private static final Map<String, OffsetDateTime> ARRIVING_AT_B = Map.of("B", at(7, 55));
        private static final Map<String, OffsetDateTime> ARRIVING_AT_A_AND_B = Map.of("A", at(8, 0), "B",
                at(7, 55));

        /**
         * A, B and C two minutes' walk apart in a row, a trip from C at 08:10 to T at 08:20, one from T at 07:40 to C
         * at 07:50, and with a faster route, one from C at 08:08 to T at 08:12. T is reached from B by walking to C
         * and riding, not from A, which would walk twice.
         */
        private static RaptorAlgorithm walkingOn(boolean withFasterRoute) {
            RaptorRouterBuilder builder = RaptorRouter.builder(RaptorConfig.defaults());
            List.of("A", "B", "C", "T").forEach(builder::addStop);
            for (String[] walk : new String[][]{{"A", "B"}, {"B", "A"}, {"B", "C"}, {"C", "B"}}) {
                builder.addTransfer(walk[0], walk[1], 120);
            }
            addTrip(builder, "R", "C", 8 * 60 + 10, "T", 8 * 60 + 20);
            addTrip(builder, "Q", "T", 7 * 60 + 40, "C", 7 * 60 + 50);
            if (withFasterRoute) {
                addTrip(builder, "F", "C", 8 * 60 + 8, "T", 8 * 60 + 12);
            }
            return builder.build();
        }

        /**
         * A walk of two minutes from A to B, and a trip from B at 08:09 to T at 08:20.
         */
        private static RaptorAlgorithm ridingLate() {
            RaptorRouterBuilder builder = RaptorRouter.builder(RaptorConfig.defaults());
            List.of("A", "B", "T").forEach(builder::addStop);
            builder.addTransfer("A", "B", 120);
            addTrip(builder, "R", "B", 8 * 60 + 9, "T", 8 * 60 + 20);
            return builder.build();
        }

        private static void addTrip(RaptorRouterBuilder builder, String routeId, String from, int departure,
                                    String to, int arrival) {
            builder.addRoute(routeId, RaptorRouterTestBuilder.DEFAULT_ZONE_ID, List.of(from, to));
            builder.addTrip(routeId + "-0", routeId);
            builder.addStopTime(routeId, routeId + "-0", 0, from, departure * 60, departure * 60);
            builder.addStopTime(routeId, routeId + "-0", 1, to, arrival * 60, arrival * 60);
        }

        @Test
        void connectionsDeparting_walkOnFromTheSourceStop() {
            RaptorAlgorithm raptor = walkingOn(false);

            for (Map<String, OffsetDateTime> sources : List.of(DEPARTING_FROM_B, DEPARTING_FROM_A_AND_B)) {
                List<Connection> connections = raptor.connectionRangeQuery(sources, Map.of("T", 0),
                        TimeType.DEPARTURE, FIVE_MINUTES, QueryConfig.defaults());

                assertEquals(1, connections.size());
                List<Leg> legs = connections.getFirst().getLegs();
                assertEquals(2, legs.size());
                assertLeg(legs.get(0), Leg.Type.WALK_TRANSFER, "B", at(8, 8), "C", at(8, 10));
                assertLeg(legs.get(1), Leg.Type.ROUTE, "C", at(8, 10), "T", at(8, 20));
            }
        }

        @Test
        void connectionsArriving_walkOnFromTheSourceStop() {
            RaptorAlgorithm raptor = walkingOn(false);

            for (Map<String, OffsetDateTime> sources : List.of(ARRIVING_AT_B, ARRIVING_AT_A_AND_B)) {
                List<Connection> connections = raptor.connectionRangeQuery(sources, Map.of("T", 0), TimeType.ARRIVAL,
                        FIVE_MINUTES, QueryConfig.defaults());

                assertEquals(1, connections.size());
                List<Leg> legs = connections.getFirst().getLegs();
                assertEquals(2, legs.size());
                assertLeg(legs.get(0), Leg.Type.ROUTE, "T", at(7, 40), "C", at(7, 50));
                assertLeg(legs.get(1), Leg.Type.WALK_TRANSFER, "C", at(7, 50), "B", at(7, 52));
            }
        }

        @Test
        void isolines_walkOnFromTheSourceStop() {
            // routed from B leaving at 08:06, the faster trip from C at 08:08 is the shortest
            RaptorAlgorithm raptor = walkingOn(true);

            for (Map<String, OffsetDateTime> sources : List.of(DEPARTING_FROM_B, DEPARTING_FROM_A_AND_B)) {
                Connection toT = raptor.isolineRangeQuery(sources, TimeType.DEPARTURE, FIVE_MINUTES,
                        QueryConfig.defaults()).get("T");

                assertNotNull(toT);
                assertEquals(2, toT.getLegs().size());
                assertLeg(toT.getLegs().get(0), Leg.Type.WALK_TRANSFER, "B", at(8, 6), "C", at(8, 8));
                assertLeg(toT.getLegs().get(1), Leg.Type.ROUTE, "C", at(8, 8), "T", at(8, 12));
            }
        }

        @Test
        void isolines_rideFromTheSourceStopInItsOwnWindow() {
            // the trip from B at 08:09 leaves in B's window, which ends at 08:10, but not two minutes after A's
            RaptorAlgorithm raptor = ridingLate();

            for (Map<String, OffsetDateTime> sources : List.of(DEPARTING_FROM_B, DEPARTING_FROM_A_AND_B)) {
                Connection toT = raptor.isolineRangeQuery(sources, TimeType.DEPARTURE, FIVE_MINUTES,
                        QueryConfig.defaults()).get("T");

                assertNotNull(toT);
                assertEquals(1, toT.getLegs().size());
                assertLeg(toT.getLegs().getFirst(), Leg.Type.ROUTE, "B", at(8, 9), "T", at(8, 20));
            }
        }

        @Test
        void connections_beatenByAWalkFromAnotherSourceStopAfterTheWindow() {
            // leaving A after the window, from 08:05 to 08:07, the walk catches the trip from B at 08:09 too, so no
            // connection leaving in the window is reported; a window of eight minutes holds the latest of them
            RaptorAlgorithm raptor = ridingLate();

            assertTrue(raptor.connectionRangeQuery(DEPARTING_FROM_A_AND_B, Map.of("T", 0), TimeType.DEPARTURE,
                    FIVE_MINUTES, QueryConfig.defaults()).isEmpty());
            List<Connection> connections = raptor.connectionRangeQuery(DEPARTING_FROM_A_AND_B, Map.of("T", 0),
                    TimeType.DEPARTURE, Duration.ofMinutes(8), QueryConfig.defaults());

            assertEquals(1, connections.size());
            assertLeg(connections.getFirst().getLegs().getFirst(), Leg.Type.WALK_TRANSFER, "A", at(8, 7), "B",
                    at(8, 9));
        }
    }
}
