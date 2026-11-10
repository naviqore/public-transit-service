package org.naviqore.service.raptor.routing;

import org.junit.jupiter.api.Test;
import org.naviqore.raptor.Connection;
import org.naviqore.raptor.QueryConfig;
import org.naviqore.raptor.RaptorAlgorithm;
import org.naviqore.raptor.TimeType;
import org.naviqore.raptor.router.RaptorConfig;
import org.naviqore.raptor.router.RaptorRouter;
import org.naviqore.raptor.router.RaptorRouterBuilder;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TravelTimeOptimizerTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Zurich");
    private static final OffsetDateTime EIGHT_AM = LocalDate.of(2026, 1, 14)
            .atTime(8, 0)
            .atZone(ZONE)
            .toOffsetDateTime();

    /**
     * R1 leaves A at 08:00 and 08:15 and reaches B five minutes later, R2 leaves B at 08:21 and 08:36 and reaches I
     * five minutes later, and R3 leaves A2 at 08:16 and reaches B at 08:18. There is no same stop transfer time.
     */
    private static RaptorAlgorithm buildRouter() {
        RaptorRouterBuilder builder = RaptorRouter.builder(
                RaptorConfig.builder().sameStopTransferDurationDefault(0).build());
        List.of("A", "A2", "B", "I").forEach(builder::addStop);
        addRoute(builder, "R1", List.of("A", "B"), List.of(List.of(0, 5), List.of(15, 20)));
        addRoute(builder, "R2", List.of("B", "I"), List.of(List.of(21, 26), List.of(36, 41)));
        addRoute(builder, "R3", List.of("A2", "B"), List.of(List.of(16, 18)));
        return builder.build();
    }

    /**
     * Adds a route with trips calling at its stops the given minutes after 08:00.
     */
    private static void addRoute(RaptorRouterBuilder builder, String routeId, List<String> stops,
                                 List<List<Integer>> minutesOfTrips) {
        builder.addRoute(routeId, ZONE, stops);
        for (int trip = 0; trip < minutesOfTrips.size(); trip++) {
            String tripId = routeId + "-" + trip;
            builder.addTrip(tripId, routeId);
            for (int i = 0; i < stops.size(); i++) {
                int seconds = (8 * 60 + minutesOfTrips.get(trip).get(i)) * 60;
                builder.addStopTime(routeId, tripId, i, stops.get(i), seconds, seconds);
            }
        }
    }

    private static List<Connection> optimize(RaptorAlgorithm raptor, Map<String, OffsetDateTime> sourceStops,
                                             Map<String, Integer> targetStops, TimeType timeType) {
        List<Connection> connections = raptor.connectionQuery(sourceStops, targetStops, timeType,
                QueryConfig.defaults());
        return new TravelTimeOptimizer(raptor, sourceStops, targetStops, timeType, QueryConfig.defaults()).optimize(
                connections);
    }

    @Test
    void leaveAsLateAsTheArrivalAllows() {
        RaptorAlgorithm raptor = buildRouter();

        // RAPTOR takes R1 at 08:00, which waits at B for R2 at 08:21 as the one at 08:15 does
        assertThat(raptor.connectionQuery(Map.of("A", EIGHT_AM), Map.of("I", 0), TimeType.DEPARTURE,
                QueryConfig.defaults()).getFirst().getDepartureTime()).isEqualTo(EIGHT_AM);

        List<Connection> connections = optimize(raptor, Map.of("A", EIGHT_AM), Map.of("I", 0), TimeType.DEPARTURE);

        assertThat(connections).hasSize(1);
        assertThat(connections.getFirst().getDepartureTime()).isEqualTo(EIGHT_AM.plusMinutes(15));
        assertThat(connections.getFirst().getArrivalTime()).isEqualTo(EIGHT_AM.plusMinutes(26));
    }

    @Test
    void arriveAsEarlyAsTheDepartureAllows_routingBackward() {
        RaptorAlgorithm raptor = buildRouter();

        // arriving at I by 08:41, RAPTOR leaves A as late as 08:15 and takes R2 at 08:36, though the one at 08:21 can
        // be caught
        List<Connection> connections = optimize(raptor, Map.of("I", EIGHT_AM.plusMinutes(41)), Map.of("A", 0),
                TimeType.ARRIVAL);

        assertThat(connections).hasSize(1);
        assertThat(connections.getFirst().getDepartureTime()).isEqualTo(EIGHT_AM.plusMinutes(15));
        assertThat(connections.getFirst().getArrivalTime()).isEqualTo(EIGHT_AM.plusMinutes(26));
    }

    @Test
    void leaveTheOriginAsLateAsTheArrivalAllows_overSourceStopsAtOtherTimes() {
        RaptorAlgorithm raptor = buildRouter();

        // A is a walk of one minute from the origin, A2 one of ten: R1 at A at 08:15 leaves the origin at 08:14, later
        // than R3 at A2 at 08:16, which leaves it at 08:06, and both reach I at 08:26
        Map<String, OffsetDateTime> sourceStops = Map.of("A", EIGHT_AM.plusMinutes(1), "A2", EIGHT_AM.plusMinutes(10));
        List<Connection> connections = optimize(raptor, sourceStops, Map.of("I", 0), TimeType.DEPARTURE);

        assertThat(connections).hasSize(1);
        assertThat(connections.getFirst().getFromStopId()).isEqualTo("A");
        assertThat(connections.getFirst().getDepartureTime()).isEqualTo(EIGHT_AM.plusMinutes(15));
        assertThat(connections.getFirst().getArrivalTime()).isEqualTo(EIGHT_AM.plusMinutes(26));
    }

}
