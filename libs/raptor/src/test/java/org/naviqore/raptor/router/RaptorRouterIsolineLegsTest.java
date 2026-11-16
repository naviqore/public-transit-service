package org.naviqore.raptor.router;

import org.junit.jupiter.api.Test;
import org.naviqore.raptor.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * The connections of an isoline that ride the same trip between the same stops share the leg of that ride, and each
 * rides on from it on legs of its own.
 */
class RaptorRouterIsolineLegsTest {

    private static final OffsetDateTime START_OF_DAY = RaptorRouterTestBuilder.DEFAULT_REFERENCE_DATE.atStartOfDay(
            RaptorRouterTestBuilder.DEFAULT_ZONE_ID).toOffsetDateTime();

    private static OffsetDateTime at(int hour, int minute) {
        return START_OF_DAY.plusHours(hour).plusMinutes(minute);
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

    private static List<String> tripsOf(Connection connection) {
        return connection.getRouteLegs().stream().map(Leg::getTripId).toList();
    }

    /**
     * X runs A - B at 08:00, reaching B at 08:10; Y runs B - C - D at 08:20, reaching C at 08:30 and D at 08:40.
     */
    private static RaptorAlgorithm twoRoutes() {
        RaptorRouterBuilder builder = RaptorRouter.builder(RaptorConfig.defaults());
        for (String stop : List.of("A", "B", "C", "D")) {
            builder.addStop(stop);
        }
        addRoute(builder, "X", List.of("A", "B"), new int[]{0, 10}, 8 * 60);
        addRoute(builder, "Y", List.of("B", "C", "D"), new int[]{0, 10, 20}, 8 * 60 + 20);
        return builder.build();
    }

    @Test
    void connectionsRidingTheSameTrip_shareItsLeg() {
        Map<String, Connection> isolines = twoRoutes().isolineQuery(Map.of("A", at(8, 0)), TimeType.DEPARTURE,
                QueryConfig.defaults());

        Leg toB = isolines.get("B").getLegs().getFirst();
        assertEquals("X-0", toB.getTripId());
        assertEquals("B", toB.getToStopId());
        assertSame(toB, isolines.get("C").getLegs().getFirst());
        assertSame(toB, isolines.get("D").getLegs().getFirst());
    }

    @Test
    void connectionsRidingTheSameTrip_rideOnOnLegsOfTheirOwn() {
        Map<String, Connection> isolines = twoRoutes().isolineQuery(Map.of("A", at(8, 0)), TimeType.DEPARTURE,
                QueryConfig.defaults());

        Connection toC = isolines.get("C");
        Connection toD = isolines.get("D");
        assertEquals(List.of("X-0", "Y-0"), tripsOf(toC));
        assertEquals(List.of("X-0", "Y-0"), tripsOf(toD));
        assertEquals("C", toC.getLegs().getLast().getToStopId());
        assertEquals(at(8, 30), toC.getArrivalTime());
        assertEquals("D", toD.getLegs().getLast().getToStopId());
        assertEquals(at(8, 40), toD.getArrivalTime());
    }

    @Test
    void connectionsRidingTheSameTrip_shareItsLeg_routingBackward() {
        Map<String, Connection> isolines = twoRoutes().isolineQuery(Map.of("D", at(9, 0)), TimeType.ARRIVAL,
                QueryConfig.defaults());

        Leg fromC = isolines.get("C").getLegs().getLast();
        assertEquals("Y-0", fromC.getTripId());
        assertEquals("C", fromC.getFromStopId());
        // from B the ride is another one: the same trip, boarded a stop earlier
        assertEquals("B", isolines.get("B").getLegs().getLast().getFromStopId());
        assertSame(isolines.get("B").getLegs().getLast(), isolines.get("A").getLegs().getLast());
        assertEquals(List.of("X-0", "Y-0"), tripsOf(isolines.get("A")));
        assertEquals(at(8, 0), isolines.get("A").getDepartureTime());
    }
}
