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

    public static final int SECONDS_IN_DAY = 86400;
    private final int totalSeconds;

    public ServiceDayTime(int seconds) {
        if (seconds < 0) {
            throw new IllegalArgumentException("Seconds cannot be negative");
        }
        this.totalSeconds = seconds;
    }

    public ServiceDayTime(int hours, int minutes, int seconds) {
        if (hours < 0 || minutes < 0 || seconds < 0) {
            throw new IllegalArgumentException("Hours, minutes, and seconds cannot be negative");
        }
        this.totalSeconds = seconds + 60 * minutes + 3600 * hours;
    }

    public static ServiceDayTime parse(String timeString) {
        String[] parts = timeString.split(":");
        int hours = Integer.parseInt(parts[0]);
        int minutes = Integer.parseInt(parts[1]);
        int seconds = Integer.parseInt(parts[2]);
        return new ServiceDayTime(hours, minutes, seconds);
    }

    public LocalTime toLocalTime() {
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int seconds = totalSeconds % 60;
        return LocalTime.of(hours % 24, minutes, seconds);
    }

    public LocalDateTime toLocalDateTime(LocalDate date) {
        LocalTime localTime = this.toLocalTime();
        int hours = totalSeconds / 3600;
        LocalDate adjustedDate = date.plusDays(hours / 24);
        return LocalDateTime.of(adjustedDate, localTime);
    }

    @Override
    public int compareTo(ServiceDayTime o) {
        return Integer.compare(totalSeconds, o.totalSeconds);
    }

    @Override
    public String toString() {
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int seconds = totalSeconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

}
