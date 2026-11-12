package org.naviqore.raptor.router;

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
}
