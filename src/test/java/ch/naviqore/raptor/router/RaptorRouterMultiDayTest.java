package ch.naviqore.raptor.router;

import ch.naviqore.raptor.Connection;
import ch.naviqore.raptor.RaptorAlgorithm;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.LocalDate;
import java.time.LocalDateTime;
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

    private static final LocalDateTime REFERENCE_DAY = LocalDateTime.of(2021, 1, 1, 0, 0);
    private static final LocalDateTime PREVIOUS_DAY = REFERENCE_DAY.minusDays(1);
    private static final LocalDateTime NEXT_DAY = REFERENCE_DAY.plusDays(1);

    @NoArgsConstructor
    static class RoutePerDayMasker implements RaptorTripMaskProvider {
        private static final int SECONDS_IN_HOUR = 3600;
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
            blockedRoutes.computeIfAbsent(date, k -> new HashSet<>()).add(forwardRouteId);
            blockedRoutes.get(date).add(reverseRouteId);
        }

        @Override
        public Map<String, TripMask> getTripMask(LocalDate date) {

            Set<String> blockedRouteIds = blockedRoutes.getOrDefault(date, Set.of());

            int earliestTripTime = dayStartHour * SECONDS_IN_HOUR;
            int latestTripTime = (dayEndHour + 2) * SECONDS_IN_HOUR;

            Map<String, TripMask> tripMasks = new HashMap<>();
            for (Map.Entry<String, String[]> entry : tripIds.entrySet()) {
                String routeId = entry.getKey();
                String[] tripIds = entry.getValue();
                if (blockedRouteIds.contains(routeId)) {
                    tripMasks.put(routeId,
                            new TripMask(TripMask.NO_TRIP, TripMask.NO_TRIP, new boolean[tripIds.length]));
                } else {
                    boolean[] tripMask = new boolean[tripIds.length];
                    for (int i = 0; i < tripIds.length; i++) {
                        tripMask[i] = true;
                    }
                    tripMasks.put(routeId, new TripMask(earliestTripTime, latestTripTime, tripMask));
                }
            }

            return tripMasks;
        }
    }

    @Nested
    class PreviousDay {
        @Test
        void findDepartureConnectionFromPreviousDayService(RaptorRouterTestBuilder builder) {
            RaptorAlgorithm raptor = builder.withAddRoute1_AG().withMaxDaysToScan(3).build(5, 26);

            // connection from A to G should leave at 00:00 am. (this trip is part of the previous day service)
            List<Connection> connections = RaptorRouterTestHelpers.routeEarliestArrival(raptor, STOP_A, STOP_G,
                    REFERENCE_DAY);

            assertEquals(1, connections.size());
            Connection connection = connections.getFirst();
            RaptorRouterTestHelpers.assertEarliestArrivalConnection(connection, STOP_A, STOP_G, REFERENCE_DAY, 0, 0, 1,
                    raptor);
            assertEquals(REFERENCE_DAY, connection.getDepartureTime());
        }

        @Test
        void findArrivalConnectionFromPreviousDayService(RaptorRouterTestBuilder builder) {
            RaptorAlgorithm raptor = builder.withAddRoute1_AG().withMaxDaysToScan(3).build(5, 26);

            LocalDateTime requestedArrivalTime = REFERENCE_DAY.plusMinutes(5);

            // connection from A to G should arrive at 00:05 am. (this trip is part of the previous day service)
            List<Connection> connections = RaptorRouterTestHelpers.routeLatestDeparture(raptor, STOP_A, STOP_G,
                    requestedArrivalTime);

            assertEquals(1, connections.size());
            Connection connection = connections.getFirst();
            RaptorRouterTestHelpers.assertLatestDepartureConnection(connection, STOP_A, STOP_G, requestedArrivalTime, 0,
                    0, 1, raptor);
            assertEquals(requestedArrivalTime, connection.getArrivalTime());
            assertEquals(PREVIOUS_DAY.toLocalDate(), connection.getDepartureTime().toLocalDate());
        }
    }

    @Nested
    class MultiDay {

        @Test
        void findOnlyConnectionUsingTwoServiceDays(RaptorRouterTestBuilder builder) {

            int startOfDay = 6;
            int endOfDay = 22;

            LocalDateTime departureTime = REFERENCE_DAY.plusHours(16);

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
                    .build(startOfDay, endOfDay);

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

    }

}
