package org.naviqore.raptor.router;

import org.junit.jupiter.api.Test;
import org.naviqore.raptor.*;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Transfers between particular routes or trips: the most specific one applies to a pair of trips, the stops' walks and
 * same stop transfer times where none does, and a trip transfer that refuses a change or takes longer than the stop
 * never hides another arrival at the stop. A later trip that a trip transfer allows to change is found on another
 * service day too, and a trip boarded along a trip transfer leads to no later trip that transfer refuses.
 */
class RaptorRouterTripTransferTest {

    private static final OffsetDateTime START_OF_DAY = RaptorRouterTestBuilder.DEFAULT_REFERENCE_DATE.atStartOfDay(
            RaptorRouterTestBuilder.DEFAULT_ZONE_ID).toOffsetDateTime();

    private static OffsetDateTime at(int hour, int minute) {
        return START_OF_DAY.plusHours(hour).plusMinutes(minute);
    }

    private static RaptorRouterBuilder builder(String... stops) {
        return builder(RaptorConfig.defaults(), stops);
    }

    private static RaptorRouterBuilder builder(RaptorConfig config, String... stops) {
        RaptorRouterBuilder builder = RaptorRouter.builder(config);
        for (String stop : stops) {
            builder.addStop(stop);
        }
        return builder;
    }

    /**
     * Adds a route with a trip at each start time, in minutes after the start of the day, each reaching the following
     * stops after the given minutes; trip i is named route-i.
     */
    private static void addRoute(RaptorRouterBuilder builder, String routeId, List<String> stops, int[] travel,
                                 int... starts) {
        builder.addRoute(routeId, RaptorRouterTestBuilder.DEFAULT_ZONE_ID, stops);
        for (int i = 0; i < starts.length; i++) {
            String tripId = routeId + "-" + i;
            builder.addTrip(tripId, routeId);
            for (int s = 0; s < stops.size(); s++) {
                int time = (starts[i] + travel[s]) * 60;
                builder.addStopTime(routeId, tripId, s, stops.get(s), time, time);
            }
        }
    }

    private static TripTransfer trips(String fromStop, String fromTrip, String toStop, String toTrip,
                                      TripTransfer.Type type, int duration) {
        return new TripTransfer(fromStop, null, fromTrip, toStop, null, toTrip, type, duration);
    }

    private static List<Connection> route(RaptorAlgorithm raptor, Map<String, OffsetDateTime> sources, String target,
                                          QueryConfig config) {
        return raptor.connectionQuery(sources, Map.of(target, 0), TimeType.DEPARTURE, config);
    }

    private static List<String> tripsOf(Connection connection) {
        return connection.getRouteLegs().stream().map(Leg::getTripId).toList();
    }

    /**
     * X runs A - C every 30 minutes from 08:00, reaching C after 10; Y runs C - D every hour from 08:40, reaching D
     * after 10. C refuses transfers, except from the X of 08:30 to the Y of 08:40 and from the X of 09:30 to the Y of
     * 09:40, in no time: every other X changes to Y, and the first X that runs is not one of them.
     */
    private static RaptorAlgorithm everyOtherTripChanges() {
        RaptorRouterBuilder builder = builder("A", "C", "D");
        addRoute(builder, "X", List.of("A", "C"), new int[]{0, 10}, 8 * 60, 8 * 60 + 30, 9 * 60, 9 * 60 + 30);
        addRoute(builder, "Y", List.of("C", "D"), new int[]{0, 10}, 8 * 60 + 40, 9 * 60 + 40);
        builder.refuseTransfers("C", "C");
        builder.addTripTransfer(trips("C", "X-1", "C", "Y-0", TripTransfer.Type.MINIMUM_TIME, 0));
        builder.addTripTransfer(trips("C", "X-3", "C", "Y-1", TripTransfer.Type.MINIMUM_TIME, 0));
        return builder.build();
    }

    /**
     * P runs A - C, reaching C at 08:00; X runs C - D at 08:05 and 08:10, Y runs D - E at 08:25. C and D refuse
     * transfers, except from P to the X of 08:05 and from the X of 08:10 to Y, and from P to the X of 08:10 if it may.
     */
    private static RaptorAlgorithm changesAlongTwoTripTransfers(boolean toLaterTrip) {
        RaptorRouterBuilder builder = builder("A", "C", "D", "E");
        addRoute(builder, "P", List.of("A", "C"), new int[]{0, 10}, 7 * 60 + 50);
        addRoute(builder, "X", List.of("C", "D"), new int[]{0, 10}, 8 * 60 + 5, 8 * 60 + 10);
        addRoute(builder, "Y", List.of("D", "E"), new int[]{0, 10}, 8 * 60 + 25);
        builder.refuseTransfers("C", "C");
        builder.refuseTransfers("D", "D");
        builder.addTripTransfer(trips("C", "P-0", "C", "X-0", TripTransfer.Type.MINIMUM_TIME, 0));
        if (toLaterTrip) {
            builder.addTripTransfer(trips("C", "P-0", "C", "X-1", TripTransfer.Type.MINIMUM_TIME, 0));
        }
        builder.addTripTransfer(trips("D", "X-1", "D", "Y-0", TripTransfer.Type.MINIMUM_TIME, 0));
        return builder.build();
    }

    /**
     * F runs S - A at 07:40 and 07:45, reaching A after 10; L runs A - C - B - C - D at 08:00, calling at C at 08:10
     * and again at 08:30; Y runs C - E at 08:15 and 08:35, reaching E after 10. C refuses transfers, except from L to
     * the trips of Y the trip transfers name.
     */
    private static RaptorAlgorithm routeCallingTwiceWhereItChanges(String... tripsOfY) {
        RaptorRouterBuilder builder = builder("S", "A", "B", "C", "D", "E");
        addRoute(builder, "F", List.of("S", "A"), new int[]{0, 10}, 7 * 60 + 40, 7 * 60 + 45);
        addRoute(builder, "L", List.of("A", "C", "B", "C", "D"), new int[]{0, 10, 20, 30, 40}, 8 * 60);
        addRoute(builder, "Y", List.of("C", "E"), new int[]{0, 10}, 8 * 60 + 15, 8 * 60 + 35);
        builder.refuseTransfers("C", "C");
        for (String trip : tripsOfY) {
            builder.addTripTransfer(trips("C", "L-0", "C", trip, TripTransfer.Type.MINIMUM_TIME, 0));
        }
        return builder.build();
    }

    @Test
    void onlyTheTripsATripTransferNames_change() {
        RaptorAlgorithm raptor = everyOtherTripChanges();

        List<Connection> connections = route(raptor, Map.of("A", at(8, 0)), "D", QueryConfig.defaults());
        assertEquals(1, connections.size());
        assertEquals(List.of("X-1", "Y-0"), tripsOf(connections.getFirst()));
        assertEquals(at(8, 30), connections.getFirst().getDepartureTime());
        assertEquals(at(8, 50), connections.getFirst().getArrivalTime());
    }

    @Test
    void onlyTheTripsATripTransferNames_change_routingBackward() {
        RaptorAlgorithm raptor = everyOtherTripChanges();

        List<Connection> connections = raptor.connectionQuery(Map.of("D", at(10, 0)), Map.of("A", 0), TimeType.ARRIVAL,
                QueryConfig.defaults());
        assertEquals(1, connections.size());
        assertEquals(List.of("X-3", "Y-1"), tripsOf(connections.getFirst()));
        assertEquals(at(9, 30), connections.getFirst().getDepartureTime());
    }

    @Test
    void onlyTheTripsATripTransferNames_change_overATimeWindow() {
        RaptorAlgorithm raptor = everyOtherTripChanges();

        List<Connection> connections = raptor.connectionRangeQuery(Map.of("A", at(8, 0)), Map.of("D", 0),
                TimeType.DEPARTURE, Duration.ofMinutes(120), QueryConfig.defaults());
        assertEquals(2, connections.size());
        assertEquals(List.of("X-1", "Y-0"), tripsOf(connections.get(0)));
        assertEquals(List.of("X-3", "Y-1"), tripsOf(connections.get(1)));

        Map<String, Connection> isolines = raptor.isolineQuery(Map.of("A", at(8, 0)), TimeType.DEPARTURE,
                QueryConfig.defaults());
        assertEquals(at(8, 50), isolines.get("D").getArrivalTime());
        assertEquals(at(8, 10), isolines.get("C").getArrivalTime());
    }

    @Test
    void aRouteCallingTwiceWhereItChanges_changesAtItsFirstCall_inEveryPassOfATimeWindow() {
        RaptorAlgorithm raptor = routeCallingTwiceWhereItChanges("Y-0", "Y-1");

        // a pass for each trip of F: both ride the same trip of L past C twice
        List<Connection> connections = raptor.connectionRangeQuery(Map.of("S", at(7, 40)), Map.of("E", 0),
                TimeType.DEPARTURE, Duration.ofMinutes(10), QueryConfig.defaults());
        assertEquals(1, connections.size());
        assertEquals(List.of("F-1", "L-0", "Y-0"), tripsOf(connections.getFirst()));
        assertEquals(at(7, 45), connections.getFirst().getDepartureTime());
        assertEquals(at(8, 25), connections.getFirst().getArrivalTime());

        Map<String, Connection> isolines = raptor.isolineRangeQuery(Map.of("S", at(7, 40)), TimeType.DEPARTURE,
                Duration.ofMinutes(10), QueryConfig.defaults());
        assertEquals(List.of("F-1", "L-0", "Y-0"), tripsOf(isolines.get("E")));
        assertEquals(at(8, 25), isolines.get("E").getArrivalTime());
    }

    @Test
    void aRouteCallingTwiceWhereItChanges_changesToTheTripItsTripTransferNames_inEveryPassOfATimeWindow() {
        RaptorAlgorithm raptor = routeCallingTwiceWhereItChanges("Y-1");

        List<Connection> connections = raptor.connectionRangeQuery(Map.of("S", at(7, 40)), Map.of("E", 0),
                TimeType.DEPARTURE, Duration.ofMinutes(10), QueryConfig.defaults());
        assertEquals(1, connections.size());
        assertEquals(List.of("F-1", "L-0", "Y-1"), tripsOf(connections.getFirst()));
        assertEquals(at(7, 45), connections.getFirst().getDepartureTime());
        assertEquals(at(8, 45), connections.getFirst().getArrivalTime());

        // as the query at each departure of the window answers
        for (int minute : new int[]{40, 45}) {
            List<Connection> atDeparture = route(raptor, Map.of("S", at(7, minute)), "E", QueryConfig.defaults());
            assertEquals(1, atDeparture.size());
            assertEquals(List.of(minute == 40 ? "F-0" : "F-1", "L-0", "Y-1"), tripsOf(atDeparture.getFirst()));
            assertEquals(at(8, 45), atDeparture.getFirst().getArrivalTime());
        }
    }

    @Test
    void aTripOfTheNextServiceDay_changesByItsTripTransfer() {
        // X runs A - C at 00:10 and 23:40, Y runs C - D at 00:30, every day; C refuses transfers, except from the X of
        // 00:10 to Y: from A at 23:35, the X of 23:40 cannot change at C, but the X of 00:10 of the next day can
        RaptorRouterBuilder builder = builder(RaptorConfig.builder().daysToScan(3).build(), "A", "C", "D");
        addRoute(builder, "X", List.of("A", "C"), new int[]{0, 10}, 10, 23 * 60 + 40);
        addRoute(builder, "Y", List.of("C", "D"), new int[]{0, 10}, 30);
        builder.refuseTransfers("C", "C");
        builder.addTripTransfer(trips("C", "X-0", "C", "Y-0", TripTransfer.Type.MINIMUM_TIME, 0));
        RaptorAlgorithm raptor = builder.build();

        List<Connection> connections = route(raptor, Map.of("A", at(23, 35)), "D", QueryConfig.defaults());
        assertEquals(1, connections.size());
        assertEquals(List.of("X-0", "Y-0"), tripsOf(connections.getFirst()));
        assertEquals(at(24, 10), connections.getFirst().getDepartureTime());
        assertEquals(at(24, 40), connections.getFirst().getArrivalTime());
    }

    @Test
    void aTripOfThePreviousServiceDay_changesByItsTripTransfer_routingBackward() {
        // X runs A - C at 23:30, Y runs C - D at 00:05 and 23:45, every day; C refuses transfers, except from X to the Y
        // of 23:45: arriving at D by 00:20, the Y of 00:05 cannot be changed to at C, but the Y of 23:45 of the day
        // before can
        RaptorRouterBuilder builder = builder(RaptorConfig.builder().daysToScan(3).build(), "A", "C", "D");
        addRoute(builder, "X", List.of("A", "C"), new int[]{0, 10}, 23 * 60 + 30);
        addRoute(builder, "Y", List.of("C", "D"), new int[]{0, 10}, 5, 23 * 60 + 45);
        builder.refuseTransfers("C", "C");
        builder.addTripTransfer(trips("C", "X-0", "C", "Y-1", TripTransfer.Type.MINIMUM_TIME, 0));
        RaptorAlgorithm raptor = builder.build();

        List<Connection> connections = raptor.connectionQuery(Map.of("D", at(0, 20)), Map.of("A", 0), TimeType.ARRIVAL,
                QueryConfig.defaults());
        assertEquals(1, connections.size());
        assertEquals(List.of("X-0", "Y-1"), tripsOf(connections.getFirst()));
        assertEquals(at(-1, 30), connections.getFirst().getDepartureTime());
        assertEquals(at(-1, 55), connections.getFirst().getArrivalTime());
    }

    @Test
    void aTripBoardedAlongATripTransfer_leadsToNoLaterTripTheTransferRefuses() {
        // P may change to the X of 08:05 only, which may not change to Y: the X of 08:10 is not reached
        RaptorAlgorithm raptor = changesAlongTwoTripTransfers(false);

        assertTrue(route(raptor, Map.of("A", at(7, 50)), "E", QueryConfig.defaults()).isEmpty());
        assertTrue(
                raptor.connectionQuery(Map.of("E", at(8, 40)), Map.of("A", 0), TimeType.ARRIVAL, QueryConfig.defaults())
                        .isEmpty());
    }

    @Test
    void aTripBoardedAlongATripTransfer_leadsToALaterTripTheTransferAllows() {
        RaptorAlgorithm raptor = changesAlongTwoTripTransfers(true);

        List<Connection> connections = route(raptor, Map.of("A", at(7, 50)), "E", QueryConfig.defaults());
        assertEquals(1, connections.size());
        assertEquals(List.of("P-0", "X-1", "Y-0"), tripsOf(connections.getFirst()));
        assertEquals(at(8, 35), connections.getFirst().getArrivalTime());

        connections = raptor.connectionQuery(Map.of("E", at(8, 40)), Map.of("A", 0), TimeType.ARRIVAL,
                QueryConfig.defaults());
        assertEquals(1, connections.size());
        assertEquals(List.of("P-0", "X-1", "Y-0"), tripsOf(connections.getFirst()));
    }

    @Test
    void aTripTransferLongerThanTheStop_doesNotHideAnotherRoute() {
        // Q1 reaches S at 08:00 and Q2 at 08:02, R leaves at 08:05; S takes 120 s to change, but from Q1 to R an hour:
        // the earlier arrival of Q1 must not hide that of Q2, which makes it
        RaptorRouterBuilder builder = builder("A", "B", "S", "T");
        addRoute(builder, "Q1", List.of("A", "S"), new int[]{0, 10}, 7 * 60 + 50);
        addRoute(builder, "Q2", List.of("B", "S"), new int[]{0, 12}, 7 * 60 + 50);
        addRoute(builder, "R", List.of("S", "T"), new int[]{0, 10}, 8 * 60 + 5, 9 * 60 + 5);
        builder.addTripTransfer(
                new TripTransfer("S", "Q1", null, "S", "R", null, TripTransfer.Type.MINIMUM_TIME, 3600));
        RaptorAlgorithm raptor = builder.build();

        List<Connection> connections = route(raptor, Map.of("A", at(7, 45), "B", at(7, 45)), "T",
                QueryConfig.defaults());
        assertEquals(1, connections.size());
        assertEquals(List.of("Q2-0", "R-0"), tripsOf(connections.getFirst()));

        // from A alone, Q1 changes to the R an hour later
        connections = route(raptor, Map.of("A", at(7, 45)), "T", QueryConfig.defaults());
        assertEquals(List.of("Q1-0", "R-1"), tripsOf(connections.getFirst()));
    }

    @Test
    void aTripTransferShorterThanTheWalk_changesBetweenStops() {
        // a reaches platform P4 at 09:05 and b leaves platform P5 at 09:08; the walk takes 240 s, but from a to b 180 s
        RaptorRouterBuilder builder = builder("A", "P4", "P5", "T");
        addRoute(builder, "Q", List.of("A", "P4"), new int[]{0, 5}, 9 * 60);
        addRoute(builder, "R", List.of("P5", "T"), new int[]{0, 10}, 9 * 60 + 8, 9 * 60 + 38);
        builder.addTransfer("P4", "P5", 240);
        builder.addTripTransfer(trips("P4", "Q-0", "P5", "R-0", TripTransfer.Type.MINIMUM_TIME, 180));
        RaptorAlgorithm raptor = builder.build();

        List<Connection> connections = route(raptor, Map.of("A", at(9, 0)), "T", QueryConfig.defaults());
        assertEquals(1, connections.size());
        Connection connection = connections.getFirst();
        assertEquals(List.of("Q-0", "R-0"), tripsOf(connection));
        assertEquals(1, connection.getWalkTransfers().size());
        assertEquals(at(9, 18), connection.getArrivalTime());

        // routing backward, the same change
        connections = raptor.connectionQuery(Map.of("T", at(9, 20)), Map.of("A", 0), TimeType.ARRIVAL,
                QueryConfig.defaults());
        assertEquals(List.of("Q-0", "R-0"), tripsOf(connections.getFirst()));

        // another trip of Q, without a trip transfer, walks in 240 s
        Map<String, Connection> isolines = raptor.isolineQuery(Map.of("A", at(9, 0)), TimeType.DEPARTURE,
                QueryConfig.defaults());
        assertEquals(at(9, 18), isolines.get("T").getArrivalTime());

        // a query that walks at most 120 s takes neither
        QueryConfig shortWalks = QueryConfig.builder().maximumWalkDuration(120).build();
        assertTrue(route(raptor, Map.of("A", at(9, 0)), "T", shortWalks).isEmpty());
    }

    @Test
    void aWalkFromAnArrivalWithTripTransfers_boardsOtherRoutesAsTheWalkAllows() {
        // from a at P4, the trip transfer to R at P5 takes an hour, but the walk to P5 still boards R2 after 240 s
        RaptorRouterBuilder builder = builder("A", "P4", "P5", "T", "U");
        addRoute(builder, "Q", List.of("A", "P4"), new int[]{0, 5}, 9 * 60);
        addRoute(builder, "R", List.of("P5", "T"), new int[]{0, 10}, 9 * 60 + 10);
        addRoute(builder, "R2", List.of("P5", "U"), new int[]{0, 10}, 9 * 60 + 10);
        builder.addTransfer("P4", "P5", 240);
        builder.addTripTransfer(trips("P4", "Q-0", "P5", "R-0", TripTransfer.Type.MINIMUM_TIME, 3600));
        RaptorAlgorithm raptor = builder.build();

        assertTrue(route(raptor, Map.of("A", at(9, 0)), "T", QueryConfig.defaults()).isEmpty());
        List<Connection> connections = route(raptor, Map.of("A", at(9, 0)), "U", QueryConfig.defaults());
        assertEquals(List.of("Q-0", "R2-0"), tripsOf(connections.getFirst()));
    }

    @Test
    void anInSeatTransfer_takesNoTimeAndNoMinimumTransferDuration() {
        // a ends at S at 09:00, where b starts at 09:00; S takes 120 s to change, and the query asks for 300 s
        RaptorRouterBuilder builder = builder("A", "S", "T");
        addRoute(builder, "Q", List.of("A", "S"), new int[]{0, 10}, 8 * 60 + 50);
        addRoute(builder, "R", List.of("S", "T"), new int[]{0, 10}, 9 * 60, 10 * 60);
        builder.addTripTransfer(trips("S", "Q-0", "S", "R-0", TripTransfer.Type.IN_SEAT, 0));
        RaptorAlgorithm raptor = builder.build();

        QueryConfig config = QueryConfig.builder().minimumTransferDuration(300).build();
        List<Connection> connections = route(raptor, Map.of("A", at(8, 50)), "T", config);
        assertEquals(List.of("Q-0", "R-0"), tripsOf(connections.getFirst()));
        assertEquals(at(9, 10), connections.getFirst().getArrivalTime());
    }

    @Test
    void aTripTransferThatIsNotPossible_takesTheNextTrip() {
        // from a, the R of 08:05 is refused; the stop allows the R of 08:20
        RaptorRouterBuilder builder = builder("A", "S", "T");
        addRoute(builder, "Q", List.of("A", "S"), new int[]{0, 10}, 7 * 60 + 50);
        addRoute(builder, "R", List.of("S", "T"), new int[]{0, 10}, 8 * 60 + 5, 8 * 60 + 20);
        builder.addTripTransfer(trips("S", "Q-0", "S", "R-0", TripTransfer.Type.NOT_POSSIBLE, 0));
        RaptorAlgorithm raptor = builder.build();

        List<Connection> connections = route(raptor, Map.of("A", at(7, 50)), "T", QueryConfig.defaults());
        assertEquals(List.of("Q-0", "R-1"), tripsOf(connections.getFirst()));
    }

    @Test
    void theMostSpecificTripTransfer_applies() {
        // no trip of Q changes to R at S, but its trip of 07:50 changes to the R of 08:05 in no time
        RaptorRouterBuilder builder = builder("A", "S", "T");
        addRoute(builder, "Q", List.of("A", "S"), new int[]{0, 10}, 7 * 60 + 50, 8 * 60 + 50);
        addRoute(builder, "R", List.of("S", "T"), new int[]{0, 10}, 8 * 60 + 5, 9 * 60 + 5, 10 * 60 + 5);
        builder.addTripTransfer(new TripTransfer("S", "Q", null, "S", "R", null, TripTransfer.Type.NOT_POSSIBLE, 0));
        builder.addTripTransfer(trips("S", "Q-0", "S", "R-0", TripTransfer.Type.MINIMUM_TIME, 0));
        RaptorAlgorithm raptor = builder.build();

        assertEquals(List.of("Q-0", "R-0"),
                tripsOf(route(raptor, Map.of("A", at(7, 50)), "T", QueryConfig.defaults()).getFirst()));
        assertTrue(route(raptor, Map.of("A", at(8, 0)), "T", QueryConfig.defaults()).isEmpty());
    }

    @Test
    void aTripTransferNamingARouteOrTripTheRouterDoesNotHave_isRejected() {
        // an unknown trip, a trip of another route than the one named, and an unknown route
        List<TripTransfer> invalid = List.of(trips("S", "Q-0", "S", "unknown", TripTransfer.Type.NOT_POSSIBLE, 0),
                new TripTransfer("S", "R", "Q-0", "S", null, null, TripTransfer.Type.NOT_POSSIBLE, 0),
                new TripTransfer("S", "unknown", null, "S", "R", null, TripTransfer.Type.NOT_POSSIBLE, 0));
        for (TripTransfer tripTransfer : invalid) {
            RaptorRouterBuilder builder = builder("A", "S", "T");
            addRoute(builder, "Q", List.of("A", "S"), new int[]{0, 10}, 7 * 60 + 50);
            addRoute(builder, "R", List.of("S", "T"), new int[]{0, 10}, 8 * 60 + 5);
            builder.addTripTransfer(tripTransfer);

            assertThrows(IllegalArgumentException.class, builder::build, tripTransfer::toString);
        }
    }

    @Test
    void aTripTransferNamingNoRouteOrTrip_isRejected() {
        RaptorRouterBuilder builder = builder("A", "S");
        assertThrows(IllegalArgumentException.class, () -> builder.addTripTransfer(
                new TripTransfer("A", null, null, "S", null, null, TripTransfer.Type.NOT_POSSIBLE, 0)));
    }

}
