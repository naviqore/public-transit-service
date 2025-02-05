package ch.naviqore.raptor.router;

import ch.naviqore.raptor.Connection;
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
