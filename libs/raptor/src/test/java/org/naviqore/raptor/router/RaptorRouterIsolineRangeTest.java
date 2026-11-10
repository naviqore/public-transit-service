package org.naviqore.raptor.router;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.naviqore.raptor.Connection;
import org.naviqore.raptor.QueryConfig;
import org.naviqore.raptor.RaptorAlgorithm;
import org.naviqore.raptor.TimeType;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The isoline range query reports, for each stop, the connection with the shortest travel time among those leaving in
 * the time window: as short as the shortest of the isolines at every time in the window.
 */
@ExtendWith(RaptorRouterTestExtension.class)
class RaptorRouterIsolineRangeTest {

    private static final String SOURCE = "A";
    private static final OffsetDateTime EIGHT_AM = RaptorRouterTestBuilder.DEFAULT_REFERENCE_DATE.atStartOfDay(
            RaptorRouterTestBuilder.DEFAULT_ZONE_ID).toOffsetDateTime().plusHours(8);
    private static final OffsetDateTime NINE_AM = EIGHT_AM.plusHours(1);
    private static final Duration WINDOW = Duration.ofHours(1);

    /**
     * The schedule of {@link RaptorRouterRangeTest#buildIrregularSchedule}, in which the shortest travel time to a stop
     * depends on when one leaves, and no isoline in the window takes a trip leaving the source after it (arriving at it
     * before it).
     */
    private static RaptorAlgorithm buildIrregularSchedule(RaptorRouterTestBuilder builder) {
        return RaptorRouterRangeTest.buildIrregularSchedule(builder);
    }

    private static OffsetDateTime windowEdge(TimeType timeType) {
        return timeType == TimeType.DEPARTURE ? EIGHT_AM : NINE_AM;
    }

    /**
     * The shortest travel time to each stop over the isolines at every minute of the window. The trips and walks of the
     * schedule are whole minutes, so a travel time is shortest leaving (arriving) at a whole minute.
     */
    private static Map<String, Long> shortestOfIsolinesEveryMinute(RaptorAlgorithm raptor, TimeType timeType) {
        Map<String, Long> shortest = new HashMap<>();
        for (int minute = 0; minute < WINDOW.toMinutes(); minute++) {
            OffsetDateTime time = timeType == TimeType.DEPARTURE ? EIGHT_AM.plusMinutes(minute) : NINE_AM.minusMinutes(
                    minute);
            raptor.isolineQuery(Map.of(SOURCE, time), timeType, QueryConfig.defaults())
                    .forEach((stop, connection) -> shortest.merge(stop,
                            timeType == TimeType.DEPARTURE ? Duration.between(time, connection.getArrivalTime())
                                    .toSeconds() : Duration.between(connection.getDepartureTime(), time).toSeconds(),
                            Math::min));
        }

        return shortest;
    }

    private static long travelTime(Connection connection) {
        return Duration.between(connection.getDepartureTime(), connection.getArrivalTime()).toSeconds();
    }

    @ParameterizedTest
    @EnumSource(TimeType.class)
    void shortestTravelTimesAreTheShortestOfTheIsolinesEveryMinute(TimeType timeType, RaptorRouterTestBuilder builder) {
        RaptorAlgorithm raptor = buildIrregularSchedule(builder);

        Map<String, Connection> isolines = raptor.isolineRangeQuery(Map.of(SOURCE, windowEdge(timeType)), timeType,
                WINDOW, QueryConfig.defaults());

        Map<String, Long> expected = shortestOfIsolinesEveryMinute(raptor, timeType);
        assertEquals(expected.keySet(), isolines.keySet());
        for (Map.Entry<String, Connection> entry : isolines.entrySet()) {
            Connection connection = entry.getValue();
            assertEquals(expected.get(entry.getKey()), travelTime(connection), "travel time to " + entry.getKey());
            boolean inWindow = timeType == TimeType.DEPARTURE ? !connection.getDepartureTime()
                    .isBefore(EIGHT_AM) && connection.getDepartureTime().isBefore(NINE_AM) : connection.getArrivalTime()
                    .isAfter(EIGHT_AM) && !connection.getArrivalTime().isAfter(NINE_AM);
            assertTrue(inWindow, "the connection to " + entry.getKey() + " leaves or arrives in the window");
        }
    }

    @Test
    void equallyShortTravelTimes_leaveEarliest(RaptorRouterTestBuilder builder) {
        // every trip of route 1 takes as long from A to G
        RaptorAlgorithm raptor = buildIrregularSchedule(builder);

        Map<String, Connection> isolines = raptor.isolineRangeQuery(Map.of(SOURCE, EIGHT_AM), TimeType.DEPARTURE,
                WINDOW, QueryConfig.defaults());

        assertEquals(EIGHT_AM, isolines.get("G").getDepartureTime());
    }

    @Test
    void connectionLeavingAfterTheWindow_isNotReported(RaptorRouterTestBuilder builder) {
        // route 1 leaves A at 8:00 and reaches G at 8:35; a direct route leaves A at 8:12 and reaches G at 8:22
        RaptorAlgorithm raptor = builder.withAddRoute1_AG()
                .withAddRoute("X", RaptorRouterTestBuilder.DEFAULT_ZONE_ID, List.of("A", "G"), 12, 15, 10, 1)
                .build();

        Map<String, Connection> isolines = raptor.isolineRangeQuery(Map.of(SOURCE, EIGHT_AM), TimeType.DEPARTURE,
                Duration.ofMinutes(10), QueryConfig.defaults());

        assertEquals(EIGHT_AM, isolines.get("G").getDepartureTime());
        assertEquals(EIGHT_AM.plusMinutes(35), isolines.get("G").getArrivalTime());
    }

    @Test
    void windowShorterThanASecond_isRejected(RaptorRouterTestBuilder builder) {
        RaptorAlgorithm raptor = buildIrregularSchedule(builder);

        for (Duration window : List.of(Duration.ZERO, Duration.ofMillis(999), Duration.ofSeconds(-1))) {
            assertThrows(IllegalArgumentException.class,
                    () -> raptor.isolineRangeQuery(Map.of(SOURCE, EIGHT_AM), TimeType.DEPARTURE, window,
                            QueryConfig.defaults()));
        }
    }

}
