package ch.naviqore.raptor.router;

import ch.naviqore.raptor.RaptorAlgorithm;
import ch.naviqore.utils.cache.EvictionCache;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.*;

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
 * <li>R5: A, B, C, D, E, F, P, O, N, K, J, I, B, H</li>
 * </ul>
 * <p>
 * Transfers:
 * <ul>
 * <li>N <--> D: 60*10</li>
 * <li>L <--> R: 60*3</li>
 * </ul>
 */
@NoArgsConstructor
public class RaptorRouterTestBuilder {

    static final int SECONDS_IN_HOUR = 3600;
    static final int DAY_START_HOUR = 5;
    static final int DAY_END_HOUR = 25;

    static final int DEFAULT_TIME_BETWEEN_STOPS = 5;
    static final int DEFAULT_DWELL_TIME = 1;
    static final int DEFAULT_HEADWAY_TIME = 15;
    static final int DEFAULT_OFFSET = 0;

    private final List<Route> routes = new ArrayList<>();
    private final List<Transfer> transfers = new ArrayList<>();
    private int sameStopTransferTime = 120;

    private int maxDaysToScan = 1;
    private RaptorTripMaskProvider tripMaskProvider = new NoMaskProvider();

    private static RaptorAlgorithm build(List<Route> routes, List<Transfer> transfers, int dayStart, int dayEnd,
                                         int sameStopTransferTime, int daysToScan,
                                         RaptorTripMaskProvider maskProvider) {
        RaptorRouterBuilder builder = new RaptorRouterBuilder(sameStopTransferTime, daysToScan, maskProvider,
                daysToScan, EvictionCache.Strategy.LRU);
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
            int timestamp = dayStart * SECONDS_IN_HOUR + route.firstDepartureOffset * 60;
            while (timestamp < dayEnd * SECONDS_IN_HOUR) {

                // add trips
                String tripIdF = String.format("%s-F-%s", route.id, tripCount);
                String tripIdR = String.format("%s-R-%s", route.id, tripCount);
                builder.addTrip(tripIdF, routeIdF).addTrip(tripIdR, routeIdR);
                tripCount++;

                // add stop times
                int departureTimestamp = timestamp;
                // first stop of trip has no arrival time
                int arrivalTimestamp = departureTimestamp;
                for (int i = 0; i < route.stops.size(); i++) {
                    if (i + 1 == route.stops.size()) {
                        // last stop of trip has no departure time
                        departureTimestamp = arrivalTimestamp;
                    }
                    builder.addStopTime(routeIdF, tripIdF, i, route.stops.get(i), arrivalTimestamp, departureTimestamp);
                    builder.addStopTime(routeIdR, tripIdR, i, route.stops.get(route.stops.size() - 1 - i),
                            arrivalTimestamp, departureTimestamp);

                    arrivalTimestamp = departureTimestamp + route.travelTimeBetweenStops * 60;
                    departureTimestamp = arrivalTimestamp + route.dwellTimeAtSTop * 60;
                }

                timestamp += route.headWayTime * 60;
            }
        }

        for (Transfer transfer : transfers) {
            builder.addTransfer(transfer.sourceStop, transfer.targetStop, transfer.duration * 60);
            builder.addTransfer(transfer.targetStop, transfer.sourceStop, transfer.duration * 60);
        }

        return builder.build();
    }

    public RaptorRouterTestBuilder withAddRoute1_AG() {
        return withAddRoute1_AG(DEFAULT_OFFSET, DEFAULT_HEADWAY_TIME, DEFAULT_TIME_BETWEEN_STOPS, DEFAULT_DWELL_TIME);
    }

    public RaptorRouterTestBuilder withAddRoute1_AG(int offset, int headway, int travelTime, int dwellTime) {
        return withAddRoute1_AG("R1", offset, headway, travelTime, dwellTime);
    }

    public RaptorRouterTestBuilder withAddRoute1_AG(String routeId, int offset, int headway, int travelTime,
                                                    int dwellTime) {
        routes.add(
                new Route(routeId, List.of("A", "B", "C", "D", "E", "F", "G"), offset, headway, travelTime, dwellTime));
        return this;
    }

    public RaptorRouterTestBuilder withAddRoute2_HL() {
        routes.add(new Route("R2", List.of("H", "B", "I", "J", "K", "L")));
        return this;
    }

    public RaptorRouterTestBuilder withAddRoute3_MQ() {
        routes.add(new Route("R3", List.of("M", "K", "N", "O", "P", "Q")));
        return this;
    }

    public RaptorRouterTestBuilder withAddRoute3_MQ(int offset, int headway, int travelTime, int dwellTime) {
        routes.add(new Route("R3", List.of("M", "K", "N", "O", "P", "Q"), offset, headway, travelTime, dwellTime));
        return this;
    }

    public RaptorRouterTestBuilder withAddRoute4_RS() {
        routes.add(new Route("R4", List.of("R", "P", "F", "S")));
        return this;
    }

    public RaptorRouterTestBuilder withAddRoute5_AH_selfIntersecting() {
        routes.add(new Route("R5", List.of("A", "B", "C", "D", "E", "F", "P", "O", "N", "K", "J", "I", "B", "H")));
        return this;
    }

    public RaptorRouterTestBuilder withAddTransfer1_ND() {
        return withAddTransfer1_ND(60);
    }

    public RaptorRouterTestBuilder withAddTransfer1_ND(int duration) {
        transfers.add(new Transfer("N", "D", duration));
        return this;
    }

    public RaptorRouterTestBuilder withAddTransfer2_LR() {
        transfers.add(new Transfer("L", "R", 30));
        return this;
    }

    public RaptorRouterTestBuilder withAddTransfer(String sourceStop, String targetStop, int duration) {
        transfers.add(new Transfer(sourceStop, targetStop, duration));
        return this;
    }

    public RaptorRouterTestBuilder withSameStopTransferTime(int time) {
        this.sameStopTransferTime = time;
        return this;
    }

    public RaptorRouterTestBuilder withMaxDaysToScan(int days) {
        this.maxDaysToScan = days;
        return this;
    }

    public RaptorRouterTestBuilder withTripMaskProvider(RaptorTripMaskProvider provider) {
        this.tripMaskProvider = provider;
        return this;
    }

    public RaptorAlgorithm build() {
        return build(routes, transfers, DAY_START_HOUR, DAY_END_HOUR, sameStopTransferTime, maxDaysToScan,
                tripMaskProvider);
    }

    RaptorAlgorithm build(int startOfDay, int endOfDay) {
        return build(routes, transfers, startOfDay, endOfDay, sameStopTransferTime, maxDaysToScan, tripMaskProvider);
    }

    public RaptorAlgorithm buildWithDefaults() {
        return this.withAddRoute1_AG()
                .withAddRoute2_HL()
                .withAddRoute3_MQ()
                .withAddRoute4_RS()
                .withAddTransfer1_ND()
                .withAddTransfer2_LR()
                .build();
    }

    /**
     * Route.
     *
     * @param id                     the route id.
     * @param stops                  the stops of the route.
     * @param firstDepartureOffset   the time of the first departure in minutes after the start of the day.
     * @param headWayTime            the time between the trip departures in minutes.
     * @param travelTimeBetweenStops the travel time between stops in minutes.
     * @param dwellTimeAtSTop        the dwell time at a stop in minutes (time between arrival and departure).
     */
    private record Route(String id, List<String> stops, int firstDepartureOffset, int headWayTime,
                         int travelTimeBetweenStops, int dwellTimeAtSTop) {

        public Route(String id, List<String> stops) {
            this(id, stops, DEFAULT_OFFSET, DEFAULT_HEADWAY_TIME, DEFAULT_TIME_BETWEEN_STOPS, DEFAULT_DWELL_TIME);
        }

    }

    /**
     * Transfer.
     *
     * @param sourceStop the id of the source stop.
     * @param targetStop the id of the target stop.
     * @param duration   the (walking) duration of the transfer between stops in minutes.
     */
    private record Transfer(String sourceStop, String targetStop, int duration) {
    }

    /**
     * No mask provider for testing.
     */
    @Setter
    @NoArgsConstructor
    static class NoMaskProvider implements RaptorTripMaskProvider {

        Map<String, String[]> tripIds = null;

        @Override
        public String getServiceIdForDate(LocalDate date) {
            return "NoMask";
        }

        @Override
        public RaptorDayMask getTripMask(LocalDate date) {
            int earliestTripTime = DAY_START_HOUR * SECONDS_IN_HOUR;
            int latestTripTime = (DAY_END_HOUR + 2) * SECONDS_IN_HOUR;

            Map<String, TripMask> tripMasks = new HashMap<>();
            for (Map.Entry<String, String[]> entry : tripIds.entrySet()) {
                String routeId = entry.getKey();
                String[] tripIds = entry.getValue();
                boolean[] tripMask = new boolean[tripIds.length];
                for (int i = 0; i < tripIds.length; i++) {
                    tripMask[i] = true;
                }
                tripMasks.put(routeId, new TripMask(earliestTripTime, latestTripTime, tripMask));
            }

            return new RaptorDayMask(getServiceIdForDate(date), date, earliestTripTime, latestTripTime, tripMasks);
        }
    }

}
