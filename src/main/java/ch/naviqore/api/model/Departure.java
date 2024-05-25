package ch.naviqore.api.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.Objects;

/**
 * Departure
 */
@Getter
public class Departure {

    @JsonProperty("stopTime")
    private StopTime stopTime;

    @JsonProperty("trip")
    private Trip trip;

    public Departure(StopTime stopTime, Trip trip) {
        this.stopTime = stopTime;
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
        Departure departure = (Departure) o;
        return Objects.equals(this.stopTime, departure.stopTime) && Objects.equals(this.trip, departure.trip);
    }

    @Override
    public int hashCode() {
        return Objects.hash(stopTime, trip);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("class Departure {\n");
        sb.append("    stopTime: ").append(toIndentedString(stopTime)).append("\n");
        sb.append("    trip: ").append(toIndentedString(trip)).append("\n");
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

