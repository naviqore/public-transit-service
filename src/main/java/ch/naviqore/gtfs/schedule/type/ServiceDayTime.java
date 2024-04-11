package ch.naviqore.gtfs.schedule.type;

import lombok.EqualsAndHashCode;
import lombok.Getter;


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
public class ServiceDayTime implements Comparable<ServiceDayTime> {
    private final int totalSeconds;

    public ServiceDayTime(int hours, int minutes, int seconds) {
        this.totalSeconds = seconds + 60 * minutes + 3600 * hours;
    }

    public static ServiceDayTime parse(String timeString) {
        String[] parts = timeString.split(":");
        int hours = Integer.parseInt(parts[0]);
        int minutes = Integer.parseInt(parts[1]);
        int seconds = Integer.parseInt(parts[2]);
        return new ServiceDayTime(hours, minutes, seconds);
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
