package ch.naviqore.api.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import javax.validation.constraints.*;
import java.util.Objects;

/**
 * Leg
 */

public class Leg {

  @JsonProperty("id")
  private String id;

  @JsonProperty("fromStopId")
  private String fromStopId;

  @JsonProperty("toStopId")
  private String toStopId;

  @JsonProperty("departureTime")
  private String departureTime;

  @JsonProperty("arrivalTime")
  private String arrivalTime;

  @JsonProperty("type")
  private String type;

  public Leg id(String id) {
    this.id = id;
    return this;
  }

  /**
   * Get id
   * @return id
  */
  
  @Schema(name = "id", required = false)
  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public Leg fromStopId(String fromStopId) {
    this.fromStopId = fromStopId;
    return this;
  }

  /**
   * Get fromStopId
   * @return fromStopId
  */
  
  @Schema(name = "fromStopId", required = false)
  public String getFromStopId() {
    return fromStopId;
  }

  public void setFromStopId(String fromStopId) {
    this.fromStopId = fromStopId;
  }

  public Leg toStopId(String toStopId) {
    this.toStopId = toStopId;
    return this;
  }

  /**
   * Get toStopId
   * @return toStopId
  */
  
  @Schema(name = "toStopId", required = false)
  public String getToStopId() {
    return toStopId;
  }

  public void setToStopId(String toStopId) {
    this.toStopId = toStopId;
  }

  public Leg departureTime(String departureTime) {
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

  public Leg arrivalTime(String arrivalTime) {
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

  public Leg type(String type) {
    this.type = type;
    return this;
  }

  /**
   * Get type
   * @return type
  */
  
  @Schema(name = "type", required = false)
  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
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
    return Objects.equals(this.id, leg.id) &&
        Objects.equals(this.fromStopId, leg.fromStopId) &&
        Objects.equals(this.toStopId, leg.toStopId) &&
        Objects.equals(this.departureTime, leg.departureTime) &&
        Objects.equals(this.arrivalTime, leg.arrivalTime) &&
        Objects.equals(this.type, leg.type);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, fromStopId, toStopId, departureTime, arrivalTime, type);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class Leg {\n");
    sb.append("    id: ").append(toIndentedString(id)).append("\n");
    sb.append("    fromStopId: ").append(toIndentedString(fromStopId)).append("\n");
    sb.append("    toStopId: ").append(toIndentedString(toStopId)).append("\n");
    sb.append("    departureTime: ").append(toIndentedString(departureTime)).append("\n");
    sb.append("    arrivalTime: ").append(toIndentedString(arrivalTime)).append("\n");
    sb.append("    type: ").append(toIndentedString(type)).append("\n");
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

