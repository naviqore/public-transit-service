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
        RaptorBuilder builder = Raptor.builder();
        Set<String> addedStops = new HashSet<>();

        for (Route route : routes) {
            // define route ids
            String routeIdF = route.id + "-F";
            String routeIdR = route.id + "-R";

            // add stops
            for (String stop : route.stops) {
                if (!addedStops.contains(stop)) {
                    builder.addStop(stop);
                    addedStops.add(stop);
                }
            }

            // add routes
            builder.addRoute(routeIdF, route.stops);
            builder.addRoute(routeIdR, route.stops.reversed());

            // add trips
            int tripCount = 0;
            int time = dayStart * SECONDS_IN_HOUR + route.firstDepartureOffset * 60;
            while (time < dayEnd * SECONDS_IN_HOUR) {

                // add trips
                String tripIdF = String.format("%s-F-%s", route.id, tripCount);
                String tripIdR = String.format("%s-R-%s", route.id, tripCount);
                builder.addTrip(tripIdF, routeIdF).addTrip(tripIdR, routeIdR);
                tripCount++;

                // add stop times
                int departureTime = time;
                // first stop of trip has no arrival time
                int arrivalTime = departureTime;
                for (int i = 0; i < route.stops.size(); i++) {
                    if (i + 1 == route.stops.size()) {
                        // last stop of trip has no departure time
                        departureTime = arrivalTime;
                    }
                    builder.addStopTime(routeIdF, tripIdF, i, route.stops.get(i), arrivalTime, departureTime);
                    builder.addStopTime(routeIdR, tripIdR, i, route.stops.get(route.stops.size() - 1 - i), arrivalTime,
                            departureTime);

                    arrivalTime = departureTime + route.travelTimeBetweenStops * 60;
                    departureTime = arrivalTime + route.dwellTimeAtSTop * 60;
                }

                time += route.headWayTime * 60;
            }
        }

        for (Transfer transfer : transfers) {
            builder.addTransfer(transfer.sourceStop, transfer.targetStop, transfer.duration * 60);
            builder.addTransfer(transfer.targetStop, transfer.sourceStop, transfer.duration * 60);
        }

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

    /**
     * Route, times are in minutes.
     *
     * @param headWayTime the time between the trip departures.
     */
    private record Route(String id, List<String> stops, int firstDepartureOffset, int headWayTime,
                         int travelTimeBetweenStops, int dwellTimeAtSTop) {

        public Route(String id, List<String> stops) {
            this(id, stops, 0, 15, 5, 1);
        }

    }

    /**
     * Transfer, times are in minutes.
     */
    private record Transfer(String sourceStop, String targetStop, int duration) {
    }

}
