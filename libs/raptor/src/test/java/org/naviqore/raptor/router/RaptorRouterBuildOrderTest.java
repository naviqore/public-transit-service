package org.naviqore.raptor.router;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.naviqore.raptor.*;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The same timetable gives the same answers, whatever order its stops, routes, trips, stop times and transfers are
 * added in, and whatever order the stops of a query are given in. The timetable holds equally good alternatives, which
 * only an order of the router's own decides:
 * <ul>
 *     <li>routes Aa and BB from A to B, with a trip at the same times;</li>
 *     <li>trips Ca and DB of route R from C to D, at the same times;</li>
 *     <li>routes P1 from E to F1 and P2 from E to F2, at the same times, and a walk of the same duration from F1 and
 *     from F2 to G.</li>
 * </ul>
 * The ids of each pair of routes and of trips have the same hash code, so that a hash map keeps them in the order they
 * were added.
 */
class RaptorRouterBuildOrderTest {

    private static final OffsetDateTime MIDNIGHT = RaptorRouterTestBuilder.DEFAULT_REFERENCE_DATE.atStartOfDay(
            RaptorRouterTestBuilder.DEFAULT_ZONE_ID).toOffsetDateTime();

    private static final List<String> STOPS = List.of("A", "B", "C", "D", "E", "F1", "F2", "G");
    private static final List<TripTimes> TRIPS = List.of(new TripTimes("Aa", "Aa-1", List.of("A", "B"), 8 * 60, 10),
            new TripTimes("BB", "BB-1", List.of("A", "B"), 8 * 60, 10),
            new TripTimes("R", "Ca", List.of("C", "D"), 9 * 60, 10),
            new TripTimes("R", "DB", List.of("C", "D"), 9 * 60, 10),
            new TripTimes("P1", "P1-1", List.of("E", "F1"), 10 * 60, 10),
            new TripTimes("P2", "P2-1", List.of("E", "F2"), 10 * 60, 10),
            new TripTimes("W", "W-1", List.of("G", "A"), 6 * 60, 10));
    private static final List<Walk> WALKS = List.of(new Walk("F1", "G", 5), new Walk("F2", "G", 5));

    private static final int SHUFFLES = 20;

    /**
     * Builds the timetable, adding its stops, routes, trips, stop times and walks each in an order drawn by the random
     * generator, or in the order they are listed if there is none.
     */
    private static RaptorAlgorithm build(@Nullable Random random) {
        RaptorRouterBuilder builder = RaptorRouter.builder(RaptorConfig.defaults());
        shuffled(STOPS, random).forEach(builder::addStop);
        Map<String, List<String>> routes = new LinkedHashMap<>();
        TRIPS.forEach(trip -> routes.putIfAbsent(trip.routeId(), trip.stops()));
        shuffled(List.copyOf(routes.keySet()), random).forEach(
                routeId -> builder.addRoute(routeId, RaptorRouterTestBuilder.DEFAULT_ZONE_ID, routes.get(routeId)));
        shuffled(TRIPS, random).forEach(trip -> builder.addTrip(trip.tripId(), trip.routeId()));
        shuffled(TRIPS.stream().flatMap(trip -> trip.stopTimes().stream()).toList(), random).forEach(
                stopTime -> stopTime.addTo(builder));
        shuffled(WALKS, random).forEach(walk -> builder.addTransfer(walk.from(), walk.to(), walk.minutes() * 60));

        return builder.build();
    }

    private static <T> List<T> shuffled(List<T> list, @Nullable Random random) {
        List<T> copy = new ArrayList<>(list);
        if (random != null) {
            Collections.shuffle(copy, random);
        }

        return copy;
    }

    /**
     * The stops of a query, in an order drawn by the random generator, or in the order they are listed if there is
     * none.
     */
    private static <V> Map<String, V> stops(Map<String, V> stops, @Nullable Random random) {
        Map<String, V> ordered = new LinkedHashMap<>();
        shuffled(stops.keySet().stream().sorted().toList(), random).forEach(stop -> ordered.put(stop, stops.get(stop)));

        return ordered;
    }

    /**
     * The answers of the router to connections between all stops departing and arriving at several times, with and
     * without a time window, to connections to and from several stops at once, and to isolines from every stop.
     */
    private static List<String> answers(RaptorAlgorithm raptor, @Nullable Random random) {
        QueryConfig config = QueryConfig.defaults();
        List<String> answers = new ArrayList<>();
        for (String source : STOPS) {
            for (String target : STOPS) {
                if (source.equals(target)) {
                    continue;
                }
                for (int hour = 7; hour <= 10; hour++) {
                    OffsetDateTime time = MIDNIGHT.plusHours(hour).plusMinutes(55);
                    answers.add(text(raptor.connectionQuery(Map.of(source, time), Map.of(target, 0), TimeType.DEPARTURE,
                            config)));
                    answers.add(text(raptor.connectionQuery(Map.of(target, time.plusMinutes(25)), Map.of(source, 0),
                            TimeType.ARRIVAL, config)));
                }
                answers.add(text(raptor.connectionRangeQuery(Map.of(source, MIDNIGHT.plusHours(7)), Map.of(target, 0),
                        TimeType.DEPARTURE, Duration.ofHours(4), config)));
            }
            answers.add(text(raptor.isolineQuery(Map.of(source, MIDNIGHT.plusHours(7)), TimeType.DEPARTURE, config)));
            answers.add(text(raptor.isolineRangeQuery(Map.of(source, MIDNIGHT.plusHours(7)), TimeType.DEPARTURE,
                    Duration.ofHours(4), config)));
        }

        OffsetDateTime beforeTen = MIDNIGHT.plusHours(9).plusMinutes(55);
        answers.add(text(raptor.connectionQuery(stops(Map.of("E", beforeTen), random),
                stops(Map.of("F1", 0, "F2", 0), random), TimeType.DEPARTURE, config)));
        OffsetDateTime tenPastTen = MIDNIGHT.plusHours(10).plusMinutes(10);
        answers.add(text(raptor.connectionQuery(stops(Map.of("F1", tenPastTen, "F2", tenPastTen), random),
                stops(Map.of("G", 0), random), TimeType.DEPARTURE, config)));

        return answers;
    }

    private static String text(List<Connection> connections) {
        return connections.stream().map(RaptorRouterBuildOrderTest::text).collect(Collectors.joining(" | "));
    }

    private static String text(Map<String, Connection> isolines) {
        return new TreeMap<>(isolines).entrySet()
                .stream()
                .map(entry -> entry.getKey() + ": " + text(entry.getValue()))
                .collect(Collectors.joining(" | "));
    }

    private static String text(Connection connection) {
        return connection.getLegs().stream().map(RaptorRouterBuildOrderTest::text).collect(Collectors.joining("; "));
    }

    private static String text(Leg leg) {
        return String.join(" ", leg.getType().name(), String.valueOf(leg.getRouteId()), String.valueOf(leg.getTripId()),
                leg.getFromStopId() + "@" + leg.getDepartureTime().toLocalTime(),
                leg.getToStopId() + "@" + leg.getArrivalTime().toLocalTime());
    }

    static IntStream seeds() {
        return IntStream.range(0, SHUFFLES);
    }

    @ParameterizedTest(name = "shuffle {0}")
    @MethodSource("seeds")
    void answers_areTheSameWhateverTheOrderOfTheInput(int seed) {
        Random random = new Random(seed);

        assertEquals(answers(build(null), null), answers(build(random), random));
    }

    @Test
    void equallyGoodAlternatives_theFirstByIdIsTaken() {
        RaptorAlgorithm raptor = build(null);
        QueryConfig config = QueryConfig.defaults();

        List<Connection> routes = raptor.connectionQuery(Map.of("A", MIDNIGHT.plusHours(7)), Map.of("B", 0),
                TimeType.DEPARTURE, config);
        List<Connection> trips = raptor.connectionQuery(Map.of("C", MIDNIGHT.plusHours(8)), Map.of("D", 0),
                TimeType.DEPARTURE, config);
        List<Connection> walks = raptor.connectionQuery(Map.of("E", MIDNIGHT.plusHours(9)), Map.of("G", 0),
                TimeType.DEPARTURE, config);

        assertEquals("ROUTE Aa Aa-1 A@08:00 B@08:10", text(routes));
        assertEquals("ROUTE R Ca C@09:00 D@09:10", text(trips));
        assertEquals("ROUTE P1 P1-1 E@10:00 F1@10:10; WALK_TRANSFER transfer_F1_G null F1@10:10 G@10:15", text(walks));
    }

    /**
     * A trip of a route from its first stop to its last, leaving at a minute of the day and taking the given minutes.
     */
    private record TripTimes(String routeId, String tripId, List<String> stops, int departure, int minutes) {

        List<StopTime> stopTimes() {
            return List.of(new StopTime(this, 0, departure), new StopTime(this, 1, departure + minutes));
        }
    }

    private record StopTime(TripTimes trip, int position, int minute) {

        void addTo(RaptorRouterBuilder builder) {
            builder.addStopTime(trip.routeId(), trip.tripId(), position, trip.stops().get(position), minute * 60,
                    minute * 60);
        }
    }

    private record Walk(String from, String to, int minutes) {
    }
}
