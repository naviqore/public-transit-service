package ch.naviqore.api.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.List;
import java.util.Objects;

/**
 * Trip
 */
@Getter
public class Trip {

    @JsonProperty("headSign")
    private String headSign;

    @JsonProperty("route")
    private Route route;

    @JsonProperty("stopTimes")
    private List<StopTime> stopTimes;

    public Trip(String headSign, Route route, List<StopTime> stopTimes) {
        this.headSign = headSign;
        this.route = route;
        this.stopTimes = stopTimes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Trip trip = (Trip) o;
        return Objects.equals(this.headSign, trip.headSign) && Objects.equals(this.route, trip.route) && Objects.equals(
                this.stopTimes, trip.stopTimes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(headSign, route, stopTimes);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("class Trip {\n");
        sb.append("    headSign: ").append(toIndentedString(headSign)).append("\n");
        sb.append("    route: ").append(toIndentedString(route)).append("\n");
        sb.append("    stopTimes: ").append(toIndentedString(stopTimes)).append("\n");
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

