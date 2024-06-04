package ch.naviqore.raptor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the Raptor class.
 */
@ExtendWith(RaptorTestExtension.class)
class RaptorTest {

    @Nested
    class EarliestArrival {

        @Test
        void shouldFindConnectionsBetweenIntersectingRoutes(RaptorTestBuilder builder) {
            // Should return two pareto optimal connections:
            // 1. Connection (with two route legs and one transfer (including footpath) --> slower but fewer transfers)
            //  - Route R1-F from A to D
            //  - Foot Transfer from D to N
            //  - Route R3-F from N to Q

            // 2. Connection (with three route legs and two transfers (same station) --> faster but more transfers)
            //  - Route R1-F from A to F
            //  - Route R4-R from F to P
            //  - Route R3-F from P to Q
            Raptor raptor = builder.buildWithDefaults();

            String sourceStop = "A";
            String targetStop = "Q";
            int departureTime = 8 * RaptorTestBuilder.SECONDS_IN_HOUR;
            List<Connection> connections = raptor.routeEarliestArrival(sourceStop, targetStop, departureTime);

            // check if 2 connections were found
            assertEquals(2, connections.size());

            // check if the first connection is correct
            Connection connection1 = connections.getFirst();
            assertEquals(sourceStop, connection1.getFromStopId());
            assertEquals(targetStop, connection1.getToStopId());
            assertTrue(connection1.getDepartureTime() >= departureTime,
                    "Departure time should be greater equal than searched for departure time");
            // check that transfers make sense
            assertEquals(1, connection1.getNumFootPathTransfers());
            assertEquals(1, connection1.getNumTransfers());
            assertEquals(0, connection1.getNumSameStationTransfers());

            // check second connection
            Connection connection2 = connections.get(1);
            assertEquals(sourceStop, connection2.getFromStopId());
            assertEquals(targetStop, connection2.getToStopId());
            assertTrue(connection2.getDepartureTime() >= departureTime,
                    "Departure time should be greater equal than searched for departure time");
            // check that transfers make sense
            assertEquals(0, connection2.getNumFootPathTransfers());
            assertEquals(2, connection2.getNumTransfers());
            assertEquals(2, connection2.getNumSameStationTransfers());

            // compare two connections (make sure they are pareto optimal)
            assertTrue(connection1.getDuration() > connection2.getDuration(),
                    "First connection should be slower than second connection");
            assertTrue(connection1.getNumRouteLegs() < connection2.getNumRouteLegs(),
                    "First connection should have fewer route legs than second connection");
        }

        @Test
        void routeBetweenTwoStopsOnSameRoute(RaptorTestBuilder builder) {
            Raptor raptor = builder.buildWithDefaults();

            String sourceStop = "A";
            String targetStop = "B";
            int departureTime = 8 * RaptorTestBuilder.SECONDS_IN_HOUR;
            List<Connection> connections = raptor.routeEarliestArrival(sourceStop, targetStop, departureTime);
            assertEquals(1, connections.size());
            Connection connection = connections.getFirst();
            assertEquals(sourceStop, connection.getFromStopId());
            assertEquals(targetStop, connection.getToStopId());
            assertTrue(connection.getDepartureTime() >= departureTime,
                    "Departure time should be greater equal than searched for departure time");
            assertEquals(0, connection.getNumFootPathTransfers());
            assertEquals(0, connection.getNumTransfers());
            assertEquals(0, connection.getNumSameStationTransfers());
        }

        @Test
        void shouldNotFindConnectionBetweenNotLinkedStops(RaptorTestBuilder builder) {
            // Omit route R2/R4 and transfers to make stop Q (on R3) unreachable from A (on R1)
            Raptor raptor = builder.withAddRoute1_AG().withAddRoute3_MQ().build();

            String sourceStop = "A";
            String targetStop = "Q";
            int departureTime = 8 * RaptorTestBuilder.SECONDS_IN_HOUR;
            List<Connection> connections = raptor.routeEarliestArrival(sourceStop, targetStop, departureTime);
            assertTrue(connections.isEmpty(), "No connection should be found");
        }

        @Test
        void shouldFindConnectionBetweenOnlyFootpath(RaptorTestBuilder builder) {
            Raptor raptor = builder.withAddRoute1_AG()
                    .withAddRoute2_HL()
                    .withAddRoute3_MQ()
                    .withAddRoute4_RS()
                    .withAddTransfer1_ND(1)
                    .withAddTransfer2_LR()
                    .build();

            String sourceStop = "N";
            String targetStop = "D";
            int departureTime = 8 * RaptorTestBuilder.SECONDS_IN_HOUR;
            List<Connection> connections = raptor.routeEarliestArrival(sourceStop, targetStop, departureTime);
            assertEquals(1, connections.size());
            Connection connection = connections.getFirst();
            assertEquals(sourceStop, connection.getFromStopId());
            assertEquals(targetStop, connection.getToStopId());
            assertTrue(connection.getDepartureTime() >= departureTime,
                    "Departure time should be greater equal than searched for departure time");
            assertEquals(1, connection.getNumFootPathTransfers());
            assertEquals(1, connection.getNumTransfers());
            assertEquals(0, connection.getNumSameStationTransfers());
            assertEquals(0, connection.getNumRouteLegs());
        }

        @Nested
        class InputValidation {

            private Raptor raptor;

            @BeforeEach
            void setUp(RaptorTestBuilder builder) {
                raptor = builder.buildWithDefaults();
            }

            @Test
            void shouldThrowErrorWhenSourceStopNotExists() {
                String sourceStop = "NonExistentStop";
                String targetStop = "A";
                int departureTime = 8 * RaptorTestBuilder.SECONDS_IN_HOUR;

                assertThrows(IllegalArgumentException.class,
                        () -> raptor.routeEarliestArrival(sourceStop, targetStop, departureTime),
                        "Source stop has to exists");
            }

            @Test
            void shouldThrowErrorWhenTargetStopNotExists() {
                String sourceStop = "A";
                String targetStop = "NonExistentStop";
                int departureTime = 8 * RaptorTestBuilder.SECONDS_IN_HOUR;

                assertThrows(IllegalArgumentException.class,
                        () -> raptor.routeEarliestArrival(sourceStop, targetStop, departureTime),
                        "Target stop has to exists");
            }

            @Test
            void shouldThrowErrorWhenDepartureTimeIsOutOfRange() {
                String sourceStop = "A";
                String targetStop = "B";

                assertThrows(IllegalArgumentException.class,
                        () -> raptor.routeEarliestArrival(sourceStop, targetStop, -1),
                        "Departure time cannot be negative");
                assertThrows(IllegalArgumentException.class, () -> raptor.routeEarliestArrival(sourceStop, targetStop,
                        49 * RaptorTestBuilder.SECONDS_IN_HOUR), "Departure time cannot be greater than two days");
            }

            @Test
            void shouldThrowErrorWhenRequestBetweenSameStop() {
                String sourceStop = "A";
                String targetStop = "A";
                int departureTime = 8 * RaptorTestBuilder.SECONDS_IN_HOUR;

                assertThrows(IllegalArgumentException.class,
                        () -> raptor.routeEarliestArrival(sourceStop, targetStop, departureTime),
                        "Stops cannot be the same");
            }

        }

    }

}
