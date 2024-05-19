package ch.naviqore.raptor.model;

import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Test builder to set up a raptor for testing purposes.
 * <p>
 * Simple example schedule for testing:
 * <pre>
 *                      M
 *                      |
 *        I ---- J ---- K ---- L #### R
 *        |             |             |
 *        |             N ---- O ---- P ---- Q
 *        |             #             |
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
 */
@NoArgsConstructor
public class RaptorTestBuilder {

    static final int SECONDS_IN_HOUR = 3600;
    private static final int DAY_START_HOUR = 5;
    private static final int DAY_END_HOUR = 25;

    private final List<Route> routes = new ArrayList<>();
    private final List<Transfer> transfers = new ArrayList<>();

    private static Raptor build(List<Route> routes, List<Transfer> transfers, int dayStart, int dayEnd) {
        Set<String> addedStops = new HashSet<>();
        RaptorBuilder builder = Raptor.builder();
        routes.forEach(route -> {
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
            int time = dayStart * SECONDS_IN_HOUR + route.firstDepartureOffsetInMinutes * 60;
            while (time < dayEnd * SECONDS_IN_HOUR) {
                int departureTime = time;
                // first stop of trip has no arrival time
                int arrivalTime = 0;
                for (int i = 0; i < route.stops.size(); i++) {
                    if (i + 1 == route.stops.size()) {
                        // last stop of trip has no departure time
                        departureTime = 0;
                    }
                    builder.addStopTime(route.stops.get(i), route.id + "-F", arrivalTime, departureTime);
                    builder.addStopTime(route.stops.get(route.stops.size() - 1 - i), route.id + "-R", arrivalTime,
                            departureTime);

                    arrivalTime = departureTime + route.timeBetweenStopsInMinutes * 60;
                    departureTime = arrivalTime + route.dwellTimeInMinutes * 60;
                }
                time += route.timeBetweenDeparturesInMinutes * 60;
            }
        });
        transfers.forEach(transfer -> {
            builder.addTransfer(transfer.sourceStop, transfer.targetStop, transfer.durationInMinutes * 60);
            builder.addTransfer(transfer.targetStop, transfer.sourceStop, transfer.durationInMinutes * 60);
        });
        return builder.build();
    }

    public RaptorTestBuilder withAddRoute1_AG() {
        routes.add(new Route("R1", List.of("A", "B", "C", "D", "E", "F", "G")));
        return this;
    }

    public RaptorTestBuilder withAddRoute2_HL() {
        routes.add(new Route("R2", List.of("H", "B", "I", "J", "K", "L")));
        return this;
    }

    public RaptorTestBuilder withAddRoute3_MQ() {
        routes.add(new Route("R3", List.of("M", "K", "N", "O", "P", "Q")));
        return this;
    }

    public RaptorTestBuilder withAddRoute4_RS() {
        routes.add(new Route("R4", List.of("R", "P", "F", "S")));
        return this;
    }

    public RaptorTestBuilder withAddTransfer1_ND() {
        return withAddTransfer1_ND(60);
    }

    public RaptorTestBuilder withAddTransfer1_ND(int duration) {
        transfers.add(new Transfer("N", "D", duration));
        return this;
    }

    public RaptorTestBuilder withAddTransfer2_LR() {
        transfers.add(new Transfer("L", "R", 30));
        return this;
    }

    public Raptor build() {
        return build(routes, transfers, DAY_START_HOUR, DAY_END_HOUR);
    }

    public Raptor buildWithDefaults() {
        return this.withAddRoute1_AG()
                .withAddRoute2_HL()
                .withAddRoute3_MQ()
                .withAddRoute4_RS()
                .withAddTransfer1_ND()
                .withAddTransfer2_LR()
                .build();
    }

    private record Route(String id, List<String> stops, int firstDepartureOffsetInMinutes,
                         int timeBetweenDeparturesInMinutes, int timeBetweenStopsInMinutes, int dwellTimeInMinutes) {

        public Route(String id, List<String> stops) {
            this(id, stops, 0, 15, 5, 1);
        }

    }

    private record Transfer(String sourceStop, String targetStop, int durationInMinutes) {
    }

}
