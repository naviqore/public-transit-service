package org.naviqore.gtfs.schedule.type;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.*;

import static org.junit.jupiter.api.Assertions.*;

class ServiceDayTimeTest {

    private static final ZoneId ZURICH = ZoneId.of("Europe/Zurich");

    @Nested
    class Constructor {

        @Test
        void shouldCreateServiceDayTimeFromSeconds() {
            ServiceDayTime sdt = new ServiceDayTime(3661);
            assertEquals(3661, sdt.getTotalSeconds());
        }

        @Test
        void shouldCreateServiceDayTimeFromHoursMinutesSeconds() {
            ServiceDayTime sdt = new ServiceDayTime(1, 1, 1);
            assertEquals(3661, sdt.getTotalSeconds());
        }

        @Test
        void shouldThrowExceptionForNegativeSeconds() {
            assertThrows(IllegalArgumentException.class, () -> new ServiceDayTime(-1));
        }

        @Test
        void shouldThrowExceptionForNegativeHoursMinutesSeconds() {
            assertThrows(IllegalArgumentException.class, () -> new ServiceDayTime(-1, 0, 0));
            assertThrows(IllegalArgumentException.class, () -> new ServiceDayTime(0, -1, 0));
            assertThrows(IllegalArgumentException.class, () -> new ServiceDayTime(0, 0, -1));
        }

        @Test
        void shouldThrowExceptionForInvalidMinutes() {
            assertThrows(IllegalArgumentException.class,
                    () -> new ServiceDayTime(0, ServiceDayTime.MINUTES_IN_HOUR, 0));
        }

        @Test
        void shouldThrowExceptionForInvalidSeconds() {
            assertThrows(IllegalArgumentException.class,
                    () -> new ServiceDayTime(0, 0, ServiceDayTime.SECONDS_IN_MINUTE));
        }
    }

    @Nested
    class Parse {

        @Test
        void shouldParseTimeStringCorrectly() {
            ServiceDayTime sdt = ServiceDayTime.parse("01:01:01");
            assertEquals(3661, sdt.getTotalSeconds());
        }
    }

    @Nested
    class Comparison {

        @Test
        void shouldCompareServiceDayTimesCorrectly() {
            ServiceDayTime sdt1 = new ServiceDayTime(ServiceDayTime.SECONDS_IN_HOUR);
            ServiceDayTime sdt2 = new ServiceDayTime(2 * ServiceDayTime.SECONDS_IN_HOUR);
            ServiceDayTime sdt3 = new ServiceDayTime(ServiceDayTime.SECONDS_IN_HOUR);

            assertTrue(sdt1.compareTo(sdt2) < 0);
            assertTrue(sdt2.compareTo(sdt1) > 0);
            assertEquals(0, sdt1.compareTo(sdt3));
        }
    }

    @Nested
    class Conversion {

        @Nested
        class Standard {
            @Test
            void shouldConvertToLocalTime() {
                // Test basic 24h modulo
                assertEquals(LocalTime.of(13, 30), new ServiceDayTime(13, 30, 0).toLocalTime());
                assertEquals(LocalTime.MIDNIGHT, new ServiceDayTime(0).toLocalTime());
            }

            @Test
            void shouldConvertToZonedDateTimeOnNormalDay() {
                // Standard winter day (CET / +01:00)
                LocalDate date = LocalDate.of(2026, 1, 15);
                ServiceDayTime sdt = new ServiceDayTime(10, 0, 0);
                ZonedDateTime zdt = sdt.toZonedDateTime(date, ZURICH);

                assertEquals(LocalTime.of(10, 0), zdt.toLocalTime());
                assertEquals(ZoneOffset.ofHours(1), zdt.getOffset());
            }

            @Test
            void shouldConvertToOffsetDateTimeOnNormalDay() {
                // Summer (CEST / +02:00)
                LocalDate date = LocalDate.of(2026, 6, 1);
                ServiceDayTime sdt = new ServiceDayTime(12, 0, 0);
                OffsetDateTime odt = sdt.toOffsetDateTime(date, ZURICH);

                assertEquals(12, odt.getHour());
                assertEquals(ZoneOffset.ofHours(2), odt.getOffset());
            }
        }

        @Nested
        class Overflow {
            @Test
            void shouldHandleTimesGreater24Hours() {
                LocalDate baseDate = LocalDate.of(2026, 1, 1);
                // 25:30:00 GTFS time is logically 01:30 AM on the next day
                ServiceDayTime sdt = new ServiceDayTime(25, 30, 0);
                ZonedDateTime zdt = sdt.toZonedDateTime(baseDate, ZURICH);

                assertEquals(LocalDate.of(2026, 1, 2), zdt.toLocalDate());
                assertEquals(LocalTime.of(1, 30), zdt.toLocalTime());
            }

            @Test
            void shouldHandleExactly24Hours() {
                LocalDate baseDate = LocalDate.of(2026, 1, 1);
                ServiceDayTime sdt = new ServiceDayTime(24, 0, 0);
                ZonedDateTime zdt = sdt.toZonedDateTime(baseDate, ZURICH);

                assertEquals(LocalDate.of(2026, 1, 2), zdt.toLocalDate());
                assertEquals(LocalTime.MIDNIGHT, zdt.toLocalTime());
            }
        }

        @Nested
        class DaylightSavingTime {
            /**
             * Test "Spring Forward" (Start of DST): In Zurich, on March 29, 2026, clocks jump from 02:00 to 03:00.
             * Service day anchor (noon - 12h) is March 28th 23:00 CET.
             */
            @Test
            void springForward_ShouldFollowGtfsNoonAnchor() {
                LocalDate date = LocalDate.of(2026, 3, 29);

                // GTFS 01:30:00 is 1.5h after anchor (23:00 prev day).
                // Results in 00:30 AM wall-clock.
                ServiceDayTime earlyTrip = new ServiceDayTime(1, 30, 0);
                ZonedDateTime zdt1 = earlyTrip.toZonedDateTime(date, ZURICH);
                assertEquals(LocalTime.of(0, 30), zdt1.toLocalTime());

                // GTFS 03:30:00 is 3.5h after anchor (23:00 prev day).
                // 23:00 + 1h (00:00) + 1h (01:00) + 1h (03:00 JUMP) + 0.5h = 03:30 AM wall-clock.
                ServiceDayTime afterJumpTrip = new ServiceDayTime(3, 30, 0);
                ZonedDateTime zdt2 = afterJumpTrip.toZonedDateTime(date, ZURICH);
                assertEquals(LocalTime.of(3, 30), zdt2.toLocalTime());

                // Physical duration is correctly 2 hours
                assertEquals(Duration.ofHours(2), Duration.between(zdt1, zdt2));
            }

            /**
             * Test "Fall Back" (End of DST): In Zurich, on October 25, 2026, clocks jump back from 03:00 to 02:00.
             * Service day anchor (noon - 12h) is October 25th 01:00 AM CEST.
             */
            @Test
            void fallBack_ShouldFollowGtfsNoonAnchor() {
                LocalDate date = LocalDate.of(2026, 10, 25);

                // GTFS 01:00:00 is 1h after anchor (01:00 AM CEST).
                // Results in 02:00 AM wall-clock (still CEST).
                ServiceDayTime earlyTrip = new ServiceDayTime(1, 0, 0);
                ZonedDateTime zdt1 = earlyTrip.toZonedDateTime(date, ZURICH);
                assertEquals(LocalTime.of(2, 0), zdt1.toLocalTime());

                // GTFS 04:00:00 is 4h after anchor (01:00 AM CEST).
                // Anchor 01:00 + 4h (incl. jump back) = 04:00 AM wall-clock.
                ServiceDayTime afterJumpTrip = new ServiceDayTime(4, 0, 0);
                ZonedDateTime zdt2 = afterJumpTrip.toZonedDateTime(date, ZURICH);
                assertEquals(LocalTime.of(4, 0), zdt2.toLocalTime());

                // Physical duration is correctly 3 hours
                assertEquals(Duration.ofHours(3), Duration.between(zdt1, zdt2));
            }

            /**
             * The 'noon minus 12h' rule ensures that standard daytime trips (e.g. 08:00 AM) ALWAYS match the local
             * wall-clock time, regardless of the day's physical length.
             */
            @Test
            void standardDaytimeTrips_ShouldAlwaysMatchClockFace() {
                ServiceDayTime tripAtEight = new ServiceDayTime(8, 0, 0);

                // Standard day
                assertEquals(LocalTime.of(8, 0),
                        tripAtEight.toZonedDateTime(LocalDate.of(2026, 1, 1), ZURICH).toLocalTime());
                // Spring Forward
                assertEquals(LocalTime.of(8, 0),
                        tripAtEight.toZonedDateTime(LocalDate.of(2026, 3, 29), ZURICH).toLocalTime());
                // Fall Back
                assertEquals(LocalTime.of(8, 0),
                        tripAtEight.toZonedDateTime(LocalDate.of(2026, 10, 25), ZURICH).toLocalTime());
            }
        }
    }
}