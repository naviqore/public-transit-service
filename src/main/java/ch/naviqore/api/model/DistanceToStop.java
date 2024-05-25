package ch.naviqore.api.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.Objects;

/**
 * DistanceToStop
 */
@Getter
public class DistanceToStop {

    @JsonProperty("stop")
    private Stop stop;

    @JsonProperty("distance")
    private double distance;

    public DistanceToStop(Stop stop, double distance) {
        this.stop = stop;
        this.distance = distance;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DistanceToStop distanceToStop = (DistanceToStop) o;
        return Objects.equals(this.stop, distanceToStop.stop) && Objects.equals(this.distance, distanceToStop.distance);
    }

    @Override
    public int hashCode() {
        return Objects.hash(stop, distance);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("class DistanceToStop {\n");
        sb.append("    stop: ").append(toIndentedString(stop)).append("\n");
        sb.append("    distance: ").append(toIndentedString(distance)).append("\n");
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

