package ch.naviqore.gtfs.schedule.type;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Service day time
 * <p>
 * A service day may end after 24:00:00 and times like 25:30:00 are possible values. java.time.LocalTime does not
 * support this. Therefore, this time class is used to be able to cover service days bigger than 24 hours.
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
    public static final int SECONDS_IN_DAY = HOURS_IN_DAY * SECONDS_IN_HOUR;

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

    public LocalTime toLocalTime() {
        int hours = totalSeconds / SECONDS_IN_HOUR;
        int minutes = (totalSeconds % SECONDS_IN_HOUR) / SECONDS_IN_MINUTE;
        int seconds = totalSeconds % SECONDS_IN_MINUTE;
        return LocalTime.of(hours % HOURS_IN_DAY, minutes, seconds);
    }

    public LocalDateTime toLocalDateTime(LocalDate date) {
        LocalTime localTime = this.toLocalTime();
        int hours = totalSeconds / SECONDS_IN_HOUR;
        LocalDate adjustedDate = date.plusDays(hours / HOURS_IN_DAY);
        return LocalDateTime.of(adjustedDate, localTime);
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
