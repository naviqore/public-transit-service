package ch.naviqore.raptor.router;

import ch.naviqore.raptor.Connection;
import ch.naviqore.raptor.RaptorAlgorithm;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test class for the raptor algorithm in range raptor mode.
 */
@ExtendWith(RaptorRouterTestExtension.class)
public class RangeRaptorTest {

    private static final String STOP_A = "A";
    private static final String STOP_I = "I";

    private static final LocalDateTime START_OF_DAY = LocalDateTime.of(2021, 1, 1, 0, 0);
    private static final LocalDateTime EIGHT_AM = START_OF_DAY.plusHours(8);

    @Test
    void findDepartureConnections(RaptorRouterTestBuilder builder) {
        int headway_route_1 = 15;
        int headway_route_2 = 30;

        int offset_route_2 = 15;

        LocalDateTime departureTime = EIGHT_AM;

        RaptorAlgorithm rangeRaptor = builder.withAddRoute1_AG(RaptorRouterTestBuilder.DEFAULT_OFFSET, headway_route_1,
                        RaptorRouterTestBuilder.DEFAULT_TIME_BETWEEN_STOPS, RaptorRouterTestBuilder.DEFAULT_DWELL_TIME)
                .withAddRoute2_HL(offset_route_2, headway_route_2, RaptorRouterTestBuilder.DEFAULT_TIME_BETWEEN_STOPS,
                        RaptorRouterTestBuilder.DEFAULT_DWELL_TIME)
                .withSameStopTransferTime(0)
                .withRaptorRange(1800)
                .withMaxDaysToScan(1)
                .build();

        // Find connections from "A" to "I" departing at 8:00 am.
        // Route 1 leaves "A" at 8:00, 8:15, 8:30, 8:45... and arrives at "B" 5 minutes later.
        // Route 2 leaves "H" at 8:15, 8:45, 9:15... and arrives at "B" 5 minutes later and continues on to "I" one
        // minute after arriving, the trip from "B" to "I" requires 5 minutes. Therefore, the shortest possible travel
        // time is 11 minutes. Since the headway of route 1 is 15 minutes and the headway of route 2 is 30 minutes,
        // the ideal connection leaves at 8:15, which should be suggested by the range raptor. A "normal" raptor
        // algorithm would suggest the connection at 8:00.
        List<Connection> connections = RaptorRouterTestHelpers.routeEarliestArrival(rangeRaptor, STOP_A, STOP_I,
                departureTime);

        LocalDateTime expectedDepartureTime = EIGHT_AM.plusMinutes(15);
        LocalDateTime expectedArrivalTime = expectedDepartureTime.plusMinutes(
                2 * RaptorRouterTestBuilder.DEFAULT_TIME_BETWEEN_STOPS + RaptorRouterTestBuilder.DEFAULT_DWELL_TIME);

        assertEquals(1, connections.size());
        RangeRaptorHelpers.assertConnection(connections.getFirst(), expectedDepartureTime, expectedArrivalTime);

        // confirm that simple raptor would suggest the connection at 8:00
        RaptorAlgorithm simpleRaptor = builder.withRaptorRange(-1).build();
        connections = RaptorRouterTestHelpers.routeEarliestArrival(simpleRaptor, STOP_A, STOP_I, departureTime);
        expectedDepartureTime = EIGHT_AM;
        // expected arrival time is the same as with range raptor (only 15 minute idle time and transfer stop)
        assertEquals(1, connections.size());
        RangeRaptorHelpers.assertConnection(connections.getFirst(), expectedDepartureTime, expectedArrivalTime);

        // confirm that range raptor with 10 minute range (smaller than the 15-minute headway of route 1) would suggest
        // the connection at 8:00
        rangeRaptor = builder.withRaptorRange(600).build();
        connections = RaptorRouterTestHelpers.routeEarliestArrival(rangeRaptor, STOP_A, STOP_I, departureTime);
        assertEquals(1, connections.size());
        RangeRaptorHelpers.assertConnection(connections.getFirst(), expectedDepartureTime, expectedArrivalTime);
    }

    @Test
    void findArrivalConnections(RaptorRouterTestBuilder builder) {
        int headway_route_1 = 30;
        int headway_route_2 = 15;

        int offset_route_1 = 15;

        // Find connections from "A" to "I" arriving at 8:41 am.
        // Route 1 leaves "A" at 7:45, 8:15, 8:45 ... and arrives at "B" 5 minutes later.
        // Route 2 leaves "H" at 8:00, 8:15, 8:30, 8:45 ... and arrives at "B" 5 minutes later and continues on to "I"
        // one minute after arriving, the trip from "B" to "I" requires 5 minutes. Therefore, the shortest possible
        // travel time is 11 minutes. Since the headway of route 1 is 30 minutes and the headway of route 2 is 15
        // minutes, the ideal connection arrives at 8:26, which should be suggested by the range raptor. A "normal"
        // raptor algorithm would suggest the connection arriving at 8:41.

        LocalDateTime arrivalTime = EIGHT_AM.plusMinutes(41);

        RaptorAlgorithm rangeRaptor = builder.withAddRoute1_AG(offset_route_1, headway_route_1,
                        RaptorRouterTestBuilder.DEFAULT_TIME_BETWEEN_STOPS, RaptorRouterTestBuilder.DEFAULT_DWELL_TIME)
                .withAddRoute2_HL(RaptorRouterTestBuilder.DEFAULT_OFFSET, headway_route_2,
                        RaptorRouterTestBuilder.DEFAULT_TIME_BETWEEN_STOPS, RaptorRouterTestBuilder.DEFAULT_DWELL_TIME)
                .withSameStopTransferTime(0)
                .withRaptorRange(1800)
                .withMaxDaysToScan(1)
                .build();

        List<Connection> connections = RaptorRouterTestHelpers.routeLatestDeparture(rangeRaptor, STOP_A, STOP_I,
                arrivalTime);
        LocalDateTime expectedArrivalTime = EIGHT_AM.plusMinutes(26);
        LocalDateTime expectedDepartureTime = expectedArrivalTime.minusMinutes(
                2 * RaptorRouterTestBuilder.DEFAULT_TIME_BETWEEN_STOPS + RaptorRouterTestBuilder.DEFAULT_DWELL_TIME);

        assertEquals(1, connections.size());
        RangeRaptorHelpers.assertConnection(connections.getFirst(), expectedDepartureTime, expectedArrivalTime);

        // Confirm that simple raptor would suggest the connection arriving at 8:41
        RaptorAlgorithm simpleRaptor = builder.withRaptorRange(-1).build();
        connections = RaptorRouterTestHelpers.routeLatestDeparture(simpleRaptor, STOP_A, STOP_I, arrivalTime);
        expectedArrivalTime = EIGHT_AM.plusMinutes(41);
        // expected departure time is the same as with range raptor (only 15 minute idle time and transfer stop)
        assertEquals(1, connections.size());
        RangeRaptorHelpers.assertConnection(connections.getFirst(), expectedDepartureTime, expectedArrivalTime);

        // Confirm that range raptor with 10 minute range (smaller than the 15-minute headway of route 2) would suggest
        // the connection arriving at 8:41
        rangeRaptor = builder.withRaptorRange(600).build();
        connections = RaptorRouterTestHelpers.routeLatestDeparture(rangeRaptor, STOP_A, STOP_I, arrivalTime);
        assertEquals(1, connections.size());
        RangeRaptorHelpers.assertConnection(connections.getFirst(), expectedDepartureTime, expectedArrivalTime);
    }

    static class RangeRaptorHelpers {

        static void assertConnection(Connection connection, LocalDateTime expectedDepartureTime,
                                     LocalDateTime expectedArrivalTime) {
            assertEquals(2, connection.getLegs().size());
            assertEquals(expectedDepartureTime, connection.getDepartureTime());
            assertEquals(expectedArrivalTime, connection.getArrivalTime());
            assertEquals(RangeRaptorTest.STOP_A, connection.getFromStopId());
            assertEquals(RangeRaptorTest.STOP_I, connection.getToStopId());
        }

    }

}
