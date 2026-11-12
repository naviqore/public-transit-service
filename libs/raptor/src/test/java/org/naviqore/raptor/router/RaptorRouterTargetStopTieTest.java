package org.naviqore.raptor.router;

import org.junit.jupiter.api.Test;
import org.naviqore.raptor.*;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A feed may join the stops of a station by transfers of no time: here the station S and its platform S:1, which comes
 * after S in the order of the stops. Of the target stops reached equally well, a connection ends with its trip at the
 * platform (routing backward: begins with it), not with a walk of no time to the station.
 */
class RaptorRouterTargetStopTieTest {

    private static final OffsetDateTime START_OF_DAY = RaptorRouterTestBuilder.DEFAULT_REFERENCE_DATE.atStartOfDay(
            RaptorRouterTestBuilder.DEFAULT_ZONE_ID).toOffsetDateTime();

    private static OffsetDateTime at(int hour, int minute) {
        return START_OF_DAY.plusHours(hour).plusMinutes(minute);
    }

    /**
     * A - S:1 leaving A at 08:00 and reaching S:1 at 08:10, and S:1 - A leaving S:1 at 08:30 and reaching A at 08:40.
     */
    private static RaptorAlgorithm build() {
        RaptorRouterBuilder builder = RaptorRouter.builder(RaptorConfig.defaults());
        List.of("A", "S", "S:1").forEach(builder::addStop);
        builder.addTransfer("S", "S:1", 0);
        builder.addTransfer("S:1", "S", 0);
        addTrip(builder, "R1", "A", "S:1", 8 * 60);
        addTrip(builder, "R2", "S:1", "A", 8 * 60 + 30);
        return builder.build();
    }

    private static void addTrip(RaptorRouterBuilder builder, String routeId, String from, String to, int departure) {
        builder.addRoute(routeId, RaptorRouterTestBuilder.DEFAULT_ZONE_ID, List.of(from, to));
        builder.addTrip(routeId + "-0", routeId);
        builder.addStopTime(routeId, routeId + "-0", 0, from, departure * 60, departure * 60);
        builder.addStopTime(routeId, routeId + "-0", 1, to, (departure + 10) * 60, (departure + 10) * 60);
    }

    private static Connection onlyConnection(List<Connection> connections) {
        assertEquals(1, connections.size());
        return connections.getFirst();
    }

    @Test
    void departing_endsWithTheTrip() {
        Connection connection = onlyConnection(
                build().connectionQuery(Map.of("A", at(7, 55)), Map.of("S", 0, "S:1", 0), TimeType.DEPARTURE,
                        QueryConfig.defaults()));

        assertEquals(1, connection.getLegs().size());
        assertTrue(connection.getWalkTransfers().isEmpty());
        assertEquals("S:1", connection.getToStopId());
        assertEquals(at(8, 10), connection.getArrivalTime());
    }

    @Test
    void arriving_beginsWithTheTrip() {
        Connection connection = onlyConnection(
                build().connectionQuery(Map.of("A", at(8, 45)), Map.of("S", 0, "S:1", 0), TimeType.ARRIVAL,
                        QueryConfig.defaults()));

        assertEquals(1, connection.getLegs().size());
        assertTrue(connection.getWalkTransfers().isEmpty());
        assertEquals("S:1", connection.getFromStopId());
        assertEquals(at(8, 30), connection.getDepartureTime());
    }

    @Test
    void overATimeWindow_endsWithTheTrip() {
        List<Connection> departing = build().connectionRangeQuery(Map.of("A", at(7, 30)), Map.of("S", 0, "S:1", 0),
                TimeType.DEPARTURE, Duration.ofHours(1), QueryConfig.defaults());
        List<Connection> arriving = build().connectionRangeQuery(Map.of("A", at(9, 0)), Map.of("S", 0, "S:1", 0),
                TimeType.ARRIVAL, Duration.ofHours(1), QueryConfig.defaults());

        assertEquals("S:1", onlyConnection(departing).getToStopId());
        assertTrue(departing.getFirst().getWalkTransfers().isEmpty());
        assertEquals("S:1", onlyConnection(arriving).getFromStopId());
        assertTrue(arriving.getFirst().getWalkTransfers().isEmpty());
    }

    @Test
    void walkToATargetStopReachingTheTargetEarlier_isKept() {
        // from S the target is a minute closer than from S:1, so the walk of no time to S reaches it a minute earlier
        Connection connection = onlyConnection(
                build().connectionQuery(Map.of("A", at(7, 55)), Map.of("S", 60, "S:1", 120), TimeType.DEPARTURE,
                        QueryConfig.defaults()));

        assertEquals(2, connection.getLegs().size());
        Leg walk = connection.getLegs().getLast();
        assertEquals(Leg.Type.WALK_TRANSFER, walk.getType());
        assertEquals("S:1", walk.getFromStopId());
        assertEquals("S", walk.getToStopId());
    }
}
