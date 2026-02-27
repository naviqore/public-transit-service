package org.naviqore.raptor.router;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.naviqore.raptor.TimeType;

import java.time.*;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies mathematical correctness of time normalization, timezone offsets, and daylight saving time (DST)
 * transitions. Ensures that internal UTC-anchored seconds map correctly to and from local wall-clock times.
 */
class DateTimeConverterTest {

    private static final LocalDate REF_DATE = LocalDate.of(2024, 3, 30);
    private static final ZoneId ZURICH = ZoneId.of("Europe/Zurich");
    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");

    @Nested
    @DisplayName("Reference Anchor Selection")
    class ReferenceSelection {

        @Test
        @DisplayName("Departure: selects the minimum absolute time as the anchor")
        void shouldReturnEarliestTimeForDepartureQuery() {
            Map<String, OffsetDateTime> stops = Map.of("A", REF_DATE.atTime(10, 0).atOffset(ZoneOffset.UTC), "B",
                    REF_DATE.atTime(8, 0).atOffset(ZoneOffset.UTC), "C",
                    REF_DATE.atTime(12, 0).atOffset(ZoneOffset.UTC));

            OffsetDateTime ref = DateTimeConverter.getReference(stops, TimeType.DEPARTURE);
            assertEquals(LocalTime.of(8, 0), ref.toLocalTime());
        }

        @Test
        @DisplayName("Arrival: selects the maximum absolute time as the anchor")
        void shouldReturnLatestTimeForArrivalQuery() {
            Map<String, OffsetDateTime> stops = Map.of("A", REF_DATE.atTime(10, 0).atOffset(ZoneOffset.UTC), "B",
                    REF_DATE.atTime(8, 0).atOffset(ZoneOffset.UTC), "C",
                    REF_DATE.atTime(12, 0).atOffset(ZoneOffset.UTC));

            OffsetDateTime ref = DateTimeConverter.getReference(stops, TimeType.ARRIVAL);
            assertEquals(LocalTime.of(12, 0), ref.toLocalTime());
        }
    }

    @Nested
    @DisplayName("UTC Seconds Normalization")
    class UtcSecondsNormalization {

        @Test
        @DisplayName("Anchor: midnight of the reference date maps to zero")
        void shouldReturnZeroForReferenceDateUtcMidnight() {
            OffsetDateTime midnight = REF_DATE.atStartOfDay().atOffset(ZoneOffset.UTC);
            int seconds = DateTimeConverter.toUtcSeconds(midnight, REF_DATE);

            assertEquals(0, seconds);
        }

        @Test
        @DisplayName("Early Morning: produces negative seconds if the instant is before UTC midnight")
        void shouldReturnNegativeSecondsForTimesBeforeReferenceUtcMidnight() {
            // instant: 2 hours before the reference date starts in UTC
            OffsetDateTime lateNight = REF_DATE.atStartOfDay().minusHours(2).atOffset(ZoneOffset.UTC);
            int seconds = DateTimeConverter.toUtcSeconds(lateNight, REF_DATE);

            assertEquals(-7200, seconds);

            // conversion back to Zurich winter time (UTC+1). 22:00 UTC (prev day) is 23:00 local (prev day)
            OffsetDateTime result = DateTimeConverter.toOffsetDateTime(seconds, REF_DATE, ZURICH);
            assertEquals(LocalTime.of(23, 0), result.toLocalTime());
            assertEquals(REF_DATE.minusDays(1), result.toLocalDate());
        }

        @Test
        @DisplayName("Multi-Day: correctly maps seconds exceeding a single day duration")
        void shouldHandleSecondsExceedingSingleDayDuration() {
            // instant: 48 hours after reference start
            int twoDaysInSeconds = 2 * 24 * 3600;
            OffsetDateTime result = DateTimeConverter.toOffsetDateTime(twoDaysInSeconds, REF_DATE, ZoneOffset.UTC);

            assertEquals(REF_DATE.plusDays(2), result.toLocalDate());
            assertEquals(LocalTime.MIDNIGHT, result.toLocalTime());
        }

        @Test
        @DisplayName("Bulk: maps a map of stops to internal UTC seconds")
        void shouldConvertBulkStopMapToUtcSeconds() {
            LocalDate date = LocalDate.of(2024, 6, 1);
            Map<String, OffsetDateTime> source = Map.of("start", date.atTime(12, 0).atOffset(ZoneOffset.UTC), "end",
                    date.atTime(13, 0).atOffset(ZoneOffset.UTC));

            Map<String, Integer> result = DateTimeConverter.mapToUtcSeconds(source, date);

            assertEquals(43200, result.get("start"));
            assertEquals(46800, result.get("end"));
        }
    }

    @Nested
    @DisplayName("Zone and DST Transitions")
    class ZoneAndDstTransitions {

        @Test
        @DisplayName("Offset Math: calculates correct wall-clock to UTC offsets for diverse zones")
        void shouldCalculateCorrectOffsetsAcrossDifferentSeasonsAndZones() {
            LocalDate winterDate = LocalDate.of(2024, 1, 1);
            LocalDate summerDate = LocalDate.of(2024, 7, 1);

            // Zurich: UTC+1 in winter, UTC+2 in summer
            assertEquals(-3600, DateTimeConverter.getLocalToUtcOffset(winterDate, ZURICH));
            assertEquals(-7200, DateTimeConverter.getLocalToUtcOffset(summerDate, ZURICH));

            // New York: UTC-5 in winter, UTC-4 in summer
            assertEquals(18000, DateTimeConverter.getLocalToUtcOffset(winterDate, NEW_YORK));
            assertEquals(14400, DateTimeConverter.getLocalToUtcOffset(summerDate, NEW_YORK));
        }

        @Test
        @DisplayName("Spring Forward: resolves non-existent local times by shifting forward")
        void shouldHandleSpringForwardGap() {
            // zurich spring forward 2024: clocks jump 02:00 -> 03:00 on March 31
            LocalDate gapDay = LocalDate.of(2024, 3, 31);

            // 01:30 UTC is halfway through the transition.
            // 01:00 UTC = 02:00 local (jumps to 03:00).
            // 01:30 UTC = 03:30 local (+02:00 offset).
            int utcSeconds = 3600 + 1800;

            OffsetDateTime result = DateTimeConverter.toOffsetDateTime(utcSeconds, gapDay, ZURICH);

            assertEquals(LocalTime.of(3, 30), result.toLocalTime());
            assertEquals(ZoneOffset.ofHours(2), result.getOffset());
        }

        @Test
        @DisplayName("Autumn Fallback: resolves overlapping hours using absolute UTC seconds")
        void shouldHandleAutumnOverlap() {
            // zurich autumn fallback 2024: clocks jump back 03:00 -> 02:00 on Oct 27
            LocalDate overlapDay = LocalDate.of(2024, 10, 27);

            // 01:30 UTC on fallback day.
            // 01:00 UTC is the point where local time jumps back from 03:00 to 02:00.
            // 01:30 UTC = 02:30 local (+01:00 offset).
            int utcSeconds = 3600 + 1800;

            OffsetDateTime result = DateTimeConverter.toOffsetDateTime(utcSeconds, overlapDay, ZURICH);

            assertEquals(LocalTime.of(2, 30), result.toLocalTime());
            assertEquals(ZoneOffset.ofHours(1), result.getOffset());
        }

        @Test
        @DisplayName("Service Day: uses noon to determine a stable offset for a service date")
        void shouldCalculateOffsetBasedOnNoon() {
            // transition happens at 02:00 AM. noon provides the offset for the majority of the service day.
            LocalDate transitionDay = LocalDate.of(2024, 3, 31);
            int offset = DateTimeConverter.getLocalToUtcOffset(transitionDay, ZURICH);

            assertEquals(-7200, offset);
        }
    }
}