package ch.naviqore.api.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.Objects;

/**
 * Stop
 */

@Getter
public class Stop {

    @JsonProperty("id")
    private String id;

    @JsonProperty("name")
    private String name;

    @JsonProperty("coordinates")
    private Coordinate coordinates;

    public Stop(String id, String name, Coordinate coordinates) {
        this.id = id;
        this.name = name;
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
        Stop stop = (Stop) o;
        return Objects.equals(this.id, stop.id) && Objects.equals(this.name, stop.name) && Objects.equals(
                this.coordinates, stop.coordinates);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, coordinates);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("class Stop {\n");
        sb.append("    id: ").append(toIndentedString(id)).append("\n");
        sb.append("    name: ").append(toIndentedString(name)).append("\n");
        sb.append("    coordinates: ").append(toIndentedString(coordinates)).append("\n");
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

