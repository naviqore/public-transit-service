package org.naviqore.raptor.router;

import org.junit.jupiter.api.Test;
import org.naviqore.raptor.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A stop is reached on a trip, on foot or at the start of the query, and the router keeps these arrivals apart: a trip
 * can be boarded after any of them, a walk may only continue an arrival on a trip, and a stop is reached at its
 * earliest arrival of all.
 */
class RaptorRouterTripAndWalkArrivalsTest {

    private static final OffsetDateTime START_OF_DAY = RaptorRouterTestBuilder.DEFAULT_REFERENCE_DATE.atStartOfDay(
            RaptorRouterTestBuilder.DEFAULT_ZONE_ID).toOffsetDateTime();

    private static OffsetDateTime at(int hour, int minute) {
        return START_OF_DAY.plusHours(hour).plusMinutes(minute);
    }

    private static RaptorRouterBuilder builder(String... stops) {
        RaptorRouterBuilder builder = RaptorRouter.builder(RaptorConfig.builder().raptorRangeDefault(-1).build());
        for (String stop : stops) {
            builder.addStop(stop);
        }
        return builder;
    }

    /**
     * Adds a route with one trip from its first to its last stop, at the given minutes after the start of the day.
     */
    private static void addTrip(RaptorRouterBuilder builder, String routeId, List<String> stops, int... minutes) {
        builder.addRoute(routeId, RaptorRouterTestBuilder.DEFAULT_ZONE_ID, stops);
        String tripId = routeId + "-0";
        builder.addTrip(tripId, routeId);
        for (int i = 0; i < stops.size(); i++) {
            builder.addStopTime(routeId, tripId, i, stops.get(i), minutes[i] * 60, minutes[i] * 60);
        }
    }

    private static void assertArrival(Connection connection, OffsetDateTime arrival, int routeLegs, int walks) {
        assertEquals(arrival, connection.getArrivalTime());
        assertEquals(routeLegs, connection.getRouteLegs().size());
        assertEquals(walks, connection.getWalkTransfers().size());
    }

    @Test
    void walkOnFromALaterTripArrival_atAStopReachedOnFootBefore() {
        // S is reached on foot from A at 08:05; the trips A - X - S reach it again at 08:30, which is the arrival to
        // walk on from to T, since a walk may not continue the walk to S
        RaptorRouterBuilder builder = builder("A", "X", "S", "T");
        addTrip(builder, "R1", List.of("A", "X"), 8 * 60, 8 * 60 + 10);
        addTrip(builder, "R2", List.of("X", "S"), 8 * 60 + 20, 8 * 60 + 30);
        builder.addTransfer("A", "S", 300);
        builder.addTransfer("S", "T", 300);
        RaptorAlgorithm raptor = builder.build();

        Map<String, Connection> isolines = raptor.routeIsolines(Map.of("A", at(8, 0)), TimeType.DEPARTURE,
                QueryConfig.defaults());
        assertArrival(isolines.get("S"), at(8, 5), 0, 1);
        assertArrival(isolines.get("T"), at(8, 35), 2, 1);

        List<Connection> connections = raptor.routeEarliestArrival(Map.of("A", at(8, 0)), Map.of("T", 0),
                QueryConfig.defaults());
        assertEquals(1, connections.size());
        assertArrival(connections.getFirst(), at(8, 35), 2, 1);
    }

    @Test
    void walkOnFromALaterTripArrival_routingBackward() {
        // the mirror image: S reaches A on foot by 08:55, and the trips S - X - A, leaving S at 08:30, are the arrival
        // on a trip to walk on from to T; routing backward walks transfers the other way round
        RaptorRouterBuilder builder = builder("T", "S", "X", "A");
        addTrip(builder, "R2", List.of("S", "X"), 8 * 60 + 30, 8 * 60 + 40);
        addTrip(builder, "R1", List.of("X", "A"), 8 * 60 + 50, 9 * 60);
        builder.addTransfer("A", "S", 300);
        builder.addTransfer("S", "T", 300);
        RaptorAlgorithm raptor = builder.build();

        List<Connection> connections = raptor.routeLatestDeparture(Map.of("T", 0), Map.of("A", at(9, 0)),
                QueryConfig.defaults());
        assertEquals(1, connections.size());
        assertEquals(at(8, 25), connections.getFirst().getDepartureTime());
        assertArrival(connections.getFirst(), at(9, 0), 2, 1);
    }

    @Test
    void walksOfARoundDoNotDependOnTheOrderOfTheStops() {
        // in the same round S2 is reached on a trip at 08:12 and, earlier for boarding, on foot from S1 at 08:11: the
        // walk to Q still starts at the arrival on a trip, whichever of S1 and S2 the round walks from first
        RaptorRouterBuilder builder = builder("A", "S1", "S2", "Q");
        addTrip(builder, "R1", List.of("A", "S1"), 8 * 60, 8 * 60 + 10);
        addTrip(builder, "R2", List.of("A", "S2"), 8 * 60, 8 * 60 + 12);
        builder.addTransfer("S1", "S2", 60);
        builder.addTransfer("S2", "Q", 180);
        RaptorAlgorithm raptor = builder.build();

        Map<String, Connection> isolines = raptor.routeIsolines(Map.of("A", at(8, 0)), TimeType.DEPARTURE,
                QueryConfig.defaults());
        assertArrival(isolines.get("Q"), at(8, 15), 1, 1);
    }

    @Test
    void aSourceStopKeepsItsOwnTimeAgainstALaterWalk() {
        // the walk from A reaches B at 08:06, within the same stop transfer time after B's own time of 08:05: B still
        // boards the trip leaving at 08:05
        RaptorRouterBuilder builder = builder("A", "B", "C");
        builder.addRoute("R", RaptorRouterTestBuilder.DEFAULT_ZONE_ID, List.of("B", "C"));
        builder.addTrip("R-0", "R").addTrip("R-1", "R");
        builder.addStopTime("R", "R-0", 0, "B", (8 * 60 + 5) * 60, (8 * 60 + 5) * 60);
        builder.addStopTime("R", "R-0", 1, "C", (8 * 60 + 15) * 60, (8 * 60 + 15) * 60);
        builder.addStopTime("R", "R-1", 0, "B", (8 * 60 + 35) * 60, (8 * 60 + 35) * 60);
        builder.addStopTime("R", "R-1", 1, "C", (8 * 60 + 45) * 60, (8 * 60 + 45) * 60);
        builder.addTransfer("A", "B", 360);
        RaptorAlgorithm raptor = builder.build();

        List<Connection> connections = raptor.routeEarliestArrival(Map.of("A", at(8, 0), "B", at(8, 5)), Map.of("C", 0),
                QueryConfig.defaults());
        assertEquals(1, connections.size());
        assertEquals(at(8, 5), connections.getFirst().getDepartureTime());
        assertArrival(connections.getFirst(), at(8, 15), 1, 0);
    }

    @Test
    void aStopIsReachedAtItsEarliestArrival_onATripRatherThanByALaterWalk() {
        // P is reached on a trip at 08:12, and on foot from P2 at 08:13, which is earlier for boarding a trip at P, but
        // later to end at P
        RaptorRouterBuilder builder = builder("A", "P", "P2");
        addTrip(builder, "R1", List.of("A", "P"), 8 * 60, 8 * 60 + 12);
        addTrip(builder, "R2", List.of("A", "P2"), 8 * 60, 8 * 60 + 10);
        builder.addTransfer("P2", "P", 180);
        RaptorAlgorithm raptor = builder.build();

        Map<String, Connection> isolines = raptor.routeIsolines(Map.of("A", at(8, 0)), TimeType.DEPARTURE,
                QueryConfig.defaults());
        assertArrival(isolines.get("P"), at(8, 12), 1, 0);

        List<Connection> connections = raptor.routeEarliestArrival(Map.of("A", at(8, 0)), Map.of("P", 0),
                QueryConfig.defaults());
        assertEquals(1, connections.size());
        assertArrival(connections.getFirst(), at(8, 12), 1, 0);
        assertTrue(connections.getFirst().getLegs().stream().noneMatch(leg -> leg.getType() == Leg.Type.WALK_TRANSFER));
    }

}
