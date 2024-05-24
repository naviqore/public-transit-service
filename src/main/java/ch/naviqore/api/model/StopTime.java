package ch.naviqore.api.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Objects;

/**
 * StopTime
 */

public class StopTime {

  @JsonProperty("tripId")
  private String tripId;

  @JsonProperty("stopId")
  private String stopId;

  @JsonProperty("arrivalTime")
  private String arrivalTime;

  @JsonProperty("departureTime")
  private String departureTime;

  public StopTime tripId(String tripId) {
    this.tripId = tripId;
    return this;
  }

  /**
   * Get tripId
   * @return tripId
  */
  
  @Schema(name = "tripId", required = false)
  public String getTripId() {
    return tripId;
  }

  public void setTripId(String tripId) {
    this.tripId = tripId;
  }

  public StopTime stopId(String stopId) {
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

  public StopTime arrivalTime(String arrivalTime) {
    this.arrivalTime = arrivalTime;
    return this;
  }

  /**
   * Get arrivalTime
   * @return arrivalTime
  */
  
  @Schema(name = "arrivalTime", required = false)
  public String getArrivalTime() {
    return arrivalTime;
  }

  public void setArrivalTime(String arrivalTime) {
    this.arrivalTime = arrivalTime;
  }

  public StopTime departureTime(String departureTime) {
    this.departureTime = departureTime;
    return this;
  }

  /**
   * Get departureTime
   * @return departureTime
  */
  
  @Schema(name = "departureTime", required = false)
  public String getDepartureTime() {
    return departureTime;
  }

  public void setDepartureTime(String departureTime) {
    this.departureTime = departureTime;
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
    return Objects.equals(this.tripId, stopTime.tripId) &&
        Objects.equals(this.stopId, stopTime.stopId) &&
        Objects.equals(this.arrivalTime, stopTime.arrivalTime) &&
        Objects.equals(this.departureTime, stopTime.departureTime);
  }

  @Override
  public int hashCode() {
    return Objects.hash(tripId, stopId, arrivalTime, departureTime);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class StopTime {\n");
    sb.append("    tripId: ").append(toIndentedString(tripId)).append("\n");
    sb.append("    stopId: ").append(toIndentedString(stopId)).append("\n");
    sb.append("    arrivalTime: ").append(toIndentedString(arrivalTime)).append("\n");
    sb.append("    departureTime: ").append(toIndentedString(departureTime)).append("\n");
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

