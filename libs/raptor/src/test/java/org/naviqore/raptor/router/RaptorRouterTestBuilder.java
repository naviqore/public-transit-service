package org.naviqore.raptor.router;

import lombok.NoArgsConstructor;
import org.naviqore.raptor.RaptorAlgorithm;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Test builder to set up a RaptorRouter instance for testing purposes.
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

    public static final ZoneId DEFAULT_ZONE_ID = ZoneId.of("Europe/Zurich");
    public static final LocalDate DEFAULT_REFERENCE_DATE = LocalDate.of(2021, 1, 1);

    static final int SECONDS_IN_HOUR = 3600;
    static final int DEFAULT_TIME_BETWEEN_STOPS = 5;
    static final int DEFAULT_DWELL_TIME = 1;
    static final int DEFAULT_HEADWAY_TIME = 15;
    static final int DEFAULT_OFFSET = 0;

    private final List<Route> routes = new ArrayList<>();
    private final List<Transfer> transfers = new ArrayList<>();

    private int serviceDayStartHour = 5;
    private int serviceDayEndHour = 25;
    private int daysToScan = 1;
    private int raptorRange = -1;
    private int defaultSameStopTransferTime = 120;
    private LocalDate referenceDate = DEFAULT_REFERENCE_DATE;

    private RaptorTripMaskProvider tripMaskProvider = new RaptorConfig.NoMaskProvider();

    public RaptorRouterTestBuilder withReferenceDate(LocalDate date) {
        this.referenceDate = date;
        return this;
    }

    public RaptorRouterTestBuilder withServiceDayRange(int startHour, int endHour) {
        this.serviceDayStartHour = startHour;
        this.serviceDayEndHour = endHour;
        return this;
    }

    public RaptorRouterTestBuilder withAddRoute1_AG() {
        return withAddRoute1_AG(DEFAULT_OFFSET, DEFAULT_HEADWAY_TIME, DEFAULT_TIME_BETWEEN_STOPS, DEFAULT_DWELL_TIME);
    }

    public RaptorRouterTestBuilder withAddRoute1_AG(int offset, int headway, int travelTime, int dwellTime) {
        return withAddRoute1_AG("R1", offset, headway, travelTime, dwellTime);
    }

    public RaptorRouterTestBuilder withAddRoute1_AG(String routeId, int offset, int headway, int travelTime,
                                                    int dwellTime) {
        routes.add(new Route(routeId, List.of("A", "B", "C", "D", "E", "F", "G"), DEFAULT_ZONE_ID, offset, headway,
                travelTime, dwellTime));
        return this;
    }

    public RaptorRouterTestBuilder withAddRoute2_HL() {
        return withAddRoute2_HL(DEFAULT_OFFSET, DEFAULT_HEADWAY_TIME, DEFAULT_TIME_BETWEEN_STOPS, DEFAULT_DWELL_TIME);
    }

    public RaptorRouterTestBuilder withAddRoute2_HL(int offset, int headway, int travelTime, int dwellTime) {
        routes.add(new Route("R2", List.of("H", "B", "I", "J", "K", "L"), DEFAULT_ZONE_ID, offset, headway, travelTime,
                dwellTime));
        return this;
    }

    public RaptorRouterTestBuilder withAddRoute3_MQ() {
        routes.add(new Route("R3", List.of("M", "K", "N", "O", "P", "Q")));
        return this;
    }

    public RaptorRouterTestBuilder withAddRoute3_MQ(int offset, int headway, int travelTime, int dwellTime) {
        routes.add(new Route("R3", List.of("M", "K", "N", "O", "P", "Q"), DEFAULT_ZONE_ID, offset, headway, travelTime,
                dwellTime));
        return this;
    }

    public RaptorRouterTestBuilder withAddRoute4_RS() {
        routes.add(new Route("R4", List.of("R", "P", "F", "S")));
        return this;
    }

    public RaptorRouterTestBuilder withAddRoute4_RS(int offset, int headway, int travelTime, int dwellTime) {
        routes.add(
                new Route("R4", List.of("R", "P", "F", "S"), DEFAULT_ZONE_ID, offset, headway, travelTime, dwellTime));
        return this;
    }

    public RaptorRouterTestBuilder withAddRoute5_AH_selfIntersecting() {
        routes.add(new Route("R5", List.of("A", "B", "C", "D", "E", "F", "P", "O", "N", "K", "J", "I", "B", "H")));
        return this;
    }

    public RaptorRouterTestBuilder withAddRoute(String id, ZoneId zoneId, List<String> stops, int offset, int headway,
                                                int travelTime, int dwellTime) {
        routes.add(new Route(id, stops, zoneId, offset, headway, travelTime, dwellTime));
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
        this.defaultSameStopTransferTime = time;
        return this;
    }

    public RaptorRouterTestBuilder withMaxDaysToScan(int days) {
        this.daysToScan = days;
        return this;
    }

    public RaptorRouterTestBuilder withRaptorRange(int raptorRange) {
        this.raptorRange = raptorRange;
        return this;
    }

    public RaptorRouterTestBuilder withTripMaskProvider(RaptorTripMaskProvider provider) {
        this.tripMaskProvider = provider;
        return this;
    }

    public RaptorAlgorithm build() {
        RaptorConfig config = new RaptorConfig();
        config.setDaysToScan(daysToScan);
        config.setDefaultSameStopTransferTime(defaultSameStopTransferTime);
        config.setMaskProvider(tripMaskProvider);
        config.setStopTimeCacheSize(daysToScan);
        config.setRaptorRange(raptorRange);

        RaptorRouterBuilder builder = new RaptorRouterBuilder(config);
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
            builder.addRoute(routeIdF, route.zoneId, route.stops);
            builder.addRoute(routeIdR, route.zoneId, route.stops.reversed());

            // add trips with service day anchor 'noon minus 12 hours'
            ZonedDateTime serviceDayAnchor = referenceDate.atTime(12, 0).atZone(route.zoneId).minusHours(12);
            ZonedDateTime currentTime = serviceDayAnchor.plusHours(serviceDayStartHour)
                    .plusMinutes(route.firstDepartureOffset);
            ZonedDateTime dayEnd = serviceDayAnchor.plusHours(serviceDayEndHour);
            int tripCount = 0;
            while (currentTime.isBefore(dayEnd)) {

                // add trips
                String tripIdF = String.format("%s-F-%s", route.id, tripCount);
                String tripIdR = String.format("%s-R-%s", route.id, tripCount);
                builder.addTrip(tripIdF, routeIdF).addTrip(tripIdR, routeIdR);
                tripCount++;

                // add stop times
                int departureTimestamp = (int) Duration.between(serviceDayAnchor, currentTime).toSeconds();
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
                currentTime = currentTime.plusMinutes(route.headWayTime);
            }
        }

        for (Transfer transfer : transfers) {
            // discovery of stops that are only part of a transfer and not a route
            if (!addedStops.contains(transfer.sourceStop)) {
                builder.addStop(transfer.sourceStop);
                addedStops.add(transfer.sourceStop);
            }
            if (!addedStops.contains(transfer.targetStop)) {
                builder.addStop(transfer.targetStop);
                addedStops.add(transfer.targetStop);
            }
            builder.addTransfer(transfer.sourceStop, transfer.targetStop, transfer.duration * 60);
            builder.addTransfer(transfer.targetStop, transfer.sourceStop, transfer.duration * 60);
        }

        return builder.build();
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

    private record Route(String id, List<String> stops, ZoneId zoneId, int firstDepartureOffset, int headWayTime,
                         int travelTimeBetweenStops, int dwellTimeAtSTop) {

        public Route(String id, List<String> stops) {
            this(id, stops, DEFAULT_ZONE_ID, DEFAULT_OFFSET, DEFAULT_HEADWAY_TIME, DEFAULT_TIME_BETWEEN_STOPS,
                    DEFAULT_DWELL_TIME);
        }

    }

    private record Transfer(String sourceStop, String targetStop, int duration) {
    }
}