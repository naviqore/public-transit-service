package ch.naviqore.api.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * EarliestArrival
 */
@Getter
public class EarliestArrival {

    @JsonProperty("stop")
    private Stop stop;

    @JsonProperty("arrivalTime")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime arrivalTime;

    @JsonProperty("connection")
    private Connection connection;

    public EarliestArrival(Stop stop, LocalDateTime arrivalTime, Connection connection) {
        this.stop = stop;
        this.arrivalTime = arrivalTime;
        this.connection = connection;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        EarliestArrival earliestArrival = (EarliestArrival) o;
        return Objects.equals(this.stop, earliestArrival.stop) && Objects.equals(this.arrivalTime,
                earliestArrival.arrivalTime) && Objects.equals(this.connection, earliestArrival.connection);
    }

    @Override
    public int hashCode() {
        return Objects.hash(stop, arrivalTime, connection);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("class EarliestArrival {\n");
        sb.append("    stop: ").append(toIndentedString(stop)).append("\n");
        sb.append("    arrivalTime: ").append(toIndentedString(arrivalTime)).append("\n");
        sb.append("    connection: ").append(toIndentedString(connection)).append("\n");
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

