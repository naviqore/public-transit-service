package org.naviqore.raptor.router;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.naviqore.raptor.Connection;
import org.naviqore.raptor.Leg;
import org.naviqore.raptor.RaptorAlgorithm;

import java.time.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(RaptorRouterTestExtension.class)
class RaptorRouterDateTimeTest {

    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final ZoneId ZURICH = ZoneId.of("Europe/Zurich");
    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
    private static final ZoneId TOKYO = ZoneId.of("Asia/Tokyo");

    @Nested
    @DisplayName("Global Timezone Consistency")
    class TimezoneConsistency {

        @Test
        @DisplayName("Pure UTC: Should route correctly with zero offsets")
        void shouldRouteInPureUtc(RaptorRouterTestBuilder builder) {
            LocalDate date = LocalDate.of(2024, 6, 1);
            OffsetDateTime departureTime = date.atTime(12, 0).atZone(UTC).toOffsetDateTime();

            RaptorAlgorithm router = builder.withReferenceDate(date)
                    .withDayRange(0, 24)
                    .withAddRoute("R_UTC", UTC, List.of("A", "B"), 12 * 60, 60, 30, 0)
                    .build();

            List<Connection> connections = RaptorRouterTestHelpers.routeEarliestArrival(router, "A", "B",
                    departureTime);

            assertEquals(1, connections.size());
            assertEquals(ZoneOffset.UTC, connections.getFirst().getDepartureTime().getOffset());
            assertEquals(departureTime.plusMinutes(30), connections.getFirst().getArrivalTime());
        }

        @Test
        @DisplayName("Tokyo (No DST): Should handle fixed +09:00 offset correctly")
        void shouldRouteInTokyo(RaptorRouterTestBuilder builder) {
            LocalDate date = LocalDate.of(2024, 6, 1);
            OffsetDateTime departureTime = date.atTime(17, 0).atZone(TOKYO).toOffsetDateTime();

            RaptorAlgorithm router = builder.withReferenceDate(date)
                    .withDayRange(0, 24)
                    .withAddRoute("R_TKO", TOKYO, List.of("A", "B"), 17 * 60, 60, 45, 0)
                    .build();

            List<Connection> connections = RaptorRouterTestHelpers.routeEarliestArrival(router, "A", "B",
                    departureTime);

            assertEquals(1, connections.size());
            assertEquals(ZoneOffset.ofHours(9), connections.getFirst().getDepartureTime().getOffset());
        }

        @Test
        @DisplayName("Should route correctly when everything is in New York Time")
        void shouldRouteInNewYork(RaptorRouterTestBuilder builder) {
            LocalDate date = LocalDate.of(2024, 6, 1);
            OffsetDateTime departureTime = date.atTime(8, 0).atZone(NEW_YORK).toOffsetDateTime();

            RaptorAlgorithm router = builder.withReferenceDate(date)
                    .withDayRange(0, 24)
                    .withAddRoute("R_NY", NEW_YORK, List.of("A", "B"), 8 * 60, 60, 30, 0)
                    .build();

            List<Connection> connections = RaptorRouterTestHelpers.routeEarliestArrival(router, "A", "B",
                    departureTime);

            assertEquals(1, connections.size());
            Connection c = connections.getFirst();
            assertEquals(departureTime, c.getDepartureTime());
            assertEquals(departureTime.plusMinutes(30), c.getArrivalTime());
        }
    }

    @Nested
    @DisplayName("Walk Transfer Timezone Resolution")
    class WalkTransferTimezones {

        @Test
        @DisplayName("Initial Walk: Should adopt the offset of the first Route leg")
        void initialWalkShouldAdoptRouteOffset(RaptorRouterTestBuilder builder) {
            LocalDate date = LocalDate.of(2024, 1, 10);
            OffsetDateTime queryDep = date.atTime(0, 0).atZone(UTC).toOffsetDateTime();

            // route in tokyo (+09:00)
            builder.withReferenceDate(date).withAddRoute("R_TKO", TOKYO, List.of("B", "C"), 10 * 60, 60, 30, 0);
            builder.withAddTransfer("A", "B", 30);

            RaptorAlgorithm router = builder.withDayRange(0, 24).build();

            List<Connection> connections = RaptorRouterTestHelpers.routeEarliestArrival(router, "A", "C", queryDep);

            assertEquals(1, connections.size());
            Leg walkLeg = connections.getFirst().getLegs().getFirst();

            assertEquals(ZoneOffset.ofHours(9), walkLeg.getDepartureTime().getOffset());
            assertEquals(10, walkLeg.getArrivalTime().getHour());
        }

        @Test
        @DisplayName("Final Walk: Should adopt the offset of the last Route leg")
        void finalWalkShouldAdoptRouteOffset(RaptorRouterTestBuilder builder) {
            LocalDate date = LocalDate.of(2024, 6, 1);
            OffsetDateTime queryDep = date.atTime(12, 0).atZone(UTC).toOffsetDateTime();

            // route in ny (-04:00 in summer)
            builder.withReferenceDate(date).withAddRoute("R_NY", NEW_YORK, List.of("A", "B"), 12 * 60, 60, 30, 0);
            builder.withAddTransfer("B", "C", 15);

            RaptorAlgorithm router = builder.withDayRange(0, 24).build();

            List<Connection> connections = RaptorRouterTestHelpers.routeEarliestArrival(router, "A", "C", queryDep);

            assertEquals(1, connections.size());
            Leg walkLeg = connections.getFirst().getLegs().get(1);

            assertEquals(ZoneOffset.ofHours(-4), walkLeg.getArrivalTime().getOffset());
            assertEquals(12, walkLeg.getArrivalTime().getHour());
            assertEquals(45, walkLeg.getArrivalTime().getMinute());
        }

        @Test
        @DisplayName("Intermediate Walk: Should bridge different timezone offsets")
        void intermediateWalkShouldBridgeOffsets(RaptorRouterTestBuilder builder) {
            LocalDate date = LocalDate.of(2024, 1, 10);

            // route 1 tokyo (+09:00)
            builder.withReferenceDate(date).withAddRoute("R_TKO", TOKYO, List.of("A", "B"), 9 * 60, 60, 30, 0);
            // route 2 zurich (+01:00)
            builder.withAddRoute("R_ZRH", ZURICH, List.of("C", "D"), 13 * 60, 60, 30, 0);
            builder.withAddTransfer("B", "C", 60);

            RaptorAlgorithm router = builder.withDayRange(0, 24).build();

            OffsetDateTime queryDep = date.atTime(9, 0).atZone(TOKYO).toOffsetDateTime();
            List<Connection> connections = RaptorRouterTestHelpers.routeEarliestArrival(router, "A", "D", queryDep);

            assertEquals(1, connections.size());
            Leg walkLeg = connections.getFirst().getLegs().get(1);

            assertEquals(ZoneOffset.ofHours(9), walkLeg.getDepartureTime().getOffset());
            assertEquals(ZoneOffset.ofHours(1), walkLeg.getArrivalTime().getOffset());
        }
    }

    @Nested
    @DisplayName("Inter-Agency Routing (Cross-Timezone)")
    class CrossTimezone {

        @Test
        @DisplayName("Should correctly reconstruct walk timezone when crossing from London to Zurich")
        void shouldRouteLondonToZurich(RaptorRouterTestBuilder builder) {
            LocalDate date = LocalDate.of(2024, 1, 10);

            // route london (utc): a -> b. dep 10:00, arr 11:00 utc.
            builder.withReferenceDate(date).withAddRoute("R_LDN", UTC, List.of("A", "B"), 10 * 60, 60, 60, 0);
            // route zurich (utc+1): c -> d. dep 13:30 local (12:30 utc).
            builder.withAddRoute("R_ZRH", ZURICH, List.of("C", "D"), 13 * 60 + 30, 60, 60, 0);
            // walk b -> c (60 mins).
            builder.withAddTransfer("B", "C", 60);

            RaptorAlgorithm router = builder.withDayRange(0, 24).build();

            OffsetDateTime queryDep = date.atTime(10, 0).atZone(UTC).toOffsetDateTime();
            List<Connection> connections = RaptorRouterTestHelpers.routeEarliestArrival(router, "A", "D", queryDep);

            assertEquals(1, connections.size());
            Leg walkLeg = connections.getFirst().getLegs().get(1);

            OffsetDateTime expectedArrival = date.atTime(13, 0).atZone(ZURICH).toOffsetDateTime();

            assertEquals(expectedArrival, walkLeg.getArrivalTime(),
                    "Walk arrival should match the Zurich route's time and offset");
            assertEquals(ZoneOffset.ofHours(1), walkLeg.getArrivalTime().getOffset());
        }

        @Test
        @DisplayName("Should handle numerical time travel (East to West)")
        void shouldHandleEastToWest(RaptorRouterTestBuilder builder) {
            LocalDate date = LocalDate.of(2024, 6, 1);

            // tokyo (utc+9) dep 09:00 (00:00 utc)
            builder.withReferenceDate(date)
                    .withAddRoute("R_TKO", TOKYO, List.of("A", "B"), 9 * 60, 60, 30, 0)
                    .withAddTransfer("B", "C", 30);
            // london (utc) dep 01:30 utc
            builder.withAddRoute("R_LDN", UTC, List.of("C", "D"), 60 + 30, 60, 30, 0);

            RaptorAlgorithm router = builder.withDayRange(0, 24).build();

            OffsetDateTime depQuery = date.atTime(9, 0).atZone(TOKYO).toOffsetDateTime();
            List<Connection> connections = RaptorRouterTestHelpers.routeEarliestArrival(router, "A", "D", depQuery);

            assertEquals(1, connections.size());
            Connection c = connections.getFirst();

            // numerical "time travel": 09:00 tokyo -> 02:00 london same day
            assertEquals(9, c.getDepartureTime().getHour());
            assertEquals(2, c.getArrivalTime().getHour());
            assertEquals(7200, c.getDurationInSeconds()); // 2 hours absolute
        }
    }

    @Nested
    @DisplayName("DST and Date Transitions")
    class DstAndDate {

        @Test
        @DisplayName("Spring Forward: Skip trips in the gap hour")
        void shouldHandleSpringForward(RaptorRouterTestBuilder builder) {
            LocalDate dstDate = LocalDate.of(2024, 3, 31); // 02:00 -> 03:00

            RaptorAlgorithm router = builder.withReferenceDate(dstDate)
                    .withDayRange(0, 24)
                    .withAddRoute("R1", ZURICH, List.of("A", "B"), 0, 15, 30, 0)
                    .build();

            OffsetDateTime queryDep = dstDate.atTime(1, 55).atZone(ZURICH).toOffsetDateTime();
            List<Connection> c = RaptorRouterTestHelpers.routeEarliestArrival(router, "A", "B", queryDep);

            assertFalse(c.isEmpty());
            assertEquals(LocalTime.of(3, 0), c.getFirst().getDepartureTime().toLocalTime(),
                    "First trip after jump is 03:00");
        }

        @Test
        @DisplayName("Spring Forward: Connection is impossible if absolute UTC gap is smaller than walk")
        void unreachableAcrossDstJump(RaptorRouterTestBuilder builder) {
            LocalDate date = LocalDate.of(2024, 3, 31);
            // Bus 1 arrives B at 01:55 local (UTC 00:55).
            builder.withReferenceDate(date).withAddRoute("R1", ZURICH, List.of("A", "B"), 115, 60, 0, 0);
            // Bus 2 departs C at 03:15 local (UTC 01:15).
            // Linear absolute gap is 20 mins.
            builder.withDayRange(3, 4).withAddRoute("R2", ZURICH, List.of("C", "D"), 15, 60, 0, 0);
            // Walk is 30 mins.
            builder.withAddTransfer("B", "C", 30);

            RaptorAlgorithm router = builder.withDayRange(0, 24).build();
            OffsetDateTime dep = date.atTime(1, 30).atZone(ZURICH).toOffsetDateTime();

            List<Connection> results = RaptorRouterTestHelpers.routeEarliestArrival(router, "A", "D", dep);
            assertTrue(results.isEmpty(), "Walk (30m) > UTC Gap (20m)");
        }

        @Test
        @DisplayName("Fall Back: Catch first instance of 02:30 AM during overlap")
        void shouldHandleFallBack(RaptorRouterTestBuilder builder) {
            LocalDate dstDate = LocalDate.of(2024, 10, 27);

            RaptorAlgorithm router = builder.withReferenceDate(dstDate)
                    .withDayRange(0, 24)
                    .withAddRoute("R1", ZURICH, List.of("A", "B"), 0, 30, 10, 0)
                    .build();

            OffsetDateTime queryDep = dstDate.atTime(2, 15)
                    .atZone(ZURICH)
                    .withEarlierOffsetAtOverlap()
                    .toOffsetDateTime();
            List<Connection> connections = RaptorRouterTestHelpers.routeEarliestArrival(router, "A", "B", queryDep);

            assertEquals(1, connections.size());
            assertEquals(ZoneOffset.ofHours(2), connections.getFirst().getDepartureTime().getOffset());
        }

        @Test
        @DisplayName("Midnight Crossing: Correct arrival date")
        void shouldHandleMidnight(RaptorRouterTestBuilder builder) {
            LocalDate date = LocalDate.of(2024, 6, 1);
            builder.withReferenceDate(date).withAddRoute("Night", UTC, List.of("A", "B"), 23 * 60 + 50, 60, 20, 0);
            RaptorAlgorithm router = builder.withMaxDaysToScan(2).withDayRange(0, 30).build();

            OffsetDateTime queryDep = date.atTime(23, 45).atZone(UTC).toOffsetDateTime();
            List<Connection> c = RaptorRouterTestHelpers.routeEarliestArrival(router, "A", "B", queryDep);

            assertEquals(1, c.size());
            assertEquals(date.plusDays(1), c.getFirst().getArrivalTime().toLocalDate());
            assertEquals(LocalTime.of(0, 10), c.getFirst().getArrivalTime().toLocalTime());
        }
    }
}