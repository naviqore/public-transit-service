package org.naviqore.gtfs.schedule.type;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

class ServiceDayTimeTest {

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

        @Test
        void shouldConvertToLocalTime() {
            ServiceDayTime sdt = new ServiceDayTime(25, 30, 0);
            LocalTime expectedTime = LocalTime.of(1, 30, 0);
            assertEquals(expectedTime, sdt.toLocalTime());
        }

        @Test
        void shouldConvertZeroSecondsToLocalTime() {
            ServiceDayTime sdt = new ServiceDayTime(0);
            LocalTime expectedTime = LocalTime.MIDNIGHT;
            assertEquals(expectedTime, sdt.toLocalTime());
        }

        @Test
        void shouldConvertToLocalDateTime() {
            LocalDate baseDate = LocalDate.of(2024, 1, 1);
            ServiceDayTime sdt = new ServiceDayTime(25, 30, 0);
            LocalDateTime expectedDateTime = LocalDateTime.of(2024, 1, 2, 1, 30, 0);
            assertEquals(expectedDateTime, sdt.toLocalDateTime(baseDate));
        }

        @Test
        void shouldConvertZeroSecondsToLocalDateTime() {
            LocalDate baseDate = LocalDate.of(2024, 1, 1);
            ServiceDayTime sdt = new ServiceDayTime(0);
            LocalDateTime expectedDateTime = LocalDateTime.of(baseDate, LocalTime.MIDNIGHT);
            assertEquals(expectedDateTime, sdt.toLocalDateTime(baseDate));
        }

        @Test
        void shouldConvertTotalSecondsInDayToLocalDateTime() {
            LocalDate baseDate = LocalDate.of(2024, 1, 1);
            ServiceDayTime sdt = new ServiceDayTime(ServiceDayTime.SECONDS_IN_DAY);
            LocalDateTime expectedDateTime = LocalDateTime.of(2024, 1, 2, 0, 0, 0);
            assertEquals(expectedDateTime, sdt.toLocalDateTime(baseDate));
        }

    }

}
