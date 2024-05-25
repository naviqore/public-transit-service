package ch.naviqore.api.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.Objects;

/**
 * Route
 */

@Getter
public class Route {

    @JsonProperty("id")
    private String id;

    @JsonProperty("name")
    private String name;

    @JsonProperty("shortName")
    private String shortName;

    @JsonProperty("transportMode")
    private String transportMode;

    public Route(String id, String name, String shortName, String transportMode) {
        this.id = id;
        this.name = name;
        this.shortName = shortName;
        this.transportMode = transportMode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Route route = (Route) o;
        return Objects.equals(this.id, route.id) && Objects.equals(this.name, route.name) && Objects.equals(
                this.shortName, route.shortName) && Objects.equals(this.transportMode, route.transportMode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, shortName, transportMode);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("class Route {\n");
        sb.append("    id: ").append(toIndentedString(id)).append("\n");
        sb.append("    name: ").append(toIndentedString(name)).append("\n");
        sb.append("    shortName: ").append(toIndentedString(shortName)).append("\n");
        sb.append("    transportMode: ").append(toIndentedString(transportMode)).append("\n");
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

