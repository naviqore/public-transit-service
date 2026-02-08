package org.naviqore.raptor.router;

import lombok.NoArgsConstructor;
import lombok.Setter;
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

}
