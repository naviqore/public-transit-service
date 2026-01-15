package org.naviqore.raptor.router;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.naviqore.raptor.Connection;
import org.naviqore.raptor.Leg;
import org.naviqore.raptor.QueryConfig;
import org.naviqore.raptor.RaptorAlgorithm;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test class to test all transfer handling rules in the QueryConfig
 */
@ExtendWith(RaptorRouterTestExtension.class)
public class RaptorRouterTransferBehaviorTest {

    private static final int DAY_START_HOUR = 8;
    private static final int DAY_END_HOUR = 9;

    static class TransferBehaviorHelpers {
        /**
         * Tests will only use a simplified version of a raptor schedule. Focus will lie on the stop sequence A - B - C,
         * which are connected by route 1 (A-G) and have transfers between each-other. Therefore, allowing completing
         * trips between all stops depending on the query configuration.
         */
        static RaptorAlgorithm prepareRouter(RaptorRouterTestBuilder builder, int routeTimeBetweenStops,
                                             int abTransferTime, int bcTransferTime) {
            builder.withAddRoute1_AG(0, 15, routeTimeBetweenStops, 0);
            builder.withAddTransfer("A", "B", abTransferTime);
            builder.withAddTransfer("B", "C", bcTransferTime);
            builder.withMaxDaysToScan(1);
            builder.withServiceDayRange(DAY_START_HOUR, DAY_END_HOUR);
            return builder.build();
        }

        static RaptorAlgorithm prepareRouter(RaptorRouterTestBuilder builder, int routeTimeBetweenStops,
                                             int transferTime) {
            return prepareRouter(builder, routeTimeBetweenStops, transferTime, transferTime);
        }

        static List<Connection> routeBetweenStops(RaptorAlgorithm router, String stop1, String stop2,
                                                  QueryConfig config) {
            OffsetDateTime startTime = RaptorRouterTestBuilder.DEFAULT_REFERENCE_DATE.atTime(DAY_START_HOUR, 0)
                    .atZone(RaptorRouterTestBuilder.DEFAULT_ZONE_ID)
                    .toOffsetDateTime();
            return routeBetweenStops(router, stop1, stop2, startTime, config);
        }

        static List<Connection> routeBetweenStops(RaptorAlgorithm router, String stop1, String stop2,
                                                  OffsetDateTime startTime, QueryConfig config) {
            return RaptorRouterTestHelpers.routeEarliestArrival(router, stop1, stop2, startTime, config);
        }
    }

    @Nested
    class SourceTransferRelaxation {

        @Test
        void connectBetweenStops_withSourceTransfer(RaptorRouterTestBuilder builder) {
            // setting route time between stops greater han transfer time between stops allows for the possibility
            // that the transfer allows catching up with an already departed route trip at the next stop
            // --> source transfer
            RaptorAlgorithm router = TransferBehaviorHelpers.prepareRouter(builder, 10, 5);
            QueryConfig config = new QueryConfig();
            config.setAllowSourceTransfer(true);

            // first trip leaves "A" at start time (8:00 AM) and arrives "B" after 10 minutes (8:10 AM). if start
            // time is set to 08:01 AM and the transfer time to B is 5 minutes B can be reached at 8:06 AM, allowing to
            // embark the route for the remaining trip
            OffsetDateTime startTime = RaptorRouterTestBuilder.DEFAULT_REFERENCE_DATE.atTime(DAY_START_HOUR, 1)
                    .atZone(RaptorRouterTestBuilder.DEFAULT_ZONE_ID)
                    .toOffsetDateTime();
            List<Connection> connections = TransferBehaviorHelpers.routeBetweenStops(router, "A", "C", startTime,
                    config);

            assertEquals(1, connections.size());
            Connection connection = connections.getFirst();
            Leg firstLeg = connection.getLegs().getFirst();
            assertEquals(Leg.Type.WALK_TRANSFER, firstLeg.getType());
        }

        @Test
        void connectBetweenStops_withoutSourceTransfer(RaptorRouterTestBuilder builder) {
            // setting route time between stops greater han transfer time between stops allows for the possibility
            // that the transfer allows catching up with an already departed route trip at the next stop
            // --> source transfer
            RaptorAlgorithm router = TransferBehaviorHelpers.prepareRouter(builder, 10, 5);
            QueryConfig config = new QueryConfig();
            // however this test should check that source transfers can be disabled, i.e. the slower solution should be
            // returned
            config.setAllowSourceTransfer(false);

            // first trip leaves "A" at start time (8:00 AM), second trip leaves "A" at 08:15 AM. Since no transfers
            // from the source stop are allowed, the solution must start with the 8:15 AM trip when the start time is
            // set to 08:01 AM.
            OffsetDateTime startTime = RaptorRouterTestBuilder.DEFAULT_REFERENCE_DATE.atTime(DAY_START_HOUR, 1)
                    .atZone(RaptorRouterTestBuilder.DEFAULT_ZONE_ID)
                    .toOffsetDateTime();
            List<Connection> connections = TransferBehaviorHelpers.routeBetweenStops(router, "A", "C", startTime,
                    config);

            assertEquals(1, connections.size());
            Connection connection = connections.getFirst();
            Leg firstLeg = connection.getLegs().getFirst();
            assertEquals(Leg.Type.ROUTE, firstLeg.getType());

            // check that departure time is 08:15 AM (Using Instant comparison to ignore TimeZone offset differences)
            assertEquals(startTime.plusMinutes(14).toInstant(), connection.getDepartureTime().toInstant());
        }

    }

    @Nested
    class TargetTransferRelaxation {

        @Test
        void connectBetweenStops_withTargetTransfer(RaptorRouterTestBuilder builder) {
            // setting route time between stops greater han transfer time between stops allows for the possibility
            // that the transfer allows passing a route trip to arrive earlier at the final destination
            // --> target transfer
            RaptorAlgorithm router = TransferBehaviorHelpers.prepareRouter(builder, 10, 30, 5);
            QueryConfig config = new QueryConfig();
            config.setAllowTargetTransfer(true);

            // first trip leaves "A" at start time (8:00 AM) and arrives "B" after 10 minutes (8:10 AM) and continues
            // on to "C" to arrive at 08:20. However, walking from B-C would allow arriving C at 08:15.
            List<Connection> connections = TransferBehaviorHelpers.routeBetweenStops(router, "A", "C", config);

            assertEquals(1, connections.size());
            Connection connection = connections.getFirst();
            Leg lastLeg = connection.getLegs().getLast();
            assertEquals(Leg.Type.WALK_TRANSFER, lastLeg.getType());

            // convert Result to Test Zone before checking the Hour
            assertEquals(DAY_START_HOUR,
                    connection.getArrivalTime().atZoneSameInstant(RaptorRouterTestBuilder.DEFAULT_ZONE_ID).getHour());
            assertEquals(15, connection.getArrivalTime().getMinute());
        }

        @Test
        void connectBetweenStops_withoutTargetTransfer(RaptorRouterTestBuilder builder) {
            // setting route time between stops greater han transfer time between stops allows for the possibility
            // that the transfer allows passing a route trip to arrive earlier at the final destination
            // --> target transfer
            RaptorAlgorithm router = TransferBehaviorHelpers.prepareRouter(builder, 10, 30, 5);
            QueryConfig config = new QueryConfig();
            config.setAllowTargetTransfer(false);

            // first trip leaves "A" at start time (8:00 AM) and arrives "B" after 10 minutes (8:10 AM) and continues
            // on to "C" to arrive at 08:20. However, walking from B-C would allow arriving C at 08:15. However, since
            // target transfers are not allowed, arrival time should be 08:20.
            List<Connection> connections = TransferBehaviorHelpers.routeBetweenStops(router, "A", "C", config);

            assertEquals(1, connections.size());
            Connection connection = connections.getFirst();
            Leg lastLeg = connection.getLegs().getLast();
            assertEquals(Leg.Type.ROUTE, lastLeg.getType());

            // Convert Result to Test Zone before checking the Hour
            assertEquals(DAY_START_HOUR,
                    connection.getArrivalTime().atZoneSameInstant(RaptorRouterTestBuilder.DEFAULT_ZONE_ID).getHour());
            assertEquals(20, connection.getArrivalTime().getMinute());
        }

    }

}
