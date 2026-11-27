package org.naviqore.raptor.router;

import org.junit.jupiter.api.Test;
import org.naviqore.raptor.Connection;
import org.naviqore.raptor.QueryConfig;
import org.naviqore.raptor.RaptorAlgorithm;
import org.naviqore.raptor.TimeType;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A trip is boarded only where it picks up passengers and left only where it sets them down, and a stop or a pair of
 * stops can refuse transfers: no trip is changed for another at the stop, and no walk leads between the stops.
 */
class RaptorRouterBoardingAndRefusalTest {

    private static final OffsetDateTime START_OF_DAY = RaptorRouterTestBuilder.DEFAULT_REFERENCE_DATE.atStartOfDay(
            RaptorRouterTestBuilder.DEFAULT_ZONE_ID).toOffsetDateTime();

    private static OffsetDateTime at(int hour, int minute) {
        return START_OF_DAY.plusHours(hour).plusMinutes(minute);
    }

    private static RaptorRouterBuilder builder(String... stops) {
        RaptorRouterBuilder builder = RaptorRouter.builder(RaptorConfig.defaults());
        for (String stop : stops) {
            builder.addStop(stop);
        }
        return builder;
    }

    /**
     * Adds a trip of a route, at the given minutes after the start of the day, that picks up and sets down passengers
     * everywhere but at the stop given, where it does only what is given.
     */
    private static void addTrip(RaptorRouterBuilder builder, String routeId, String tripId, List<String> stops,
                                String restrictedStop, boolean boarding, boolean alighting, int... minutes) {
        builder.addTrip(tripId, routeId);
        for (int i = 0; i < stops.size(); i++) {
            boolean restricted = stops.get(i).equals(restrictedStop);
            builder.addStopTime(routeId, tripId, i, stops.get(i), minutes[i] * 60, minutes[i] * 60,
                    !restricted || boarding, !restricted || alighting);
        }
    }

    private static void addTrip(RaptorRouterBuilder builder, String routeId, String tripId, List<String> stops,
                                int... minutes) {
        addTrip(builder, routeId, tripId, stops, "", true, true, minutes);
    }

    private static List<Connection> route(RaptorAlgorithm raptor, String source, OffsetDateTime time, String target) {
        return raptor.connectionQuery(Map.of(source, time), Map.of(target, 0), TimeType.DEPARTURE,
                QueryConfig.defaults());
    }

    private static List<Connection> routeArriving(RaptorAlgorithm raptor, String source, String target,
                                                  OffsetDateTime time) {
        return raptor.connectionQuery(Map.of(target, time), Map.of(source, 0), TimeType.ARRIVAL,
                QueryConfig.defaults());
    }

    @Test
    void aTripThatDoesNotPickUpAtAStop_isNotBoardedThere() {
        // R1 runs A - B - C but does not pick up at B, so from B only R2 goes to C
        RaptorRouterBuilder builder = builder("A", "B", "C");
        builder.addRoute("R1", RaptorRouterTestBuilder.DEFAULT_ZONE_ID, List.of("A", "B", "C"));
        addTrip(builder, "R1", "R1-0", List.of("A", "B", "C"), "B", false, true, 8 * 60, 8 * 60 + 10, 8 * 60 + 20);
        builder.addRoute("R2", RaptorRouterTestBuilder.DEFAULT_ZONE_ID, List.of("B", "C"));
        addTrip(builder, "R2", "R2-0", List.of("B", "C"), 8 * 60 + 30, 8 * 60 + 40);
        RaptorAlgorithm raptor = builder.build();

        List<Connection> connections = route(raptor, "B", at(8, 0), "C");
        assertEquals(1, connections.size());
        assertEquals("R2-0", connections.getFirst().getRouteLegs().getFirst().getTripId());

        // routing backward, R1 is not ridden from B either
        connections = routeArriving(raptor, "B", "C", at(8, 45));
        assertEquals(1, connections.size());
        assertEquals("R2-0", connections.getFirst().getRouteLegs().getFirst().getTripId());

        // from A, R1 is boarded and passes B
        connections = route(raptor, "A", at(8, 0), "C");
        assertEquals("R1-0", connections.getFirst().getRouteLegs().getFirst().getTripId());
    }

    @Test
    void aTripThatDoesNotSetDownAtAStop_isNotLeftThere() {
        // R1 runs A - B - C but does not set down at B: B is reached from A by R2 only, later
        RaptorRouterBuilder builder = builder("A", "B", "C");
        builder.addRoute("R1", RaptorRouterTestBuilder.DEFAULT_ZONE_ID, List.of("A", "B", "C"));
        addTrip(builder, "R1", "R1-0", List.of("A", "B", "C"), "B", true, false, 8 * 60, 8 * 60 + 10, 8 * 60 + 20);
        builder.addRoute("R2", RaptorRouterTestBuilder.DEFAULT_ZONE_ID, List.of("A", "B"));
        addTrip(builder, "R2", "R2-0", List.of("A", "B"), 8 * 60 + 30, 8 * 60 + 50);
        RaptorAlgorithm raptor = builder.build();

        List<Connection> connections = route(raptor, "A", at(8, 0), "B");
        assertEquals(1, connections.size());
        assertEquals(at(8, 50), connections.getFirst().getArrivalTime());

        connections = routeArriving(raptor, "A", "B", at(9, 0));
        assertEquals(1, connections.size());
        assertEquals("R2-0", connections.getFirst().getRouteLegs().getFirst().getTripId());

        Map<String, Connection> isolines = raptor.isolineQuery(Map.of("A", at(8, 0)), TimeType.DEPARTURE,
                QueryConfig.defaults());
        assertEquals(at(8, 50), isolines.get("B").getArrivalTime());
        assertEquals(at(8, 20), isolines.get("C").getArrivalTime());
    }

    @Test
    void tripsOfARouteThatDoNotPickUpAtAStop_areSkippedThere() {
        // the 08:00 trip of R1 does not pick up at B, the 09:00 trip does: from B, the search takes the later one
        RaptorRouterBuilder builder = builder("A", "B", "C");
        builder.addRoute("R1", RaptorRouterTestBuilder.DEFAULT_ZONE_ID, List.of("A", "B", "C"));
        addTrip(builder, "R1", "R1-0", List.of("A", "B", "C"), "B", false, true, 8 * 60, 8 * 60 + 10, 8 * 60 + 20);
        addTrip(builder, "R1", "R1-1", List.of("A", "B", "C"), 9 * 60, 9 * 60 + 10, 9 * 60 + 20);
        RaptorAlgorithm raptor = builder.build();

        List<Connection> connections = route(raptor, "B", at(8, 0), "C");
        assertEquals(1, connections.size());
        assertEquals("R1-1", connections.getFirst().getRouteLegs().getFirst().getTripId());

        connections = routeArriving(raptor, "B", "C", at(9, 30));
        assertEquals("R1-1", connections.getFirst().getRouteLegs().getFirst().getTripId());
    }

    @Test
    void aStopThatRefusesTransfers_changesNoTrip() {
        // R1 reaches S at 08:10, R2 leaves it at 08:20: a change, unless S refuses transfers
        RaptorRouterBuilder builder = builder("A", "S", "T");
        builder.addRoute("R1", RaptorRouterTestBuilder.DEFAULT_ZONE_ID, List.of("A", "S"));
        addTrip(builder, "R1", "R1-0", List.of("A", "S"), 8 * 60, 8 * 60 + 10);
        builder.addRoute("R2", RaptorRouterTestBuilder.DEFAULT_ZONE_ID, List.of("S", "T"));
        addTrip(builder, "R2", "R2-0", List.of("S", "T"), 8 * 60 + 20, 8 * 60 + 30);
        builder.refuseTransfers("S", "S");
        RaptorAlgorithm raptor = builder.build();

        assertTrue(route(raptor, "A", at(8, 0), "T").isEmpty());
        assertTrue(routeArriving(raptor, "A", "T", at(9, 0)).isEmpty());

        // S is still reached, and a journey starting at S boards R2
        assertEquals(at(8, 10), route(raptor, "A", at(8, 0), "S").getFirst().getArrivalTime());
        assertEquals(at(8, 30), route(raptor, "S", at(8, 0), "T").getFirst().getArrivalTime());
        Map<String, Connection> isolines = raptor.isolineQuery(Map.of("A", at(8, 0)), TimeType.DEPARTURE,
                QueryConfig.defaults());
        assertEquals(at(8, 10), isolines.get("S").getArrivalTime());
        assertFalse(isolines.containsKey("T"));
    }

    @Test
    void aStopThatRefusesTransfers_stillLetsAWalkContinueFromIt() {
        // the walk from S to U is not a change at S
        RaptorRouterBuilder builder = builder("A", "S", "U", "T");
        builder.addRoute("R1", RaptorRouterTestBuilder.DEFAULT_ZONE_ID, List.of("A", "S"));
        addTrip(builder, "R1", "R1-0", List.of("A", "S"), 8 * 60, 8 * 60 + 10);
        builder.addRoute("R2", RaptorRouterTestBuilder.DEFAULT_ZONE_ID, List.of("U", "T"));
        addTrip(builder, "R2", "R2-0", List.of("U", "T"), 8 * 60 + 20, 8 * 60 + 30);
        builder.addTransfer("S", "U", 300);
        builder.refuseTransfers("S", "S");
        RaptorAlgorithm raptor = builder.build();

        List<Connection> connections = route(raptor, "A", at(8, 0), "T");
        assertEquals(1, connections.size());
        assertEquals(at(8, 30), connections.getFirst().getArrivalTime());
    }

    @Test
    void aWalkBetweenStopsThatRefuseTransfers_isDropped() {
        // the walk from S to U is refused, also when added after the refusal; the walk back is not
        RaptorRouterBuilder builder = builder("A", "S", "U", "T");
        builder.addRoute("R1", RaptorRouterTestBuilder.DEFAULT_ZONE_ID, List.of("A", "S"));
        addTrip(builder, "R1", "R1-0", List.of("A", "S"), 8 * 60, 8 * 60 + 10);
        builder.addRoute("R2", RaptorRouterTestBuilder.DEFAULT_ZONE_ID, List.of("U", "T"));
        addTrip(builder, "R2", "R2-0", List.of("U", "T"), 8 * 60 + 20, 8 * 60 + 30);
        builder.refuseTransfers("S", "U");
        builder.addTransfer("S", "U", 300);
        builder.addTransfer("U", "S", 300);
        RaptorAlgorithm raptor = builder.build();

        assertTrue(route(raptor, "A", at(8, 0), "T").isEmpty());
        assertTrue(routeArriving(raptor, "A", "T", at(9, 0)).isEmpty());
    }

}
