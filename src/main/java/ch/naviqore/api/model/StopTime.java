package ch.naviqore.api.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * StopTime
 */
@Getter
public class StopTime {

    @JsonProperty("stop")
    private Stop stop;

    @JsonProperty("arrivalTime")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime arrivalTime;

    @JsonProperty("departureTime")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime departureTime;

    public StopTime(Stop stop, LocalDateTime arrivalTime, LocalDateTime departureTime) {
        this.stop = stop;
        this.arrivalTime = arrivalTime;
        this.departureTime = departureTime;
    }

    public StopTime stop(Stop stop) {
        this.stop = stop;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        StopTime stopTime = (StopTime) o;
        return Objects.equals(this.stop, stopTime.stop) && Objects.equals(this.arrivalTime,
                stopTime.arrivalTime) && Objects.equals(this.departureTime, stopTime.departureTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(stop, arrivalTime, departureTime);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("class StopTime {\n");
        sb.append("    stop: ").append(toIndentedString(stop)).append("\n");
        sb.append("    arrivalTime: ").append(toIndentedString(arrivalTime)).append("\n");
        sb.append("    departureTime: ").append(toIndentedString(departureTime)).append("\n");
        sb.append("}");
        return sb.toString();
    }

    /**
     * Convert the given object to string with each line indented by 4 spaces (except the first line).
     */
    private String toIndentedString(Object o) {
        if (o == null) {
            return "null";
        }
        return o.toString().replace("\n", "\n    ");
    }
}

