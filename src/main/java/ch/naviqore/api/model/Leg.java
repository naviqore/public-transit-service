package ch.naviqore.api.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Leg
 */
@Getter
public class Leg {

    @JsonProperty("from")
    private Coordinate from;

    @JsonProperty("to")
    private Coordinate to;

    @JsonProperty("fromStop")
    private Stop fromStop;

    @JsonProperty("toStop")
    private Stop toStop;

    @JsonProperty("type")
    private LegType type;

    @JsonProperty("departureTime")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime departureTime;

    @JsonProperty("arrivalTime")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime arrivalTime;

    @JsonProperty("trip")
    private Trip trip;

    public Leg(Coordinate from, Coordinate to, Stop fromStop, Stop toStop, LegType type, LocalDateTime departureTime,
               LocalDateTime arrivalTime, Trip trip) {
        this.from = from;
        this.to = to;
        this.fromStop = fromStop;
        this.toStop = toStop;
        this.type = type;
        this.departureTime = departureTime;
        this.arrivalTime = arrivalTime;
        this.trip = trip;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Leg leg = (Leg) o;
        return Objects.equals(this.from, leg.from) && Objects.equals(this.to, leg.to) && Objects.equals(this.fromStop,
                leg.fromStop) && Objects.equals(this.toStop, leg.toStop) && Objects.equals(this.type,
                leg.type) && Objects.equals(this.departureTime, leg.departureTime) && Objects.equals(this.arrivalTime,
                leg.arrivalTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(from, to, fromStop, toStop, type, departureTime, arrivalTime);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("class Leg {\n");
        sb.append("    from: ").append(toIndentedString(from)).append("\n");
        sb.append("    to: ").append(toIndentedString(to)).append("\n");
        sb.append("    fromStop: ").append(toIndentedString(fromStop)).append("\n");
        sb.append("    toStop: ").append(toIndentedString(toStop)).append("\n");
        sb.append("    type: ").append(toIndentedString(type)).append("\n");
        sb.append("    departureTime: ").append(toIndentedString(departureTime)).append("\n");
        sb.append("    arrivalTime: ").append(toIndentedString(arrivalTime)).append("\n");
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

