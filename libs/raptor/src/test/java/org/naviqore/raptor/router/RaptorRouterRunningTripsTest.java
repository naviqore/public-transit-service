package org.naviqore.raptor.router;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.naviqore.raptor.Connection;
import org.naviqore.raptor.QueryConfig;
import org.naviqore.raptor.RaptorAlgorithm;
import org.naviqore.raptor.TimeType;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntPredicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A day's stop times hold the trips running on the day only, and the trip to take at a stop is found among them: read
 * one after the other on a route that runs few trips on the day, by binary search on a route that runs many.
 * <p>
 * Both routes have 600 trips, one a minute from 06:00, each taking ten minutes from the first stop to the second. Route
 * MANY runs every seventh of them on the day, 86 trips, and route FEW three.
 */
class RaptorRouterRunningTripsTest {

    private static final OffsetDateTime MIDNIGHT = RaptorRouterTestBuilder.DEFAULT_REFERENCE_DATE.atStartOfDay(
            RaptorRouterTestBuilder.DEFAULT_ZONE_ID).toOffsetDateTime();
    private static final int TRIPS = 600;
    private static final int FIRST_DEPARTURE = 6 * 60;
    private static final int RIDE = 10;

    private static final Map<String, IntPredicate> RUNNING_TRIPS = Map.of("MANY", trip -> trip % 7 == 3, "FEW",
            trip -> trip == 100 || trip == 300 || trip == 500);
    private static final Map<String, List<String>> STOPS = Map.of("MANY", List.of("A", "B"), "FEW", List.of("C", "D"));

    private final RaptorAlgorithm raptor = build();

    private static RaptorAlgorithm build() {
        RaptorConfig config = RaptorConfig.builder().maskProvider(new RunningTripsMasker()).build();
        RaptorRouterBuilder builder = RaptorRouter.builder(config);
        STOPS.values().stream().flatMap(List::stream).sorted().forEach(builder::addStop);
        STOPS.forEach((routeId, stops) -> {
            builder.addRoute(routeId, RaptorRouterTestBuilder.DEFAULT_ZONE_ID, stops);
            for (int trip = 0; trip < TRIPS; trip++) {
                String tripId = routeId + "-" + trip;
                builder.addTrip(tripId, routeId);
                int departure = (FIRST_DEPARTURE + trip) * 60;
                builder.addStopTime(routeId, tripId, 0, stops.getFirst(), departure, departure);
                builder.addStopTime(routeId, tripId, 1, stops.getLast(), departure + RIDE * 60, departure + RIDE * 60);
            }
        });
        return builder.build();
    }

    /**
     * The departures of the running trips of a route from its first stop, in minutes after midnight.
     */
    private static IntStream runningDepartures(String routeId) {
        return IntStream.range(0, TRIPS).filter(RUNNING_TRIPS.get(routeId)).map(trip -> FIRST_DEPARTURE + trip);
    }

    /**
     * Times before, at, between and after the running trips of each route, in minutes after midnight.
     */
    static Stream<Arguments> times() {
        return Stream.of(
                // before the first trip, at the first running trip and just after it, at a trip not running, before
                // and at the last running trip, and after it
                Arguments.of("MANY",
                        List.of(5 * 60, 6 * 60 + 3, 6 * 60 + 4, 9 * 60 + 31, 15 * 60 + 57, 15 * 60 + 58, 15 * 60 + 59,
                                23 * 60)), Arguments.of("FEW",
                        List.of(5 * 60, 7 * 60 + 40, 7 * 60 + 41, 9 * 60 + 31, 14 * 60 + 19, 14 * 60 + 20, 14 * 60 + 21,
                                23 * 60)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("times")
    void departure_takesTheFirstRunningTripLeavingAtOrAfterIt(String routeId, List<Integer> departures) {
        for (int departure : departures) {
            List<Integer> expected = runningDepartures(routeId).filter(time -> time >= departure)
                    .limit(1)
                    .boxed()
                    .toList();

            List<Connection> connections = raptor.connectionQuery(
                    Map.of(STOPS.get(routeId).getFirst(), MIDNIGHT.plusMinutes(departure)),
                    Map.of(STOPS.get(routeId).getLast(), 0), TimeType.DEPARTURE, QueryConfig.defaults());

            assertEquals(expected.stream().map(MIDNIGHT::plusMinutes).toList(),
                    connections.stream().map(Connection::getDepartureTime).toList(), "departing at " + departure);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("times")
    void arrival_takesTheLastRunningTripArrivingAtOrBeforeIt(String routeId, List<Integer> arrivals) {
        for (int arrival : arrivals) {
            List<Integer> expected = runningDepartures(routeId).map(time -> time + RIDE)
                    .filter(time -> time <= arrival)
                    .boxed()
                    .toList()
                    .reversed()
                    .stream()
                    .limit(1)
                    .toList();

            List<Connection> connections = raptor.connectionQuery(
                    Map.of(STOPS.get(routeId).getLast(), MIDNIGHT.plusMinutes(arrival)),
                    Map.of(STOPS.get(routeId).getFirst(), 0), TimeType.ARRIVAL, QueryConfig.defaults());

            assertEquals(expected.stream().map(MIDNIGHT::plusMinutes).toList(),
                    connections.stream().map(Connection::getArrivalTime).toList(), "arriving at " + arrival);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("times")
    void timeWindow_reportsTheRunningTripsLeavingInIt(String routeId, List<Integer> starts) {
        // no running trip leaves exactly at the end of a window of 61 minutes from these starts
        int window = 61;
        for (int start : starts) {
            List<Integer> expected = runningDepartures(routeId).filter(time -> time >= start && time < start + window)
                    .boxed()
                    .toList();

            List<Connection> connections = raptor.connectionRangeQuery(
                    Map.of(STOPS.get(routeId).getFirst(), MIDNIGHT.plusMinutes(start)),
                    Map.of(STOPS.get(routeId).getLast(), 0), TimeType.DEPARTURE, Duration.ofMinutes(window),
                    QueryConfig.defaults());

            assertEquals(expected.stream().map(MIDNIGHT::plusMinutes).toList(),
                    connections.stream().map(Connection::getDepartureTime).toList(), "window from " + start);
        }
    }

    /**
     * Runs the trips of each route that {@link #RUNNING_TRIPS} names, on every day.
     */
    private static final class RunningTripsMasker implements RaptorTripMaskProvider {

        private Map<String, String[]> tripIds = Map.of();

        @Override
        public void setTripIds(Map<String, String[]> routeTripIds) {
            this.tripIds = routeTripIds;
        }

        @Override
        public String getServiceIdForDate(LocalDate date) {
            return date.toString();
        }

        @Override
        public DayTripMask getDayTripMask(LocalDate date, QueryConfig queryConfig) {
            Map<String, RouteTripMask> tripMasks = new HashMap<>();
            tripIds.forEach((routeId, trips) -> {
                boolean[] tripMask = new boolean[trips.length];
                for (int i = 0; i < trips.length; i++) {
                    int trip = Integer.parseInt(trips[i].substring(trips[i].indexOf('-') + 1));
                    tripMask[i] = RUNNING_TRIPS.get(routeId).test(trip);
                }
                tripMasks.put(routeId, new RouteTripMask(tripMask));
            });
            return new DayTripMask(getServiceIdForDate(date), date, tripMasks);
        }
    }
}
