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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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
            OffsetDateTime queryTime = date.atTime(12, 0).atZone(UTC).toOffsetDateTime();

            RaptorAlgorithm router = builder.withAddRoute("R_UTC", UTC, List.of("A", "B"), 12 * 60, 60, 30, 0)
                    .withReferenceDate(date)
                    .withServiceDayRange(0, 24)
                    .build();

            List<Connection> connections = RaptorRouterTestHelpers.routeEarliestArrival(router, "A", "B", queryTime);

            assertEquals(1, connections.size());
            assertEquals(ZoneOffset.UTC, connections.getFirst().getDepartureTime().getOffset());
            assertEquals(queryTime.plusMinutes(30), connections.getFirst().getArrivalTime());
        }

        @Test
        @DisplayName("Tokyo (No DST): Should handle fixed +09:00 offset correctly")
        void shouldRouteInTokyo(RaptorRouterTestBuilder builder) {
            LocalDate date = LocalDate.of(2024, 6, 1);
            OffsetDateTime queryTime = date.atTime(17, 0).atZone(TOKYO).toOffsetDateTime();

            RaptorAlgorithm router = builder.withAddRoute("R_TKO", TOKYO, List.of("A", "B"), 17 * 60, 60, 45, 0)
                    .withReferenceDate(date)
                    .withServiceDayRange(0, 24)
                    .build();

            List<Connection> connections = RaptorRouterTestHelpers.routeEarliestArrival(router, "A", "B", queryTime);

            assertEquals(1, connections.size());
            assertEquals(ZoneOffset.ofHours(9), connections.getFirst().getDepartureTime().getOffset());
        }

        @Test
        @DisplayName("Should route correctly when everything is in New York Time")
        void shouldRouteInNewYork(RaptorRouterTestBuilder builder) {
            LocalDate date = LocalDate.of(2024, 6, 1);
            OffsetDateTime queryTime = date.atTime(8, 0).atZone(NEW_YORK).toOffsetDateTime();

            RaptorAlgorithm router = builder.withAddRoute("R_NY", NEW_YORK, List.of("A", "B"), 8 * 60, 60, 30, 0)
                    .withReferenceDate(date)
                    .withServiceDayRange(0, 24)
                    .build();

            List<Connection> connections = RaptorRouterTestHelpers.routeEarliestArrival(router, "A", "B", queryTime);

            assertEquals(1, connections.size());
            Connection c = connections.getFirst();
            assertEquals(queryTime, c.getDepartureTime());
            assertEquals(queryTime.plusMinutes(30), c.getArrivalTime());
        }
    }

    @Nested
    @DisplayName("Walk Transfer Timezone Resolution")
    class WalkTransferTimezones {

        @Test
        @DisplayName("Initial Walk: Should adopt the offset of the first Route leg")
        void initialWalkShouldAdoptRouteOffset(RaptorRouterTestBuilder builder) {
            LocalDate date = LocalDate.of(2024, 1, 10);
            OffsetDateTime queryTime = date.atTime(0, 0).atZone(UTC).toOffsetDateTime();

            // route in Tokyo (+09:00)
            builder.withAddRoute("R_TKO", TOKYO, List.of("B", "C"), 10 * 60, 60, 30, 0);
            builder.withAddTransfer("A", "B", 30);

            RaptorAlgorithm router = builder.withReferenceDate(date).withServiceDayRange(0, 24).build();
            List<Connection> connections = RaptorRouterTestHelpers.routeEarliestArrival(router, "A", "C", queryTime);

            assertEquals(1, connections.size());
            Leg walkLeg = connections.getFirst().getLegs().getFirst();

            assertEquals(ZoneOffset.ofHours(9), walkLeg.getDepartureTime().getOffset());
            assertEquals(10, walkLeg.getArrivalTime().getHour());
        }

        @Test
        @DisplayName("Intermediate Walk: Should bridge different timezone offsets")
        void intermediateWalkShouldBridgeOffsets(RaptorRouterTestBuilder builder) {
            LocalDate date = LocalDate.of(2024, 1, 10);
            OffsetDateTime queryTime = date.atTime(9, 0).atZone(TOKYO).toOffsetDateTime();

            // route 1 Tokyo (+09:00)
            builder.withAddRoute("R_TKO", TOKYO, List.of("A", "B"), 9 * 60, 60, 30, 0);
            // route 2 Zurich (+01:00)
            builder.withAddRoute("R_ZRH", ZURICH, List.of("C", "D"), 13 * 60, 60, 30, 0);
            builder.withAddTransfer("B", "C", 60);

            RaptorAlgorithm router = builder.withReferenceDate(date).withServiceDayRange(0, 24).build();
            List<Connection> connections = RaptorRouterTestHelpers.routeEarliestArrival(router, "A", "D", queryTime);

            assertEquals(1, connections.size());
            Leg walkLeg = connections.getFirst().getLegs().get(1);

            assertEquals(ZoneOffset.ofHours(9), walkLeg.getDepartureTime().getOffset());
            assertEquals(ZoneOffset.ofHours(1), walkLeg.getArrivalTime().getOffset());
        }

        @Test
        @DisplayName("Final Walk: Should adopt the offset of the last Route leg")
        void finalWalkShouldAdoptRouteOffset(RaptorRouterTestBuilder builder) {
            LocalDate date = LocalDate.of(2024, 6, 1);
            OffsetDateTime queryTime = date.atTime(12, 0).atZone(UTC).toOffsetDateTime();

            // route in New York (-04:00 in summer)
            builder.withAddRoute("R_NY", NEW_YORK, List.of("A", "B"), 12 * 60, 60, 30, 0);
            builder.withAddTransfer("B", "C", 15);

            RaptorAlgorithm router = builder.withReferenceDate(date).withServiceDayRange(0, 24).build();
            List<Connection> connections = RaptorRouterTestHelpers.routeEarliestArrival(router, "A", "C", queryTime);

            assertEquals(1, connections.size());
            Leg walkLeg = connections.getFirst().getLegs().get(1);

            assertEquals(ZoneOffset.ofHours(-4), walkLeg.getArrivalTime().getOffset());
            assertEquals(12, walkLeg.getArrivalTime().getHour());
            assertEquals(45, walkLeg.getArrivalTime().getMinute());
        }
    }

    @Nested
    @DisplayName("Inter-Agency Routing (Cross-Timezone)")
    class CrossTimezone {

        @Test
        @DisplayName("Should correctly reconstruct walk timezone when crossing from London to Zurich")
        void shouldRouteLondonToZurich(RaptorRouterTestBuilder builder) {
            LocalDate date = LocalDate.of(2024, 1, 10);
            OffsetDateTime queryTime = date.atTime(10, 0).atZone(UTC).toOffsetDateTime();

            // route London (utc): a -> b. dep 10:00, arr 11:00 utc.
            builder.withAddRoute("R_LDN", UTC, List.of("A", "B"), 10 * 60, 60, 60, 0);
            // route Zurich (utc+1): c -> d. dep 13:30 local (12:30 utc).
            builder.withAddRoute("R_ZRH", ZURICH, List.of("C", "D"), 13 * 60 + 30, 60, 60, 0);
            // walk b -> c (60 mins).
            builder.withAddTransfer("B", "C", 60);

            RaptorAlgorithm router = builder.withReferenceDate(date).withServiceDayRange(0, 24).build();
            List<Connection> connections = RaptorRouterTestHelpers.routeEarliestArrival(router, "A", "D", queryTime);

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
            OffsetDateTime queryTime = date.atTime(9, 0).atZone(TOKYO).toOffsetDateTime();

            // Tokyo (utc+9) dep 09:00 (00:00 utc)
            builder.withAddRoute("R_TKO", TOKYO, List.of("A", "B"), 9 * 60, 60, 30, 0).withAddTransfer("B", "C", 30);
            // London (utc) dep 01:30 utc
            builder.withAddRoute("R_LDN", UTC, List.of("C", "D"), 60 + 30, 60, 30, 0);

            RaptorAlgorithm router = builder.withReferenceDate(date).withServiceDayRange(0, 24).build();
            List<Connection> connections = RaptorRouterTestHelpers.routeEarliestArrival(router, "A", "D", queryTime);

            assertEquals(1, connections.size());
            Connection c = connections.getFirst();

            // numerical "time travel": 09:00 Tokyo -> 02:00 London same day
            assertEquals(9, c.getDepartureTime().getHour());
            assertEquals(2, c.getArrivalTime().getHour());
            assertEquals(7200, c.getDurationInSeconds()); // 2 hours absolute
        }
    }

    @Nested
    @DisplayName("DST Transitions (Spring Forward & Fall Back)")
    class DstTransitions {

        @Test
        @DisplayName("Spring Forward: Skip trips in the gap hour")
        void shouldHandleSpringForward(RaptorRouterTestBuilder builder) {
            LocalDate dstDate = LocalDate.of(2024, 3, 31); // 02:00 -> 03:00

            RaptorAlgorithm router = builder.withReferenceDate(dstDate)
                    .withServiceDayRange(0, 24)
                    .withAddRoute("R1", ZURICH, List.of("A", "B"), 0, 15, 30, 0)
                    .build();

            OffsetDateTime queryDep = dstDate.atTime(1, 55).atZone(ZURICH).toOffsetDateTime();
            List<Connection> c = RaptorRouterTestHelpers.routeEarliestArrival(router, "A", "B", queryDep);

            assertFalse(c.isEmpty());
            assertEquals(LocalTime.of(3, 0), c.getFirst().getDepartureTime().toLocalTime(),
                    "First trip after jump is 03:00");
        }

        @Test
        @DisplayName("Spring Forward: Skip trip where DST compression makes the UTC gap too small for walking")
        void shouldSkipTripMadeUnreachableByDstCompression(RaptorRouterTestBuilder builder) {
            LocalDate date = LocalDate.of(2024, 3, 31); // DST jump: 02:00 -> 03:00
            OffsetDateTime queryTime = date.atTime(1, 30).atZone(ZURICH).toOffsetDateTime();

            // R1: Arrives 01:55 CET (115m elapsed)
            builder.withAddRoute("R1", ZURICH, List.of("A", "B"), 115, 60, 0, 0);
            // R2: Departs 03:15 CEST (195m elapsed)
            builder.withAddRoute("R2", ZURICH, List.of("C", "D"), 195, 60, 0, 0);
            // Walk: 30 min
            builder.withAddTransfer("B", "C", 30);

            RaptorAlgorithm router = builder.withReferenceDate(date).withServiceDayRange(0, 24).build();
            List<Connection> results = RaptorRouterTestHelpers.routeEarliestArrival(router, "A", "D", queryTime);
            assertFalse(results.isEmpty(), "Should find the next available bus");

            Connection connection = results.getFirst();
            Leg r2Leg = connection.getLegs().get(2);
            assertEquals(LocalTime.of(4, 15), r2Leg.getDepartureTime().toLocalTime(),
                    "Must skip 03:15 trip (20m UTC gap < 30m walk) and catch 04:15 trip");
        }

        @Test
        @DisplayName("Spring Forward: Connection possible when UTC gap exceeds walk time")
        void reachableAcrossDstJump(RaptorRouterTestBuilder builder) {
            LocalDate date = LocalDate.of(2024, 3, 31); // DST: 02:00 CET -> 03:00 CEST
            OffsetDateTime queryTime = date.atTime(1, 30).atZone(ZURICH).toOffsetDateTime();

            // R1: Arrives at 01:45 CET (165m elapsed)
            builder.withAddRoute("R1", ZURICH, List.of("A", "B"), 165, 60, 0, 0);
            // R2: Departs at 03:30 CEST (210m elapsed)
            builder.withAddRoute("R2", ZURICH, List.of("C", "D"), 210, 60, 0, 0);
            // Walk: 30 minutes
            builder.withAddTransfer("B", "C", 30);

            RaptorAlgorithm router = builder.withReferenceDate(date).withServiceDayRange(0, 24).build();
            List<Connection> results = RaptorRouterTestHelpers.routeEarliestArrival(router, "A", "D", queryTime);

            assertEquals(1, results.size(), "Connection should be possible: Walk time (30min) < UTC time gap (45min)");
            Connection conn = results.getFirst();
            assertEquals(3, conn.getLegs().size(), "Should have 3 legs: Route -> Walk -> Route");
            // verify the walk leg crosses the DST boundary correctly
            Leg walkLeg = conn.getLegs().get(1);
            assertEquals(ZoneOffset.ofHours(1), walkLeg.getDepartureTime().getOffset(), "Walk starts in CET (UTC+1)");
            assertEquals(ZoneOffset.ofHours(2), walkLeg.getArrivalTime().getOffset(), "Walk ends in CEST (UTC+2)");
        }

        @Test
        @DisplayName("Spring Forward: Verify no trips exist in the gap hour (02:00-02:59)")
        void noTripsInGapHour(RaptorRouterTestBuilder builder) {
            LocalDate date = LocalDate.of(2024, 3, 31); // DST: 02:00 -> 03:00

            // create route with trips every 15 minutes starting from midnight, times are monotonically increasing from
            // service day anchor and gap hour trips (02:00-02:59 local) should map to 03:00+ CEST after DST conversion
            RaptorAlgorithm router = builder.withAddRoute("R1", ZURICH, List.of("A", "B"), 0, 15, 10, 0)
                    .withReferenceDate(date)
                    .withServiceDayRange(0, 5)
                    .build();

            // query for all connections from 01:30 to 04:00
            OffsetDateTime queryStartTime = date.atTime(1, 30).atZone(ZURICH).toOffsetDateTime();
            for (int minute = 0; minute < 150; minute += 15) {
                OffsetDateTime queryTime = queryStartTime.plusMinutes(minute);
                List<Connection> results = RaptorRouterTestHelpers.routeEarliestArrival(router, "A", "B", queryTime);

                if (!results.isEmpty()) {
                    LocalTime departureTime = results.getFirst().getDepartureTime().toLocalTime();

                    // no departure should exist between 02:00 and 02:59 (the gap hour)
                    assertFalse(
                            departureTime.isAfter(LocalTime.of(1, 59)) && departureTime.isBefore(LocalTime.of(3, 0)),
                            "Trip at " + departureTime + " should not exist in DST gap (02:00-02:59).");
                }
            }
        }

        @Test
        @DisplayName("Fall Back: GTFS time monotonicity maintained during overlap hour")
        void gtfsMonotonicityDuringFallBack(RaptorRouterTestBuilder builder) {
            LocalDate date = LocalDate.of(2024, 10, 27); // DST: 03:00 CEST -> 02:00 CET

            // The hour 02:00-03:00 occurs twice in local time but times in RAPTOR remain monotonic:
            // RAPTOR 01:00 (3600s)  -> 02:00 CEST (first occurrence, UTC+2)
            // RAPTOR 01:30 (5400s)  -> 02:30 CEST (first occurrence, UTC+2)
            // RAPTOR 02:00 (7200s)  -> 02:00 CET  (second occurrence, UTC+1)
            // RAPTOR 02:30 (9000s)  -> 02:30 CET  (second occurrence, UTC+1)

            RaptorAlgorithm router = builder.withAddRoute("R1", ZURICH, List.of("A", "B"), 0, 30, 15, 0)
                    .withReferenceDate(date)
                    .withServiceDayRange(0, 5)
                    .build();

            // query during the first occurrence of 02:30 (CEST, earlier offset)
            OffsetDateTime queryFirstTime = date.atTime(2, 15)
                    .atZone(ZURICH)
                    .withEarlierOffsetAtOverlap()  // 02:15 CEST (UTC+2)
                    .toOffsetDateTime();

            List<Connection> results = RaptorRouterTestHelpers.routeEarliestArrival(router, "A", "B", queryFirstTime);

            assertFalse(results.isEmpty(), "Should find connection during first 02:xx hour (CEST)");
            OffsetDateTime departure = results.getFirst().getDepartureTime();
            assertEquals(departure.getOffset(), ZoneOffset.ofHours(2),
                    "Departure should have valid offset (either CEST +2)");
        }
    }

    @Nested
    @DisplayName("Date Rollover")
    class DateRollover {

        @Test
        @DisplayName("Midnight: Correctly transition from 23:50 to 00:10 (+1 Day)")
        void shouldHandleMidnight(RaptorRouterTestBuilder builder) {
            LocalDate date = LocalDate.of(2024, 6, 1);
            OffsetDateTime queryTime = date.atTime(23, 45).atZone(UTC).toOffsetDateTime();

            // R_Night: Dep 23:50 -> Arr 00:10 (20 min duration)
            builder.withAddRoute("Night", UTC, List.of("A", "B"), 23 * 60 + 50, 60, 20, 0);

            RaptorAlgorithm router = builder.withReferenceDate(date)
                    .withServiceDayRange(0, 30)
                    .withMaxDaysToScan(2)
                    .build();
            List<Connection> connections = RaptorRouterTestHelpers.routeEarliestArrival(router, "A", "B", queryTime);

            assertEquals(1, connections.size());
            assertEquals(date.plusDays(1), connections.getFirst().getArrivalTime().toLocalDate(),
                    "Arrival is next day");
            assertEquals(LocalTime.of(0, 10), connections.getFirst().getArrivalTime().toLocalTime());
        }
    }
}