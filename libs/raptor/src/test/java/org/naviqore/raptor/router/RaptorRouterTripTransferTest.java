package org.naviqore.raptor.router;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.naviqore.raptor.*;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Transfers between particular routes or trips: the most specific one applies to a pair of trips, the stops' walks and
 * same stop transfer times where none does, and a trip transfer that refuses a change or takes longer than the stop
 * never hides another arrival at the stop. A later trip that a trip transfer allows to change is found on another
 * service day too, and a trip boarded along a trip transfer leads to no later trip that transfer refuses. A route
 * calling twice at a stop is changed to at the call reached in time. A search ends where trip transfers lead in a
 * circle.
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
     * stops after the given minutes; trip {@code i} is named route-{@code i}.
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

    /**
     * P and Q run S - C, reaching C at 08:00; X runs C - D at 08:05 and 08:10, reaching D after 10, and Y runs D - E at
     * 08:30. P may change to the X of 08:05 only, Q to the X of 08:10 only, and only the X of 08:10 may change to Y.
     *
     * @param refusals whether trip transfers refuse the other changes, or take too long for them.
     */
    private static RaptorAlgorithm feedersOfDifferentTrips(boolean refusals) {
        RaptorRouterBuilder builder = builder("S", "C", "D", "E");
        addRoute(builder, "P", List.of("S", "C"), new int[]{0, 10}, 7 * 60 + 50);
        addRoute(builder, "Q", List.of("S", "C"), new int[]{0, 10}, 7 * 60 + 50);
        addRoute(builder, "X", List.of("C", "D"), new int[]{0, 10}, 8 * 60 + 5, 8 * 60 + 10);
        addRoute(builder, "Y", List.of("D", "E"), new int[]{0, 10}, 8 * 60 + 30);
        builder.addTripTransfer(trips("C", "P-0", "C", "X-0", TripTransfer.Type.MINIMUM_TIME, 0));
        builder.addTripTransfer(trips("C", "Q-0", "C", "X-1", TripTransfer.Type.MINIMUM_TIME, 0));
        builder.addTripTransfer(trips("D", "X-1", "D", "Y-0", TripTransfer.Type.MINIMUM_TIME, 0));
        if (refusals) {
            builder.addTripTransfer(trips("C", "P-0", "C", "X-1", TripTransfer.Type.NOT_POSSIBLE, 0));
            builder.addTripTransfer(trips("C", "Q-0", "C", "X-0", TripTransfer.Type.NOT_POSSIBLE, 0));
            builder.addTripTransfer(trips("D", "X-0", "D", "Y-0", TripTransfer.Type.NOT_POSSIBLE, 0));
        } else {
            // P misses the X of 08:10 by 5 minutes, Q the X of 08:05 by 5, and the X of 08:05 misses Y by 15
            builder.addTripTransfer(trips("C", "P-0", "C", "X-1", TripTransfer.Type.MINIMUM_TIME, 900));
            builder.addTripTransfer(trips("C", "Q-0", "C", "X-0", TripTransfer.Type.MINIMUM_TIME, 600));
            builder.addTripTransfer(trips("D", "X-0", "D", "Y-0", TripTransfer.Type.MINIMUM_TIME, 1800));
        }
        return builder.build();
    }

    /**
     * X runs A - B and Z runs B - A, both at 08:00 at both stops, and Y runs B - T at 08:30, reaching T after 10. Trip
     * transfers of the type given let X change to Z at B and Z to X at A in no time: the trips board each other.
     */
    private static RaptorAlgorithm changesInACircle(TripTransfer.@Nullable Type type) {
        RaptorRouterBuilder builder = builder("A", "B", "T");
        addRoute(builder, "X", List.of("A", "B"), new int[]{0, 0}, 8 * 60);
        addRoute(builder, "Z", List.of("B", "A"), new int[]{0, 0}, 8 * 60);
        addRoute(builder, "Y", List.of("B", "T"), new int[]{0, 10}, 8 * 60 + 30);
        if (type != null) {
            builder.addTripTransfer(trips("B", "X-0", "B", "Z-0", type, 0));
            builder.addTripTransfer(trips("A", "Z-0", "A", "X-0", type, 0));
        }
        return builder.build();
    }

    /**
     * The queries of every kind on {@link #changesInACircle}, each giving when it reaches T (leaves A, routing
     * backward), with no limit to the transfers.
     */
    private static List<Function<RaptorAlgorithm, OffsetDateTime>> queriesInACircle() {
        QueryConfig config = QueryConfig.defaults();
        Duration window = Duration.ofMinutes(10);
        return List.of(raptor -> route(raptor, Map.of("A", at(8, 0)), "T", config).getFirst().getArrivalTime(),
                raptor -> raptor.connectionRangeQuery(Map.of("A", at(8, 0)), Map.of("T", 0), TimeType.DEPARTURE,
                        window, config).getFirst().getArrivalTime(),
                raptor -> raptor.isolineQuery(Map.of("A", at(8, 0)), TimeType.DEPARTURE, config)
                        .get("T")
                        .getArrivalTime(),
                raptor -> raptor.isolineRangeQuery(Map.of("A", at(8, 0)), TimeType.DEPARTURE, window, config)
                        .get("T")
                        .getArrivalTime(),
                raptor -> raptor.connectionQuery(Map.of("T", at(8, 40)), Map.of("A", 0), TimeType.ARRIVAL, config)
                        .getFirst()
                        .getDepartureTime(),
                raptor -> raptor.isolineQuery(Map.of("T", at(8, 40)), TimeType.ARRIVAL, config)
                        .get("A")
                        .getDepartureTime());
    }

    @Test
    void changesInACircle_endAsWithoutTheTripTransfers() {
        for (TripTransfer.Type type : new TripTransfer.Type[]{TripTransfer.Type.MINIMUM_TIME,
                TripTransfer.Type.IN_SEAT}) {
            RaptorAlgorithm withoutTripTransfers = changesInACircle(null);
            RaptorAlgorithm withTripTransfers = changesInACircle(type);
            List<Function<RaptorAlgorithm, OffsetDateTime>> queries = queriesInACircle();
            for (int i = 0; i < queries.size(); i++) {
                Function<RaptorAlgorithm, OffsetDateTime> query = queries.get(i);
                List<Integer> rounds = new ArrayList<>();
                List<OffsetDateTime> times = new ArrayList<>();
                assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
                    try (RoundCounter counter = new RoundCounter()) {
                        times.add(query.apply(withoutTripTransfers));
                        rounds.add(counter.take());
                        times.add(query.apply(withTripTransfers));
                        rounds.add(counter.take());
                    }
                });
                String message = type + ", query " + i;
                assertEquals(times.getFirst(), times.getLast(), message);
                assertEquals(rounds.getFirst(), rounds.getLast(), message);
            }
        }
    }

    @Test
    void feedersOfDifferentTrips_changeEachToItsTrip() {
        for (boolean refusals : new boolean[]{true, false}) {
            RaptorAlgorithm raptor = feedersOfDifferentTrips(refusals);
            String message = refusals ? "refusals" : "minimum times";
            Duration window = Duration.ofMinutes(30);
            QueryConfig config = QueryConfig.defaults();

            List<Connection> connections = route(raptor, Map.of("S", at(7, 50)), "E", config);
            assertEquals(1, connections.size(), message);
            assertEquals(List.of("Q-0", "X-1", "Y-0"), tripsOf(connections.getFirst()), message);
            assertEquals(at(8, 40), connections.getFirst().getArrivalTime(), message);
            connections = raptor.connectionRangeQuery(Map.of("S", at(7, 30)), Map.of("E", 0), TimeType.DEPARTURE,
                    window, config);
            assertEquals(List.of("Q-0", "X-1", "Y-0"), tripsOf(connections.getFirst()), message);
            assertEquals(at(8, 40), raptor.isolineQuery(Map.of("S", at(7, 50)), TimeType.DEPARTURE, config)
                    .get("E")
                    .getArrivalTime(), message);
            assertEquals(at(8, 40),
                    raptor.isolineRangeQuery(Map.of("S", at(7, 30)), TimeType.DEPARTURE, window, config)
                            .get("E")
                            .getArrivalTime(), message);

            connections = raptor.connectionQuery(Map.of("E", at(9, 0)), Map.of("S", 0), TimeType.ARRIVAL, config);
            assertEquals(List.of("Q-0", "X-1", "Y-0"), tripsOf(connections.getFirst()), message);
            connections = raptor.connectionRangeQuery(Map.of("E", at(9, 0)), Map.of("S", 0), TimeType.ARRIVAL, window,
                    config);
            assertEquals(List.of("Q-0", "X-1", "Y-0"), tripsOf(connections.getFirst()), message);
        }
    }

    @Test
    void feedersOfDifferentTrips_changeEachToItsTrip_routingBackward() {
        // the mirror: Y runs S - D at 08:00, reaching D after 10; X runs D - C at 08:30 and 08:45, reaching C after 10;
        // P and Q run C - E at 09:00. Only the X of 08:30 may change to Q and be changed to from Y, only that of 08:45
        // to P
        RaptorRouterBuilder builder = builder("S", "D", "C", "E");
        addRoute(builder, "Y", List.of("S", "D"), new int[]{0, 10}, 8 * 60);
        addRoute(builder, "X", List.of("D", "C"), new int[]{0, 10}, 8 * 60 + 30, 8 * 60 + 45);
        addRoute(builder, "P", List.of("C", "E"), new int[]{0, 10}, 9 * 60);
        addRoute(builder, "Q", List.of("C", "E"), new int[]{0, 10}, 9 * 60);
        builder.refuseTransfers("C", "C");
        builder.refuseTransfers("D", "D");
        builder.addTripTransfer(trips("C", "X-1", "C", "P-0", TripTransfer.Type.MINIMUM_TIME, 0));
        builder.addTripTransfer(trips("C", "X-0", "C", "Q-0", TripTransfer.Type.MINIMUM_TIME, 0));
        builder.addTripTransfer(trips("D", "Y-0", "D", "X-0", TripTransfer.Type.MINIMUM_TIME, 0));
        RaptorAlgorithm raptor = builder.build();

        List<Connection> connections = raptor.connectionQuery(Map.of("E", at(9, 10)), Map.of("S", 0),
                TimeType.ARRIVAL, QueryConfig.defaults());
        assertEquals(1, connections.size());
        assertEquals(List.of("Y-0", "X-0", "Q-0"), tripsOf(connections.getFirst()));
        assertEquals(at(8, 0), raptor.isolineQuery(Map.of("E", at(9, 10)), TimeType.ARRIVAL, QueryConfig.defaults())
                .get("S")
                .getDepartureTime());
        connections = route(raptor, Map.of("S", at(8, 0)), "E", QueryConfig.defaults());
        assertEquals(List.of("Y-0", "X-0", "Q-0"), tripsOf(connections.getFirst()));
    }

    /**
     * P runs S - C at 07:50, reaching C at 08:00; X runs C - B - D at 08:05 and 08:10, reaching B after 3 and D after
     * 10, and Y runs D - E at 08:30. C and D refuse transfers, except from P to the X of 08:05 and from the X of 08:10
     * to Y: the X of 08:05 is ahead at B, but only that of 08:10 changes to Y.
     */
    private static RaptorRouterBuilder laterTripBoardedOnTheRoute(String... stops) {
        RaptorRouterBuilder builder = builder(stops);
        addRoute(builder, "P", List.of("S", "C"), new int[]{0, 10}, 7 * 60 + 50);
        addRoute(builder, "X", List.of("C", "B", "D"), new int[]{0, 3, 10}, 8 * 60 + 5, 8 * 60 + 10);
        addRoute(builder, "Y", List.of("D", "E"), new int[]{0, 10}, 8 * 60 + 30);
        builder.refuseTransfers("C", "C");
        builder.refuseTransfers("D", "D");
        builder.addTripTransfer(trips("C", "P-0", "C", "X-0", TripTransfer.Type.MINIMUM_TIME, 0));
        builder.addTripTransfer(trips("D", "X-1", "D", "Y-0", TripTransfer.Type.MINIMUM_TIME, 0));
        return builder;
    }

    @Test
    void aLaterTripBoardedAlongATripTransferOnTheRoute_changesAsItMay() {
        // F runs S - B, reaching B at 08:06, before the X of 08:05 passes, but B refuses transfers, except from F to
        // the X of 08:10
        RaptorRouterBuilder builder = laterTripBoardedOnTheRoute("S", "C", "B", "D", "E");
        addRoute(builder, "F", List.of("S", "B"), new int[]{0, 16}, 7 * 60 + 50);
        builder.refuseTransfers("B", "B");
        builder.addTripTransfer(trips("B", "F-0", "B", "X-1", TripTransfer.Type.MINIMUM_TIME, 0));
        RaptorAlgorithm raptor = builder.build();

        List<Connection> connections = route(raptor, Map.of("S", at(7, 50)), "E", QueryConfig.defaults());
        assertEquals(1, connections.size());
        assertEquals(List.of("F-0", "X-1", "Y-0"), tripsOf(connections.getFirst()));
        assertEquals(at(8, 40), connections.getFirst().getArrivalTime());
    }

    @Test
    void aLaterTripBoardedAtAStopOfTheRoute_changesAsItMay() {
        // F runs S - A, reaching A at 08:05, and walks to B in 240 s, after the X of 08:05 passes at 08:08 and in time
        // for the X of 08:10; at B, the X of 08:05 cannot change to it in 330 s
        RaptorRouterBuilder builder = laterTripBoardedOnTheRoute("S", "A", "C", "B", "D", "E");
        addRoute(builder, "F", List.of("S", "A"), new int[]{0, 15}, 7 * 60 + 50);
        builder.addTransfer("A", "B", 240);
        builder.addTransfer("B", "B", 330);
        RaptorAlgorithm raptor = builder.build();

        List<Connection> connections = route(raptor, Map.of("S", at(7, 50)), "E", QueryConfig.defaults());
        assertEquals(1, connections.size());
        assertEquals(List.of("F-0", "X-1", "Y-0"), tripsOf(connections.getFirst()));
        assertEquals(at(8, 40), raptor.isolineQuery(Map.of("S", at(7, 50)), TimeType.DEPARTURE, QueryConfig.defaults())
                .get("E")
                .getArrivalTime());
    }

    @Test
    void aFirstTripBoardedAfterAWalk_changesAsItMay_overATimeWindow() {
        // X runs A - B - C at 08:00 and 08:10, reaching B after 2 and C after 10; Y runs C - D at 08:25, reaching D
        // after 10; C refuses transfers, except from the X of 08:10 to Y. From A at 08:00, the X of 08:00 leaves in a
        // window of 5 minutes, and the X of 08:10 at B after walking there in 8, but not that of 08:10 at A
        RaptorRouterBuilder builder = builder("A", "B", "C", "D");
        addRoute(builder, "X", List.of("A", "B", "C"), new int[]{0, 2, 10}, 8 * 60, 8 * 60 + 10);
        addRoute(builder, "Y", List.of("C", "D"), new int[]{0, 10}, 8 * 60 + 25);
        builder.addTransfer("A", "B", 480);
        builder.refuseTransfers("C", "C");
        builder.addTripTransfer(trips("C", "X-1", "C", "Y-0", TripTransfer.Type.MINIMUM_TIME, 0));
        RaptorAlgorithm raptor = builder.build();

        Connection isoline = raptor.isolineRangeQuery(Map.of("A", at(8, 0)), TimeType.DEPARTURE,
                Duration.ofMinutes(5), QueryConfig.defaults()).get("D");
        assertEquals(List.of("X-1", "Y-0"), tripsOf(isoline));
        assertEquals(at(8, 35), isoline.getArrivalTime());
    }

    /**
     * F runs S - Q at 07:55, reaching Q at 08:05; L runs W - Q - R - Q - E at 07:50, calling at Q at 08:00 and again at
     * 08:10, and reaching E at 08:20. From F to L at Q, a trip transfer takes 60 s; Q refuses transfers, or takes 600 s
     * to change, longer than the trip transfer.
     */
    private static RaptorAlgorithm changesToARouteCallingTwice(boolean refusing) {
        RaptorRouterBuilder builder = builder("S", "W", "Q", "R", "E");
        addRoute(builder, "F", List.of("S", "Q"), new int[]{0, 10}, 7 * 60 + 55);
        addRoute(builder, "L", List.of("W", "Q", "R", "Q", "E"), new int[]{0, 10, 15, 20, 30}, 7 * 60 + 50);
        if (refusing) {
            builder.refuseTransfers("Q", "Q");
        } else {
            builder.addTransfer("Q", "Q", 600);
        }
        builder.addTripTransfer(trips("Q", "F-0", "Q", "L-0", TripTransfer.Type.MINIMUM_TIME, 60));
        return builder.build();
    }

    @Test
    void aRouteCallingTwiceWhereItIsChangedTo_isBoardedAtTheCallReachedInTime() {
        for (boolean refusing : new boolean[]{true, false}) {
            RaptorAlgorithm raptor = changesToARouteCallingTwice(refusing);
            String message = refusing ? "refusing" : "longer at the stop";

            List<Connection> connections = route(raptor, Map.of("S", at(7, 55)), "E", QueryConfig.defaults());
            assertEquals(1, connections.size(), message);
            assertEquals(List.of("F-0", "L-0"), tripsOf(connections.getFirst()), message);
            assertEquals(at(8, 10), connections.getFirst().getRouteLegs().getLast().getDepartureTime(), message);
            assertEquals(at(8, 20), connections.getFirst().getArrivalTime(), message);
            connections = raptor.connectionRangeQuery(Map.of("S", at(7, 50)), Map.of("E", 0), TimeType.DEPARTURE,
                    Duration.ofMinutes(10), QueryConfig.defaults());
            assertEquals(List.of("F-0", "L-0"), tripsOf(connections.getFirst()), message);
            assertEquals(at(8, 20), raptor.isolineQuery(Map.of("S", at(7, 55)), TimeType.DEPARTURE,
                    QueryConfig.defaults()).get("E").getArrivalTime(), message);

            connections = raptor.connectionQuery(Map.of("E", at(8, 30)), Map.of("S", 0), TimeType.ARRIVAL,
                    QueryConfig.defaults());
            assertEquals(List.of("F-0", "L-0"), tripsOf(connections.getFirst()), message);
        }
    }

    @Test
    void aRouteCallingTwiceWhereItChangesToAnother_isLeftAtTheCallInTime_routingBackward() {
        // L runs W - Q - R - Q - Z at 07:40, calling at Q at 07:50 and again at 08:10; G runs Q - E at 08:05, reaching
        // E after 10. From L to G at Q, a trip transfer takes 60 s; Q refuses transfers, or takes 1200 s to change
        for (boolean refusing : new boolean[]{true, false}) {
            RaptorRouterBuilder builder = builder("W", "Q", "R", "Z", "E");
            addRoute(builder, "L", List.of("W", "Q", "R", "Q", "Z"), new int[]{0, 10, 15, 30, 40}, 7 * 60 + 40);
            addRoute(builder, "G", List.of("Q", "E"), new int[]{0, 10}, 8 * 60 + 5);
            if (refusing) {
                builder.refuseTransfers("Q", "Q");
            } else {
                builder.addTransfer("Q", "Q", 1200);
            }
            builder.addTripTransfer(trips("Q", "L-0", "Q", "G-0", TripTransfer.Type.MINIMUM_TIME, 60));
            RaptorAlgorithm raptor = builder.build();
            String message = refusing ? "refusing" : "longer at the stop";

            List<Connection> connections = raptor.connectionQuery(Map.of("E", at(8, 30)), Map.of("W", 0),
                    TimeType.ARRIVAL, QueryConfig.defaults());
            assertEquals(1, connections.size(), message);
            assertEquals(List.of("L-0", "G-0"), tripsOf(connections.getFirst()), message);
            assertEquals(at(7, 50), connections.getFirst().getRouteLegs().getFirst().getArrivalTime(), message);
            connections = raptor.connectionRangeQuery(Map.of("E", at(8, 20)), Map.of("W", 0), TimeType.ARRIVAL,
                    Duration.ofMinutes(10), QueryConfig.defaults());
            assertEquals(List.of("L-0", "G-0"), tripsOf(connections.getFirst()), message);
            assertEquals(at(7, 40), raptor.isolineQuery(Map.of("E", at(8, 30)), TimeType.ARRIVAL,
                    QueryConfig.defaults()).get("W").getDepartureTime(), message);

            connections = route(raptor, Map.of("W", at(7, 40)), "E", QueryConfig.defaults());
            assertEquals(List.of("L-0", "G-0"), tripsOf(connections.getFirst()), message);
        }
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
                TimeType.DEPARTURE, Duration.ofHours(2), QueryConfig.defaults());
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
        // from trip a at P4, the trip transfer to R at P5 takes an hour, but the walk to P5 still boards R2 after 240 s
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
        // trip a ends at S at 09:00, where trip b starts at 09:00; S takes 120 s to change, and the query asks for 300 s
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

    /**
     * Q runs A - S at 08:50 and 09:50, setting down no one at S; R runs S - T at 09:00 and 10:00, picking up no one at
     * S; W runs S - U at 09:05; each trip reaches its last stop after 10 minutes. The vehicle of the Q of 08:50
     * continues as the R of 09:00, and that Q would change to W in no time, if it set down anyone at S.
     */
    private static RaptorAlgorithm inSeatWithoutDropOffOrPickUp() {
        RaptorRouterBuilder builder = builder("A", "S", "T", "U");
        builder.addRoute("Q", RaptorRouterTestBuilder.DEFAULT_ZONE_ID, List.of("A", "S"));
        builder.addRoute("R", RaptorRouterTestBuilder.DEFAULT_ZONE_ID, List.of("S", "T"));
        for (int i = 0; i < 2; i++) {
            addTripOfTwoStops(builder, "Q", "Q-" + i, List.of("A", "S"), (8 + i) * 60 + 50, true, false);
            addTripOfTwoStops(builder, "R", "R-" + i, List.of("S", "T"), (9 + i) * 60, false, true);
        }
        addRoute(builder, "W", List.of("S", "U"), new int[]{0, 10}, 9 * 60 + 5);
        builder.addTripTransfer(trips("S", "Q-0", "S", "R-0", TripTransfer.Type.IN_SEAT, 0));
        builder.addTripTransfer(trips("S", "Q-0", "S", "W-0", TripTransfer.Type.MINIMUM_TIME, 0));
        return builder.build();
    }

    /**
     * Adds a trip of two stops, leaving at the given minutes after the start of the day and reaching its last stop
     * after 10, which picks up passengers at its first stop and sets them down at its last as given.
     */
    private static void addTripOfTwoStops(RaptorRouterBuilder builder, String routeId, String tripId,
                                          List<String> stops, int start, boolean pickUp, boolean setDown) {
        builder.addTrip(tripId, routeId);
        builder.addStopTime(routeId, tripId, 0, stops.getFirst(), start * 60, start * 60, pickUp, true);
        builder.addStopTime(routeId, tripId, 1, stops.getLast(), (start + 10) * 60, (start + 10) * 60, true, setDown);
    }

    @Test
    void anInSeatTransfer_needsNoDropOffOrPickUpAtTheStop() {
        RaptorAlgorithm raptor = inSeatWithoutDropOffOrPickUp();

        List<Connection> connections = route(raptor, Map.of("A", at(8, 50)), "T", QueryConfig.defaults());
        assertEquals(1, connections.size());
        assertEquals(List.of("Q-0", "R-0"), tripsOf(connections.getFirst()));
        assertEquals(at(9, 10), connections.getFirst().getArrivalTime());

        connections = raptor.connectionQuery(Map.of("T", at(9, 10)), Map.of("A", 0), TimeType.ARRIVAL,
                QueryConfig.defaults());
        assertEquals(1, connections.size());
        assertEquals(List.of("Q-0", "R-0"), tripsOf(connections.getFirst()));
        assertEquals(at(8, 50), connections.getFirst().getDepartureTime());

        connections = raptor.connectionRangeQuery(Map.of("A", at(8, 0)), Map.of("T", 0), TimeType.DEPARTURE,
                Duration.ofHours(2), QueryConfig.defaults());
        assertEquals(List.of(List.of("Q-0", "R-0")), connections.stream().map(RaptorRouterTripTransferTest::tripsOf)
                .toList());
    }

    @Test
    void anInSeatTransfer_letsNoOneLeaveOrBoardWhereTheTripsSetDownOrPickUpNoOne() {
        RaptorAlgorithm raptor = inSeatWithoutDropOffOrPickUp();

        // Q sets down no one at S, to stay there or change to W, and R picks up no one there
        Map<String, Connection> isolines = raptor.isolineQuery(Map.of("A", at(8, 50)), TimeType.DEPARTURE,
                QueryConfig.defaults());
        assertEquals(List.of("Q-0", "R-0"), tripsOf(isolines.get("T")));
        assertFalse(isolines.containsKey("S"));
        assertFalse(isolines.containsKey("U"));
        assertTrue(route(raptor, Map.of("S", at(8, 55)), "T", QueryConfig.defaults()).isEmpty());
        // the Q of 09:50 does not continue as the R of 10:00
        assertTrue(route(raptor, Map.of("A", at(9, 50)), "T", QueryConfig.defaults()).isEmpty());

        // routing backward, the same
        isolines = raptor.isolineQuery(Map.of("T", at(9, 10)), TimeType.ARRIVAL, QueryConfig.defaults());
        assertEquals(List.of("Q-0", "R-0"), tripsOf(isolines.get("A")));
        assertFalse(isolines.containsKey("S"));
        assertTrue(raptor.connectionQuery(Map.of("U", at(9, 15)), Map.of("A", 0), TimeType.ARRIVAL,
                QueryConfig.defaults()).isEmpty());
        // the R of 10:00 does not continue the Q of 09:50, so arriving by 10:10 the rider takes the trips before
        assertEquals(List.of("Q-0", "R-0"), tripsOf(raptor.connectionQuery(Map.of("T", at(10, 10)), Map.of("A", 0),
                TimeType.ARRIVAL, QueryConfig.defaults()).getFirst()));
    }

    @Test
    void aTripTransferThatIsNotPossible_takesTheNextTrip() {
        // from trip a, the R of 08:05 is refused; the stop allows the R of 08:20
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

    /**
     * Counts the rounds of the searches while it is open, by the message the route scanner logs for each.
     */
    private static final class RoundCounter implements AutoCloseable {

        private final Logger logger = (Logger) LoggerFactory.getLogger(RouteScanner.class);
        private final ListAppender<ILoggingEvent> events = new ListAppender<>();
        private final @Nullable Level level = logger.getLevel();

        RoundCounter() {
            logger.setLevel(Level.DEBUG);
            events.start();
            logger.addAppender(events);
        }

        /**
         * The rounds counted since the last call.
         */
        int take() {
            int rounds = (int) events.list.stream()
                    .filter(event -> event.getFormattedMessage().startsWith("Scanning routes for round"))
                    .count();
            events.list.clear();
            return rounds;
        }

        @Override
        public void close() {
            logger.detachAppender(events);
            logger.setLevel(level);
        }
    }

    @Test
    void aTripTransferNamingNoRouteOrTrip_isRejected() {
        RaptorRouterBuilder builder = builder("A", "S");
        assertThrows(IllegalArgumentException.class, () -> builder.addTripTransfer(
                new TripTransfer("A", null, null, "S", null, null, TripTransfer.Type.NOT_POSSIBLE, 0)));
    }

}
