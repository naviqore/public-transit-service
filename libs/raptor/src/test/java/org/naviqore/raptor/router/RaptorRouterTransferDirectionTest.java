package org.naviqore.raptor.router;

import org.junit.jupiter.api.Test;
import org.naviqore.raptor.*;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A transfer is a walk in one direction, which may take another time than the walk back: a search departing follows the
 * walks from a stop, and a search arriving, routing backward, the walks to it, so that each walk takes as long as
 * walking it in its own direction. A to B takes a minute here, B to A ten.
 */
class RaptorRouterTransferDirectionTest {

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
        builder.addTransfer("A", "B", 60);
        builder.addTransfer("B", "A", 600);
        return builder;
    }

    /**
     * Adds a route with a trip from its first to its last stop for each of the given minutes after the start of the day
     * at its first stop, which takes the given minutes to the last.
     */
    private static void addRoute(RaptorRouterBuilder builder, String routeId, String from, String to, int travelTime,
                                 int... departures) {
        builder.addRoute(routeId, RaptorRouterTestBuilder.DEFAULT_ZONE_ID, List.of(from, to));
        for (int i = 0; i < departures.length; i++) {
            String tripId = routeId + "-" + i;
            builder.addTrip(tripId, routeId);
            builder.addStopTime(routeId, tripId, 0, from, departures[i] * 60, departures[i] * 60);
            builder.addStopTime(routeId, tripId, 1, to, (departures[i] + travelTime) * 60,
                    (departures[i] + travelTime) * 60);
        }
    }

    private static Leg onlyWalk(Connection connection) {
        assertEquals(1, connection.getWalkTransfers().size());
        return connection.getWalkTransfers().getFirst();
    }

    @Test
    void arrivalQuery_walksAShortWalkInItsDirection() {
        // X - A at 07:50, arriving 08:00, a minute's walk to B, B - Y at 08:02
        RaptorRouterBuilder builder = builder("X", "A", "B", "Y");
        addRoute(builder, "R1", "X", "A", 10, 7 * 60 + 50);
        addRoute(builder, "R2", "B", "Y", 10, 8 * 60 + 2);
        RaptorAlgorithm raptor = builder.build();

        List<Connection> departing = raptor.connectionQuery(Map.of("X", at(7, 45)), Map.of("Y", 0), TimeType.DEPARTURE,
                QueryConfig.defaults());
        List<Connection> arriving = raptor.connectionQuery(Map.of("Y", at(8, 20)), Map.of("X", 0), TimeType.ARRIVAL,
                QueryConfig.defaults());

        for (List<Connection> connections : List.of(departing, arriving)) {
            assertEquals(1, connections.size());
            Connection connection = connections.getFirst();
            assertEquals(at(7, 50), connection.getDepartureTime());
            assertEquals(at(8, 12), connection.getArrivalTime());
            assertEquals(2, connection.getRouteLegs().size());
            assertEquals("A", onlyWalk(connection).getFromStopId());
            assertEquals("B", onlyWalk(connection).getToStopId());
        }
    }

    @Test
    void arrivalQuery_doesNotWalkALongWalkInTheTimeOfTheWalkBack() {
        // P - B at 07:50, arriving 08:00, ten minutes' walk to A, A - Q at 08:02 and 08:15: only the later can be caught
        RaptorRouterBuilder builder = builder("P", "A", "B", "Q");
        addRoute(builder, "R3", "P", "B", 10, 7 * 60 + 50);
        addRoute(builder, "R4", "A", "Q", 10, 8 * 60 + 2, 8 * 60 + 15);
        RaptorAlgorithm raptor = builder.build();

        List<Connection> departing = raptor.connectionQuery(Map.of("P", at(7, 45)), Map.of("Q", 0), TimeType.DEPARTURE,
                QueryConfig.defaults());
        assertEquals(1, departing.size());
        assertEquals(at(8, 25), departing.getFirst().getArrivalTime());

        // arriving by 08:20 would take the trip at 08:02, which the walk of ten minutes cannot catch
        assertTrue(
                raptor.connectionQuery(Map.of("Q", at(8, 20)), Map.of("P", 0), TimeType.ARRIVAL, QueryConfig.defaults())
                        .isEmpty());

        List<Connection> arriving = raptor.connectionQuery(Map.of("Q", at(8, 30)), Map.of("P", 0), TimeType.ARRIVAL,
                QueryConfig.defaults());
        assertEquals(1, arriving.size());
        assertEquals(at(8, 25), arriving.getFirst().getArrivalTime());
        Leg walk = onlyWalk(arriving.getFirst());
        assertEquals("B", walk.getFromStopId());
        assertEquals(Duration.ofMinutes(10), Duration.between(walk.getDepartureTime(), walk.getArrivalTime()));
    }

    @Test
    void arrivalIsolines_walkAShortWalkInItsDirection() {
        RaptorRouterBuilder builder = builder("X", "A", "B", "Y");
        addRoute(builder, "R1", "X", "A", 10, 7 * 60 + 50);
        addRoute(builder, "R2", "B", "Y", 10, 8 * 60 + 2);
        RaptorAlgorithm raptor = builder.build();

        Map<String, Connection> isolines = raptor.isolineQuery(Map.of("Y", at(8, 20)), TimeType.ARRIVAL,
                QueryConfig.defaults());

        Connection fromX = isolines.get("X");
        assertNotNull(fromX);
        assertEquals(at(7, 50), fromX.getDepartureTime());
        assertEquals("A", onlyWalk(fromX).getFromStopId());
    }

    @Test
    void arrivalRangeQuery_walksToItsSourceStopsInTheirDirection() {
        // K - A at 08:02 and 08:17, arriving at 08:12 and 08:27, and a minute's walk to B, the destination
        RaptorRouterBuilder builder = builder("K", "A", "B");
        addRoute(builder, "R5", "K", "A", 10, 8 * 60 + 2, 8 * 60 + 17);
        RaptorAlgorithm raptor = builder.build();

        List<Connection> connections = raptor.connectionRangeQuery(Map.of("B", at(8, 30)), Map.of("K", 0),
                TimeType.ARRIVAL, Duration.ofMinutes(30), QueryConfig.defaults());

        assertEquals(List.of(at(8, 13), at(8, 28)), connections.stream().map(Connection::getArrivalTime).toList());
        for (Connection connection : connections) {
            Leg walk = onlyWalk(connection);
            assertEquals("A", walk.getFromStopId());
            assertEquals(Duration.ofMinutes(1), Duration.between(walk.getDepartureTime(), walk.getArrivalTime()));
        }
    }
}
