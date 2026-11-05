package org.naviqore.raptor.router;

import lombok.NoArgsConstructor;
import lombok.Setter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.naviqore.raptor.Connection;
import org.naviqore.raptor.QueryConfig;
import org.naviqore.raptor.RaptorAlgorithm;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test class for the RaptorRouter with multi-day schedules.
 */
@ExtendWith(RaptorRouterTestExtension.class)
public class RaptorRouterMultiDayTest {

    private static final String STOP_A = "A";
    private static final String STOP_G = "G";
    private static final String STOP_Q = "Q";

    private static final OffsetDateTime REFERENCE_DAY = RaptorRouterTestBuilder.DEFAULT_REFERENCE_DATE.atStartOfDay(
            RaptorRouterTestBuilder.DEFAULT_ZONE_ID).toOffsetDateTime();
    private static final OffsetDateTime PREVIOUS_DAY = REFERENCE_DAY.minusDays(1);
    private static final OffsetDateTime NEXT_DAY = REFERENCE_DAY.plusDays(1);

    /**
     * Convert UTC results from Router back to the Test's Local Zone
     */
    private static LocalDate toLocalZoneDate(OffsetDateTime dateTime) {
        return dateTime.atZoneSameInstant(RaptorRouterTestBuilder.DEFAULT_ZONE_ID).toLocalDate();
    }

    @NoArgsConstructor
    static class RoutePerDayMasker implements RaptorTripMaskProvider {
        final Map<LocalDate, Set<String>> blockedRoutes = new HashMap<>();
        @Setter
        Map<String, String[]> tripIds = null;
        @Setter
        int dayStartHour = 0;
        @Setter
        int dayEndHour = 24;

        void deactivateRouteOnDate(String routeId, LocalDate date) {
            String forwardRouteId = routeId + "-F";
            String reverseRouteId = routeId + "-R";
            blockedRoutes.computeIfAbsent(date, _ -> new HashSet<>()).add(forwardRouteId);
            blockedRoutes.get(date).add(reverseRouteId);
        }

        @Override
        public String getServiceIdForDate(LocalDate date) {
            return date.toString();
        }

        @Override
        public DayTripMask getDayTripMask(LocalDate date, QueryConfig queryConfig) {

            Set<String> blockedRouteIds = blockedRoutes.getOrDefault(date, Set.of());

            Map<String, RouteTripMask> tripMasks = new HashMap<>();
            for (Map.Entry<String, String[]> entry : tripIds.entrySet()) {
                String routeId = entry.getKey();
                String[] tripIds = entry.getValue();
                if (blockedRouteIds.contains(routeId)) {
                    tripMasks.put(routeId, new RouteTripMask(new boolean[tripIds.length]));
                } else {
                    boolean[] tripMask = new boolean[tripIds.length];
                    for (int i = 0; i < tripIds.length; i++) {
                        tripMask[i] = true;
                    }
                    tripMasks.put(routeId, new RouteTripMask(tripMask));
                }
            }

            return new DayTripMask(getServiceIdForDate(date), date, tripMasks);
        }
    }

    /**
     * Runs each trip on one service day only.
     */
    static class TripPerDayMasker implements RaptorTripMaskProvider {

        private final Map<String, LocalDate> serviceDayOfTrip;
        private Map<String, String[]> tripIds = Map.of();

        TripPerDayMasker(Map<String, LocalDate> serviceDayOfTrip) {
            this.serviceDayOfTrip = serviceDayOfTrip;
        }

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
                    tripMask[i] = date.equals(serviceDayOfTrip.get(trips[i]));
                }
                tripMasks.put(routeId, new RouteTripMask(tripMask));
            });

            return new DayTripMask(getServiceIdForDate(date), date, tripMasks);
        }
    }

    @Nested
    class PreviousDay {
        @Test
        void findDepartureConnectionFromPreviousDayService(RaptorRouterTestBuilder builder) {

            RaptorAlgorithm multiDayRaptor = builder.withAddRoute1_AG()
                    .withMaxDaysToScan(3)
                    .withServiceDayRange(5, 26)
                    .build();
            RaptorAlgorithm singleDayRaptor = builder.withMaxDaysToScan(1).withServiceDayRange(5, 26).build();

            // connection from A to G should leave at 00:00 am. (this trip is part of the previous day service)
            List<Connection> multiDayConnections = RaptorRouterTestHelpers.routeEarliestArrival(multiDayRaptor, STOP_A,
                    STOP_G, REFERENCE_DAY);
            // connection from A to G should leave at 05:00 am. (this trip is part of the reference day service)
            List<Connection> singleDayConnections = RaptorRouterTestHelpers.routeEarliestArrival(singleDayRaptor,
                    STOP_A, STOP_G, REFERENCE_DAY);

            assertEquals(1, multiDayConnections.size());
            Connection connection = multiDayConnections.getFirst();
            RaptorRouterTestHelpers.assertEarliestArrivalConnection(connection, STOP_A, STOP_G, REFERENCE_DAY, 0, 0, 1,
                    multiDayRaptor);
            assertEquals(REFERENCE_DAY.toInstant(), connection.getDepartureTime().toInstant());
            assertEquals(1, singleDayConnections.size());
            Connection singleDayConnection = singleDayConnections.getFirst();
            RaptorRouterTestHelpers.assertEarliestArrivalConnection(singleDayConnection, STOP_A, STOP_G, REFERENCE_DAY,
                    0, 0, 1, multiDayRaptor);
            assertEquals(REFERENCE_DAY.plusHours(5).toInstant(), singleDayConnection.getDepartureTime().toInstant());
        }

        @Test
        void findArrivalConnectionFromPreviousDayService(RaptorRouterTestBuilder builder) {
            RaptorAlgorithm multiDayRaptor = builder.withAddRoute1_AG()
                    .withMaxDaysToScan(3)
                    .withServiceDayRange(5, 26)
                    .build();
            RaptorAlgorithm singleDayRaptor = builder.withMaxDaysToScan(1).withServiceDayRange(5, 26).build();

            OffsetDateTime requestedArrivalTime = REFERENCE_DAY.plusMinutes(5);

            // connection from A to G should arrive at 00:05 am. (this trip is part of the previous day service)
            List<Connection> multiDayConnections = RaptorRouterTestHelpers.routeLatestDeparture(multiDayRaptor, STOP_A,
                    STOP_G, requestedArrivalTime);
            // connection from A to G should not be possible if only the reference day is considered
            List<Connection> singleDayConnections = RaptorRouterTestHelpers.routeLatestDeparture(singleDayRaptor,
                    STOP_A, STOP_G, requestedArrivalTime);

            assertEquals(1, multiDayConnections.size());
            assertEquals(0, singleDayConnections.size());
            Connection connection = multiDayConnections.getFirst();
            RaptorRouterTestHelpers.assertLatestDepartureConnection(connection, STOP_A, STOP_G, requestedArrivalTime, 0,
                    0, 1, multiDayRaptor);
            assertEquals(requestedArrivalTime.toInstant(), connection.getArrivalTime().toInstant());
            assertEquals(PREVIOUS_DAY.toLocalDate(), toLocalZoneDate(connection.getDepartureTime()));
        }
    }

    @Nested
    class NextDay {

        @Test
        void findConnectionUsingNextDayService(RaptorRouterTestBuilder builder) {
            int startOfDay = 5;
            int endOfDay = 20;

            // service runs only until 20:00!
            RaptorAlgorithm raptor = builder.withAddRoute1_AG()
                    .withMaxDaysToScan(3)
                    .withServiceDayRange(startOfDay, endOfDay)
                    .build();

            // departure time is 22:00 hence the connection from A to G should leave at the start of the next service
            // day
            OffsetDateTime departureTime = REFERENCE_DAY.plusHours(22);

            // connection from A to G should leave at 00:00 am. (this trip is part of the next day service)
            List<Connection> connections = RaptorRouterTestHelpers.routeEarliestArrival(raptor, STOP_A, STOP_G,
                    departureTime);

            assertEquals(1, connections.size());
            Connection connection = connections.getFirst();
            RaptorRouterTestHelpers.assertEarliestArrivalConnection(connection, STOP_A, STOP_G, departureTime, 0, 0, 1,
                    raptor);
            assertEquals(NEXT_DAY.plusHours(startOfDay).toInstant(), connection.getDepartureTime().toInstant());

            // confirm that this connection is not possible with 1 service day only
            RaptorAlgorithm raptorWithLessDays = builder.withMaxDaysToScan(1)
                    .withServiceDayRange(startOfDay, endOfDay)
                    .build();
            List<Connection> connectionsWithLessDays = RaptorRouterTestHelpers.routeEarliestArrival(raptorWithLessDays,
                    STOP_A, STOP_G, departureTime);
            assertEquals(0, connectionsWithLessDays.size());
        }

        @Test
        void foldInitialWalkIntoTripOfNextDayService(RaptorRouterTestBuilder builder) {
            int startOfDay = 5;
            int endOfDay = 22;

            // route 1 runs on the next day only, and A and B are a walk apart
            RoutePerDayMasker tripMaskProvider = new RoutePerDayMasker();
            tripMaskProvider.deactivateRouteOnDate("R1", PREVIOUS_DAY.toLocalDate());
            tripMaskProvider.deactivateRouteOnDate("R1", REFERENCE_DAY.toLocalDate());

            RaptorAlgorithm raptor = builder.withAddRoute1_AG()
                    .withAddTransfer(STOP_A, "B", 1)
                    .withMaxDaysToScan(3)
                    .withTripMaskProvider(tripMaskProvider)
                    .withServiceDayRange(startOfDay, endOfDay)
                    .build();

            // the router walks from A to B and boards the first trip of the next day there; folding the walk into the
            // ride must board that trip at A on the next day as well, not at its time of day on the reference day
            OffsetDateTime departureTime = REFERENCE_DAY.plusHours(4);
            List<Connection> connections = RaptorRouterTestHelpers.routeEarliestArrival(raptor, STOP_A, STOP_G,
                    departureTime);

            assertEquals(1, connections.size());
            Connection connection = connections.getFirst();
            RaptorRouterTestHelpers.assertEarliestArrivalConnection(connection, STOP_A, STOP_G, departureTime, 0, 0, 1,
                    raptor);
            assertEquals(NEXT_DAY.plusHours(startOfDay).toInstant(), connection.getDepartureTime().toInstant());
        }

    }

    @Nested
    class MultiDay {

        @Test
        void findConnectionUsingTwoServiceDays(RaptorRouterTestBuilder builder) {

            int startOfDay = 6;
            int endOfDay = 22;

            OffsetDateTime departureTime = REFERENCE_DAY.plusHours(16);

            RoutePerDayMasker tripMaskProvider = new RoutePerDayMasker();
            tripMaskProvider.setDayStartHour(startOfDay);
            tripMaskProvider.setDayEndHour(endOfDay);
            tripMaskProvider.deactivateRouteOnDate("R3", REFERENCE_DAY.toLocalDate());
            tripMaskProvider.deactivateRouteOnDate("R1", NEXT_DAY.toLocalDate());

            RaptorAlgorithm raptor = builder.withAddRoute1_AG()
                    .withAddRoute3_MQ()
                    .withAddTransfer1_ND()
                    .withMaxDaysToScan(3)
                    .withTripMaskProvider(tripMaskProvider)
                    .withServiceDayRange(startOfDay, endOfDay)
                    .build();

            // connection from A to D should leave at 16:00 pm of reference day. Walk transfer to N should happen right
            // after arrival at D. The next trip from N to Q will only be running on the next day.
            List<Connection> connections = RaptorRouterTestHelpers.routeEarliestArrival(raptor, STOP_A, STOP_Q,
                    departureTime);

            assertEquals(1, connections.size());
            Connection connection = connections.getFirst();
            RaptorRouterTestHelpers.assertEarliestArrivalConnection(connection, STOP_A, STOP_Q, departureTime, 0, 1, 2,
                    raptor);
            assertTrue(connection.getDepartureTime().isBefore(REFERENCE_DAY.plusHours(endOfDay)));
            assertTrue(connection.getArrivalTime().isAfter(NEXT_DAY.plusHours(startOfDay)));
        }

        @Test
        void findConnectionUsingFiveServiceDays(RaptorRouterTestBuilder builder) {

            int startOfDay = 6;
            int endOfDay = 22;

            int numDaysInFuture = 5;

            OffsetDateTime departureTime = REFERENCE_DAY.plusHours(16);

            RoutePerDayMasker tripMaskProvider = new RoutePerDayMasker();
            tripMaskProvider.setDayStartHour(startOfDay);
            tripMaskProvider.setDayEndHour(endOfDay);

            // deactivate all days except for the day "numDaysInFuture" days in the future
            for (int i = -1; i < numDaysInFuture; i++) {
                tripMaskProvider.deactivateRouteOnDate("R1", REFERENCE_DAY.plusDays(i).toLocalDate());
            }

            RaptorAlgorithm raptor = builder.withAddRoute1_AG()
                    .withMaxDaysToScan(numDaysInFuture + 2) // +2 for reference day and previous day
                    .withTripMaskProvider(tripMaskProvider)
                    .withServiceDayRange(startOfDay, endOfDay)
                    .build();

            // connection from A to G should leave at start of day of 5th day in the future, since that will be the
            // first active trip on this connection.
            List<Connection> connections = RaptorRouterTestHelpers.routeEarliestArrival(raptor, STOP_A, STOP_G,
                    departureTime);

            assertEquals(1, connections.size());
            Connection connection = connections.getFirst();
            RaptorRouterTestHelpers.assertEarliestArrivalConnection(connection, STOP_A, STOP_G, departureTime, 0, 0, 1,
                    raptor);
            assertEquals(REFERENCE_DAY.plusDays(numDaysInFuture).plusHours(startOfDay).toInstant(),
                    connection.getDepartureTime().toInstant());
            assertTrue(
                    connection.getArrivalTime().isAfter(REFERENCE_DAY.plusDays(numDaysInFuture).plusHours(startOfDay)));

            // confirm that no connection is found if the raptor is built with fewer days to scan
            RaptorAlgorithm raptorWithLessDays = builder.withMaxDaysToScan(numDaysInFuture + 1) // one day less
                    .withServiceDayRange(startOfDay, endOfDay).build();
            List<Connection> connectionsWithLessDays = RaptorRouterTestHelpers.routeEarliestArrival(raptorWithLessDays,
                    STOP_A, STOP_G, departureTime);
            assertEquals(0, connectionsWithLessDays.size());

        }

    }

    /**
     * A route from A to B whose trips of two service days overlap: the trip of the previous day leaves A at 25:30,
     * which is 01:30 on the reference day, and the trip of the reference day leaves A at 00:30. Both take 15 minutes.
     */
    @Nested
    class OverlappingServiceDays {

        private static final String STOP_B = "B";

        private RaptorAlgorithm raptor;

        @BeforeEach
        void setUp() {
            TripPerDayMasker tripMaskProvider = new TripPerDayMasker(
                    Map.of("previous-day", PREVIOUS_DAY.toLocalDate(), "reference-day", REFERENCE_DAY.toLocalDate()));
            RaptorRouterBuilder builder = RaptorRouter.builder(
                    RaptorConfig.builder().daysToScan(3).raptorRangeDefault(-1).maskProvider(tripMaskProvider).build());
            builder.addStop(STOP_A).addStop(STOP_B);
            builder.addRoute("R", RaptorRouterTestBuilder.DEFAULT_ZONE_ID, List.of(STOP_A, STOP_B));
            builder.addTrip("previous-day", "R").addTrip("reference-day", "R");
            builder.addStopTime("R", "previous-day", 0, STOP_A, (25 * 60 + 30) * 60, (25 * 60 + 30) * 60);
            builder.addStopTime("R", "previous-day", 1, STOP_B, (25 * 60 + 45) * 60, (25 * 60 + 45) * 60);
            builder.addStopTime("R", "reference-day", 0, STOP_A, 30 * 60, 30 * 60);
            builder.addStopTime("R", "reference-day", 1, STOP_B, 45 * 60, 45 * 60);
            raptor = builder.build();
        }

        @Test
        void findEarliestTripOfBothServiceDays() {
            // the trip of the previous day is scanned first, but leaves an hour after the trip of the reference day
            List<Connection> connections = RaptorRouterTestHelpers.routeEarliestArrival(raptor, STOP_A, STOP_B,
                    REFERENCE_DAY.plusMinutes(15));

            assertEquals(1, connections.size());
            assertEquals(REFERENCE_DAY.plusMinutes(30).toInstant(),
                    connections.getFirst().getDepartureTime().toInstant());
        }

        @Test
        void findLatestTripOfBothServiceDays() {
            // the trip of the reference day is scanned first, but arrives an hour before the trip of the previous day
            List<Connection> connections = RaptorRouterTestHelpers.routeLatestDeparture(raptor, STOP_A, STOP_B,
                    REFERENCE_DAY.plusHours(1).plusMinutes(50));

            assertEquals(1, connections.size());
            assertEquals(REFERENCE_DAY.plusHours(1).plusMinutes(30).toInstant(),
                    connections.getFirst().getDepartureTime().toInstant());
        }

        @Test
        void findTripOfReferenceDayAlthoughTripOfPreviousDayArrivesLater() {
            // only the trip of the reference day arrives in time, the earliest trip of the previous day arrives later
            List<Connection> connections = RaptorRouterTestHelpers.routeLatestDeparture(raptor, STOP_A, STOP_B,
                    REFERENCE_DAY.plusMinutes(50));

            assertEquals(1, connections.size());
            assertEquals(REFERENCE_DAY.plusMinutes(30).toInstant(),
                    connections.getFirst().getDepartureTime().toInstant());
        }
    }

}
