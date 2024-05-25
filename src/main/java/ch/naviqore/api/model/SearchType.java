package ch.naviqore.api.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Gets or Sets SearchType
 */

public enum SearchType {
  
  EXACT("EXACT"),
  
  STARTS_WITH("STARTS_WITH"),
  
  CONTAINS("CONTAINS"),
  
  ENDS_WITH("ENDS_WITH"),
  
  FUZZY("FUZZY");

  private final String value;

  SearchType(String value) {
    this.value = value;
  }

  @JsonValue
  public String getValue() {
    return value;
  }

  @Override
  public String toString() {
    return String.valueOf(value);
  }

  @JsonCreator
  public static SearchType fromValue(String value) {
    for (SearchType b : SearchType.values()) {
      if (b.value.equals(value)) {
        return b;
      }
    }
    throw new IllegalArgumentException("Unexpected value '" + value + "'");
  }
}

