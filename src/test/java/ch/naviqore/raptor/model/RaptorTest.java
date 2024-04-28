package ch.naviqore.raptor.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Tests for the Raptor class.
 * <p>
 * Simple example schedule for testing:
 * <pre>
 *                      M
 *                      |
 *        I ---- J ---- K ---- L      R
 *        |             |             |
 *        |             N ---- O ---- P ---- Q
 *        |                           |
 * A ---- B ---- C ---- D ---- E ---- F ---- G
 *        |                           |
 *        H                           S
 * </pre>
 * <p>
 * Routes:
 * <ul>
 * <li>R1: A, B, C, D, E, F, G</li>
 * <li>R2: H, B, I, J, K, L</li>
 * <li>R3: M, K, N, O, P, Q</li>
 * <li>R4: R, P, F, S</li>
 * </ul>
 * <p>
 * Transfers:
 * <ul>
 * <li>N <--> D: 60*10</li>
 * <li>L <--> R: 60*3</li>
 * </ul>
 *
 * @author munterfi
 */
class RaptorTest {

    private static final int DAY_START = 5 * 60 * 60;
    private static final int DAY_END = 25 * 60 * 60;
    private static final int TRAVEL_TIME = 60 * 5;
    private static final int DWELL_TIME = 60 * 2;
    private static final List<Route> ROUTES = List.of(
            new Route("R1", List.of("A", "B", "C", "D", "E", "F", "G"), 15 * 60, 60),
            new Route("R2", List.of("H", "B", "I", "J", "K", "L"), 30 * 60, 5 * 60),
            new Route("R3", List.of("M", "K", "N", "O", "P", "Q"), 15 * 60, 7 * 60),
            new Route("R4", List.of("R", "P", "F", "S"), 60 * 60, 0));
    private static final List<Transfer> TRANSFERS = List.of(new Transfer("N", "D", 60 * 10),
            new Transfer("L", "R", 60 * 3));
    private Raptor raptor;

    @BeforeEach
    void setUp() {
        Set<String> addedStops = new HashSet<>();
        RaptorBuilder builder = Raptor.builder();
        ROUTES.forEach(route -> {
            builder.addRoute(route.id + "-F");
            builder.addRoute(route.id + "-R");
            route.stops.forEach(stop -> {
                if (!addedStops.contains(stop)) {
                    builder.addStop(stop);
                    addedStops.add(stop);
                }
            });
            for (int i = 0; i < route.stops.size(); i++) {
                builder.addRouteStop(route.stops.get(i), route.id + "-F");
                builder.addRouteStop(route.stops.get(route.stops.size() - 1 - i), route.id + "-R");
            }
            int time = DAY_START + route.offset;
            while (time < DAY_END) {
                for (int i = 0; i < route.stops.size(); i++) {
                    builder.addStopTime(route.stops.get(i), route.id + "-F", time, time + TRAVEL_TIME);
                    builder.addStopTime(route.stops.get(route.stops.size() - 1 - i), route.id + "-R", time,
                            time + TRAVEL_TIME);
                    time += TRAVEL_TIME + DWELL_TIME;
                }
            }
        });
        TRANSFERS.forEach(transfer -> {
            builder.addTransfer(transfer.sourceStop, transfer.targetStop, transfer.duration);
            builder.addTransfer(transfer.targetStop, transfer.sourceStop, transfer.duration);
        });
        raptor = builder.build();
    }

    record Route(String id, List<String> stops, int headway, int offset) {
    }

    record Transfer(String sourceStop, String targetStop, int duration) {
    }

    @Nested
    class EarliestArrival {
        @Test
        void testRoutingBetweenIntersectingRoutes() {
            raptor.routeEarliestArrival("A", "Q", 8 * 60 * 60);

            // TODO: assertThat...
        }
    }

}
