package ch.naviqore.api.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import javax.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Connection
 */

public class Connection {

  @JsonProperty("legs")
  @Valid
  private List<Leg> legs = null;

  public Connection legs(List<Leg> legs) {
    this.legs = legs;
    return this;
  }

  public Connection addLegsItem(Leg legsItem) {
    if (this.legs == null) {
      this.legs = new ArrayList<>();
    }
    this.legs.add(legsItem);
    return this;
  }

  /**
   * Get legs
   * @return legs
  */
  @Valid 
  @Schema(name = "legs", required = false)
  public List<Leg> getLegs() {
    return legs;
  }

  public void setLegs(List<Leg> legs) {
    this.legs = legs;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Connection connection = (Connection) o;
    return Objects.equals(this.legs, connection.legs);
  }

  @Override
  public int hashCode() {
    return Objects.hash(legs);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class Connection {\n");
    sb.append("    legs: ").append(toIndentedString(legs)).append("\n");
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

