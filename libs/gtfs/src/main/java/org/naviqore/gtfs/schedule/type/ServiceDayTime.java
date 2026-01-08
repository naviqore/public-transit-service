package org.naviqore.gtfs.schedule.type;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.time.*;

/**
 * ServiceDayTime handles GTFS time values using the official "noon minus 12h" reference.
 * <p>
 * According to the <a href="https://gtfs.org/documentation/schedule/reference/">GTFS Reference</a>: "The time is
 * measured from 'noon minus 12h' of the service day (effectively midnight except for days on which daylight savings
 * time changes occur)."
 * <p>
 * This implementation strictly follows the specification by anchoring the service day at 12:00 PM local time and
 * subtracting 12 physical hours to find the start of the service day. GTFS seconds are then added as a physical
 * duration from this anchor. This ensures that the resulting time matches the local wall-clock shown at the transit
 * location.
 *
 * @author munterfi
 */
@EqualsAndHashCode
@Getter
public final class ServiceDayTime implements Comparable<ServiceDayTime> {

    public static final int HOURS_IN_DAY = 24;
    public static final int MINUTES_IN_HOUR = 60;
    public static final int SECONDS_IN_MINUTE = 60;
    public static final int SECONDS_IN_HOUR = MINUTES_IN_HOUR * SECONDS_IN_MINUTE;

    private final int totalSeconds;

    public ServiceDayTime(int seconds) {
        if (seconds < 0) {
            throw new IllegalArgumentException("Seconds cannot be negative.");
        }
        this.totalSeconds = seconds;
    }

    public ServiceDayTime(int hours, int minutes, int seconds) {
        if (hours < 0) {
            throw new IllegalArgumentException("Hours cannot be negative.");
        }
        if (minutes < 0 || minutes >= MINUTES_IN_HOUR) {
            throw new IllegalArgumentException("Minutes must be between 0 and 59 inclusive");
        }
        if (seconds < 0 || seconds >= SECONDS_IN_MINUTE) {
            throw new IllegalArgumentException("Seconds must be between 0 and 59 inclusive");
        }
        this.totalSeconds = seconds + SECONDS_IN_MINUTE * minutes + SECONDS_IN_HOUR * hours;
    }

    public static ServiceDayTime parse(String timeString) {
        String[] parts = timeString.split(":");
        int hours = Integer.parseInt(parts[0]);
        int minutes = Integer.parseInt(parts[1]);
        int seconds = Integer.parseInt(parts[2]);
        return new ServiceDayTime(hours, minutes, seconds);
    }

    /**
     * Converts GTFS seconds to a ZonedDateTime using the "noon minus 12h" rule. This method is DST-safe and ensures
     * wall-clock consistency.
     *
     * @param date The service date (calendar date).
     * @param zone The timezone of the agency.
     * @return ZonedDateTime representing the scheduled time at the local clock.
     */
    public ZonedDateTime toZonedDateTime(LocalDate date, ZoneId zone) {
        // GTFS seconds-of-day anchor point
        ZonedDateTime noon = date.atTime(12, 0).atZone(zone);
        ZonedDateTime gtfsAnchor = noon.minusHours(12);

        // add physical duration from anchor
        return gtfsAnchor.plusSeconds(totalSeconds);
    }

    /**
     * Converts to OffsetDateTime for use in REST APIs (ISO 8601).
     */
    public OffsetDateTime toOffsetDateTime(LocalDate date, ZoneId zone) {
        return toZonedDateTime(date, zone).toOffsetDateTime();
    }

    /**
     * Returns the LocalTime representation (modulo 24h).
     */
    public LocalTime toLocalTime() {
        int hours = totalSeconds / SECONDS_IN_HOUR;
        int minutes = (totalSeconds % SECONDS_IN_HOUR) / SECONDS_IN_MINUTE;
        int seconds = totalSeconds % SECONDS_IN_MINUTE;
        return LocalTime.of(hours % HOURS_IN_DAY, minutes, seconds);
    }

    @Override
    public int compareTo(ServiceDayTime o) {
        return Integer.compare(totalSeconds, o.totalSeconds);
    }

    @Override
    public String toString() {
        int hours = totalSeconds / SECONDS_IN_HOUR;
        int minutes = (totalSeconds % SECONDS_IN_HOUR) / SECONDS_IN_MINUTE;
        int seconds = totalSeconds % SECONDS_IN_MINUTE;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }
}