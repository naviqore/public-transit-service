package org.naviqore.raptor.router;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.naviqore.raptor.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A route scan rides the best trip it can board and boards another one at a later stop only if that one is better, also
 * where the trip ridden passes the stop after the best time at the target.
 */
class RaptorRouterRouteScanTest {

    private static final OffsetDateTime EIGHT_AM = RaptorRouterTestBuilder.DEFAULT_REFERENCE_DATE.atStartOfDay(
            RaptorRouterTestBuilder.DEFAULT_ZONE_ID).toOffsetDateTime().plusHours(8);

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

    private static List<String> tripsOf(Connection connection) {
        return connection.getRouteLegs().stream().map(Leg::getTripId).toList();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void keepTheTrip_whenTheLabelAtALaterStopIsTooLateForIt(boolean laterTrip) {
        // U reaches X at 08:03, in time for R leaving X at 08:05, and S reaches Q at 08:09, one minute before R: a
        // trip boarded at Q after the same stop transfer time of two minutes leaves at 08:30, or with no later trip
        // not at all, which must not replace the trip boarded at X
        RaptorRouterBuilder builder = RaptorRouter.builder(RaptorConfig.defaults());
        List.of("A", "X", "Q", "Y").forEach(builder::addStop);
        addRouteWithTrips(builder, "U", List.of("A", "X"), List.of(List.of(8 * 60, 8 * 60 + 3)));
        addRouteWithTrips(builder, "S", List.of("A", "Q"), List.of(List.of(8 * 60, 8 * 60 + 9)));
        List<Integer> ridden = List.of(8 * 60 + 5, 8 * 60 + 10, 8 * 60 + 20);
        List<Integer> later = List.of(8 * 60 + 25, 8 * 60 + 30, 8 * 60 + 40);
        List<List<Integer>> tripsOfR = laterTrip ? List.of(ridden, later) : List.of(ridden);
        addRouteWithTrips(builder, "R", List.of("X", "Q", "Y"), tripsOfR);
        RaptorAlgorithm raptor = builder.build();

        List<Connection> connections = raptor.connectionQuery(Map.of("A", EIGHT_AM), Map.of("Y", 0), TimeType.DEPARTURE,
                QueryConfig.defaults());

        assertEquals(1, connections.size());
        assertEquals(EIGHT_AM.plusMinutes(20), connections.getFirst().getArrivalTime());
        assertEquals(2, connections.getFirst().getRouteLegs().size());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void keepTheTrip_whenTheLabelAtALaterStopIsTooLateForIt_routingBackward(boolean earlierTrip) {
        // the mirror image: arriving at A by 09:00, U leaves X at 08:57 and S leaves Q at 08:51; R reaching X at 08:55
        // passes Q at 08:50, which a trip from Q leaving at 08:51 cannot be reached from after the same stop transfer
        RaptorRouterBuilder builder = RaptorRouter.builder(RaptorConfig.defaults());
        List.of("Y", "Q", "X", "A").forEach(builder::addStop);
        addRouteWithTrips(builder, "U", List.of("X", "A"), List.of(List.of(8 * 60 + 57, 9 * 60)));
        addRouteWithTrips(builder, "S", List.of("Q", "A"), List.of(List.of(8 * 60 + 51, 9 * 60)));
        List<Integer> earlier = List.of(8 * 60 + 20, 8 * 60 + 30, 8 * 60 + 35);
        List<Integer> ridden = List.of(8 * 60 + 40, 8 * 60 + 50, 8 * 60 + 55);
        List<List<Integer>> tripsOfR = earlierTrip ? List.of(earlier, ridden) : List.of(ridden);
        addRouteWithTrips(builder, "R", List.of("Y", "Q", "X"), tripsOfR);
        RaptorAlgorithm raptor = builder.build();

        List<Connection> connections = raptor.connectionQuery(Map.of("A", EIGHT_AM.plusHours(1)), Map.of("Y", 0),
                TimeType.ARRIVAL, QueryConfig.defaults());

        assertEquals(1, connections.size());
        assertEquals(EIGHT_AM.plusMinutes(40), connections.getFirst().getDepartureTime());
        assertEquals(2, connections.getFirst().getRouteLegs().size());
    }

    @Test
    void boardAnEarlierTrip_whereTheTripRiddenPassesAfterTheBestTimeAtTheTarget() {
        // D reaches the target T at 09:00 on one trip. P reaches A at 08:10 and Q reaches B at 08:20. Of R, running
        // A - B - T, only the trip of 08:40 can be boarded at A: it passes B at 09:10, after the best time at the
        // target, where the trip of R leaving at 08:30 is boarded all the same, which reaches T at 08:50
        RaptorRouterBuilder builder = RaptorRouter.builder(RaptorConfig.defaults());
        List.of("S", "A", "B", "T").forEach(builder::addStop);
        addRouteWithTrips(builder, "D", List.of("S", "T"), List.of(List.of(8 * 60, 9 * 60)));
        addRouteWithTrips(builder, "P", List.of("S", "A"), List.of(List.of(8 * 60, 8 * 60 + 10)));
        addRouteWithTrips(builder, "Q", List.of("S", "B"), List.of(List.of(8 * 60, 8 * 60 + 20)));
        addRouteWithTrips(builder, "R", List.of("A", "B", "T"),
                List.of(List.of(8 * 60, 8 * 60 + 30, 8 * 60 + 50), List.of(8 * 60 + 40, 9 * 60 + 10, 9 * 60 + 30)));
        RaptorAlgorithm raptor = builder.build();

        List<Connection> connections = raptor.connectionQuery(Map.of("S", EIGHT_AM), Map.of("T", 0), TimeType.DEPARTURE,
                QueryConfig.defaults());

        assertEquals(2, connections.size());
        assertEquals(List.of("D-0"), tripsOf(connections.get(0)));
        assertEquals(EIGHT_AM.plusMinutes(60), connections.get(0).getArrivalTime());
        assertEquals(List.of("Q-0", "R-0"), tripsOf(connections.get(1)));
        assertEquals(EIGHT_AM.plusMinutes(50), connections.get(1).getArrivalTime());
    }

    @Test
    void boardALaterTrip_whereTheTripRiddenPassesBeforeTheBestTimeAtTheTarget_routingBackward() {
        // the mirror image: arriving at T by 10:00, D leaves the target S at 09:00 on one trip. P leaves A at 09:50
        // and Q leaves B at 09:40. Of R, running S - B - A, only the trip reaching A at 09:20 can be left there: it
        // passes B at 08:50, before the best time at the target, where the trip of R arriving at 09:30 is left all
        // the same, which leaves S at 09:10
        RaptorRouterBuilder builder = RaptorRouter.builder(RaptorConfig.defaults());
        List.of("S", "A", "B", "T").forEach(builder::addStop);
        addRouteWithTrips(builder, "D", List.of("S", "T"), List.of(List.of(9 * 60, 10 * 60)));
        addRouteWithTrips(builder, "P", List.of("A", "T"), List.of(List.of(9 * 60 + 50, 10 * 60)));
        addRouteWithTrips(builder, "Q", List.of("B", "T"), List.of(List.of(9 * 60 + 40, 10 * 60)));
        addRouteWithTrips(builder, "R", List.of("S", "B", "A"),
                List.of(List.of(8 * 60 + 30, 8 * 60 + 50, 9 * 60 + 20), List.of(9 * 60 + 10, 9 * 60 + 30, 10 * 60)));
        RaptorAlgorithm raptor = builder.build();

        List<Connection> connections = raptor.connectionQuery(Map.of("T", EIGHT_AM.plusHours(2)), Map.of("S", 0),
                TimeType.ARRIVAL, QueryConfig.defaults());

        assertEquals(2, connections.size());
        assertEquals(List.of("D-0"), tripsOf(connections.get(0)));
        assertEquals(EIGHT_AM.plusMinutes(60), connections.get(0).getDepartureTime());
        assertEquals(List.of("R-1", "Q-0"), tripsOf(connections.get(1)));
        assertEquals(EIGHT_AM.plusMinutes(70), connections.get(1).getDepartureTime());
    }

}
