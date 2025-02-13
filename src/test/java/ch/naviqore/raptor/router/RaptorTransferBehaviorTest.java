package ch.naviqore.raptor.router;

import ch.naviqore.raptor.Connection;
import ch.naviqore.raptor.Leg;
import ch.naviqore.raptor.QueryConfig;
import ch.naviqore.raptor.RaptorAlgorithm;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class to test all transfer handling rules in the QueryConfig
 */
@ExtendWith(RaptorRouterTestExtension.class)
public class RaptorTransferBehaviorTest {

    private static final int DAY_START_HOUR = 8;
    private static final int DAY_END_HOUR = 9;

    @Nested
    class InitialTransferRelaxation {
        @Test
        void connectBetweenStops_withInitialFootpathRelaxation(RaptorRouterTestBuilder builder) {
            RaptorAlgorithm router = TransferBehaviorHelpers.prepareRouter(builder, 5, 30);
            QueryConfig config = new QueryConfig();
            config.setDoInitialTransferRelaxation(true);

            List<Connection> connections = TransferBehaviorHelpers.routeBetweenStops(router, "A", "B", config);

            // Because initial transfer relaxation is one, this will return two solutions.
            // Round 0 best time with transfer between A-B with one transfer leg
            // Round 1 best time (faster) with route 1 from A to B with one route leg (no transfers)
            assertEquals(2, connections.size());
            Connection walkConnection = connections.getFirst();
            assertEquals(0, walkConnection.getRouteLegs().size());
            assertEquals(1, walkConnection.getWalkTransfers().size());

            Connection routeConnection = connections.getLast();
            assertEquals(1, routeConnection.getRouteLegs().size());
            assertEquals(0, routeConnection.getWalkTransfers().size());
        }

        @Test
        void connectBetweenStops_withoutInitialFootpathRelaxation(RaptorRouterTestBuilder builder) {
            RaptorAlgorithm router = TransferBehaviorHelpers.prepareRouter(builder, 5, 30);
            QueryConfig config = new QueryConfig();
            config.setDoInitialTransferRelaxation(false);

            List<Connection> connections = TransferBehaviorHelpers.routeBetweenStops(router, "A", "B", config);

            // Because nothing should happen in round 0 (no initial transfer relaxation) only one "one leg" connection
            // should be returned and since the route leg will be faster, this should be the only solution
            assertEquals(1, connections.size());
            Connection connection = connections.getFirst();
            assertEquals(1, connection.getRouteLegs().size());
            assertEquals(0, connection.getWalkTransfers().size());
        }

        @Test
        void connectBetweenStops_withoutInitialFootpathRelaxationOnlyByTransfer(RaptorRouterTestBuilder builder) {
            RaptorAlgorithm router = TransferBehaviorHelpers.prepareRouter(builder, 5, 30);
            QueryConfig config = new QueryConfig();
            config.setDoInitialTransferRelaxation(false);

            // ensure that no routes are active anymore
            LocalDateTime startTime = LocalDateTime.of(2000, 1, 1, DAY_END_HOUR + 1, 0);

            List<Connection> connections = TransferBehaviorHelpers.routeBetweenStops(router, "A", "B", startTime, config);

            // even though initial transfer relaxation is turned off, in round 1 transfer relaxation should be performed
            // from source stops (after no faster route trips were found!).
            assertEquals(1, connections.size());
            Connection connection = connections.getFirst();
            assertEquals(0, connection.getRouteLegs().size());
            assertEquals(1, connection.getWalkTransfers().size());
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
            LocalDateTime startTime = LocalDateTime.of(2000, 1, 1, DAY_START_HOUR, 1);
            List<Connection> connections = TransferBehaviorHelpers.routeBetweenStops(router, "A", "C", config);

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
            LocalDateTime startTime = LocalDateTime.of(2000, 1, 1, DAY_START_HOUR, 1);
            List<Connection> connections = TransferBehaviorHelpers.routeBetweenStops(router, "A", "C", config);

            assertEquals(1, connections.size());
            Connection connection = connections.getFirst();
            Leg firstLeg = connection.getLegs().getFirst();
            assertEquals(Leg.Type.ROUTE, firstLeg.getType());
            // check that departure time is 08:15 AM
            assertEquals(startTime.plusMinutes(14), connection.getDepartureTime());
        }

    }

    @Nested
    class TargetTransferRelaxation {

        @Test
        void connectBetweenStops_withTargetTransfer(RaptorRouterTestBuilder builder) {
            // setting route time between stops greater han transfer time between stops allows for the possibility
            // that the transfer allows passing a route trip to arrive earlier at the final destination
            // --> target transfer
            RaptorAlgorithm router = TransferBehaviorHelpers.prepareRouter(builder, 10, 5);
            QueryConfig config = new QueryConfig();
            config.setAllowTargetTransfer(true);

            // first trip leaves "A" at start time (8:00 AM) and arrives "B" after 10 minutes (8:10 AM) and continues
            // on to "C" to arrive at 08:20. However, walking from B-C would allow arriving C at 08:15.
            List<Connection> connections = TransferBehaviorHelpers.routeBetweenStops(router, "A", "C", config);

            assertEquals(1, connections.size());
            Connection connection = connections.getFirst();
            Leg lastLeg = connection.getLegs().getLast();
            assertEquals(Leg.Type.WALK_TRANSFER, lastLeg.getType());
            assertEquals(DAY_START_HOUR, connection.getArrivalTime().getHour());
            assertEquals(15, connection.getArrivalTime().getMinute());
        }

        @Test
        void connectBetweenStops_withoutInitialTransfer(RaptorRouterTestBuilder builder) {
            // setting route time between stops greater han transfer time between stops allows for the possibility
            // that the transfer allows passing a route trip to arrive earlier at the final destination
            // --> target transfer
            RaptorAlgorithm router = TransferBehaviorHelpers.prepareRouter(builder, 10, 5);
            QueryConfig config = new QueryConfig();
            config.setAllowTargetTransfer(true);

            // first trip leaves "A" at start time (8:00 AM) and arrives "B" after 10 minutes (8:10 AM) and continues
            // on to "C" to arrive at 08:20. However, walking from B-C would allow arriving C at 08:15. However, since
            // target transfers are not allowed, arrival time should be 08:20.
            List<Connection> connections = TransferBehaviorHelpers.routeBetweenStops(router, "A", "C", config);

            assertEquals(1, connections.size());
            Connection connection = connections.getFirst();
            Leg lastLeg = connection.getLegs().getLast();
            assertEquals(Leg.Type.ROUTE, lastLeg.getType());
            assertEquals(DAY_START_HOUR, connection.getArrivalTime().getHour());
            assertEquals(20, connection.getArrivalTime().getMinute());
        }

    }


    static class TransferBehaviorHelpers {
        /**
         * Tests will only use a simplified version of a raptor schedule. Focus will lie on the stop sequence A - B - C,
         * which are connected by route 1 (A-G) and have transfers between each-other. Therefore, allowing completing
         * trips between all stops depending on the query configuration.
         */
        static RaptorAlgorithm prepareRouter(RaptorRouterTestBuilder builder, int routeTimeBetweenStops,
                                             int transferTimeBetweenStops) {
            builder.withAddRoute1_AG(0, 15, routeTimeBetweenStops, 0);
            builder.withAddTransfer("A", "B", transferTimeBetweenStops);
            builder.withAddTransfer("B", "C", transferTimeBetweenStops);
            builder.withMaxDaysToScan(1);
            return builder.build(DAY_START_HOUR, DAY_END_HOUR);
        }

        static List<Connection> routeBetweenStops(RaptorAlgorithm router, QueryConfig config) {
            return routeBetweenStops(router, "A", "C", config);
        }

        static List<Connection> routeBetweenStops(RaptorAlgorithm router, String stop1, String stop2,
                                                  QueryConfig config) {
            LocalDateTime startTime = LocalDateTime.of(2000, 1, 1, DAY_START_HOUR, 0);
            return routeBetweenStops(router, stop1, stop2, startTime, config);
        }

        static List<Connection> routeBetweenStops(RaptorAlgorithm router, String stop1, String stop2,
                                                  LocalDateTime startTime, QueryConfig config) {
            return RaptorRouterTestHelpers.routeEarliestArrival(router, stop1, stop2, startTime, config);
        }
    }

}
