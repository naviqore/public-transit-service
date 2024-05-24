package ch.naviqore.api.model;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.OffsetDateTime;
import javax.validation.Valid;
import javax.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;


import java.util.*;

/**
 * Isoline
 */

public class Isoline {

    @JsonProperty("stopId")
    private String stopId;

    @JsonProperty("departureTime")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private OffsetDateTime departureTime;

    @JsonProperty("coordinates")
    @Valid
    private List<IsolineCoordinatesInner> coordinates = null;

    public Isoline stopId(String stopId) {
        this.stopId = stopId;
        return this;
    }

    /**
     * Get stopId
     * @return stopId
     */

    @Schema(name = "stopId", required = false)
    public String getStopId() {
        return stopId;
    }

    public void setStopId(String stopId) {
        this.stopId = stopId;
    }

    public Isoline departureTime(OffsetDateTime departureTime) {
        this.departureTime = departureTime;
        return this;
    }

    /**
     * Get departureTime
     * @return departureTime
     */
    @Valid
    @Schema(name = "departureTime", required = false)
    public OffsetDateTime getDepartureTime() {
        return departureTime;
    }

    public void setDepartureTime(OffsetDateTime departureTime) {
        this.departureTime = departureTime;
    }

    public Isoline coordinates(List<IsolineCoordinatesInner> coordinates) {
        this.coordinates = coordinates;
        return this;
    }

    public Isoline addCoordinatesItem(IsolineCoordinatesInner coordinatesItem) {
        if (this.coordinates == null) {
            this.coordinates = new ArrayList<>();
        }
        this.coordinates.add(coordinatesItem);
        return this;
    }

    /**
     * Get coordinates
     * @return coordinates
     */
    @Valid
    @Schema(name = "coordinates", required = false)
    public List<IsolineCoordinatesInner> getCoordinates() {
        return coordinates;
    }

    public void setCoordinates(List<IsolineCoordinatesInner> coordinates) {
        this.coordinates = coordinates;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Isoline isoline = (Isoline) o;
        return Objects.equals(this.stopId, isoline.stopId) &&
                Objects.equals(this.departureTime, isoline.departureTime) &&
                Objects.equals(this.coordinates, isoline.coordinates);
    }

    @Override
    public int hashCode() {
        return Objects.hash(stopId, departureTime, coordinates);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("class Isoline {\n");
        sb.append("    stopId: ").append(toIndentedString(stopId)).append("\n");
        sb.append("    departureTime: ").append(toIndentedString(departureTime)).append("\n");
        sb.append("    coordinates: ").append(toIndentedString(coordinates)).append("\n");
        sb.append("}");
        return sb.toString();
    }

    /**
     * Convert the given object to string with each line indented by 4 spaces
     * (except the first line).
     */
    private String toIndentedString(Object o) {
        if (o == null) {
            return "null";
        }
        return o.toString().replace("\n", "\n    ");
    }
}

