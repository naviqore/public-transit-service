package org.naviqore.app.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public enum SearchType {

    STARTS_WITH("STARTS_WITH"),
    ENDS_WITH("ENDS_WITH"),
    CONTAINS("CONTAINS"),
    EXACT("EXACT");

    private final String value;

    @JsonCreator
    public static SearchType fromValue(String value) {
        for (SearchType b : SearchType.values()) {
            if (b.value.equals(value)) {
                return b;
            }
        }
        throw new IllegalArgumentException("Unexpected value '" + value + "'");
    }

    @JsonValue
    public String getValue() {
        return value;
    }

}

