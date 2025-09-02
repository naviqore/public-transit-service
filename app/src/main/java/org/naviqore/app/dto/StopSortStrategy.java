package org.naviqore.app.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public enum StopSortStrategy {

    ALPHABETICAL("ALPHABETICAL"),
    RELEVANCE("RELEVANCE");

    private final String value;

    @JsonCreator
    public static StopSortStrategy fromValue(String value) {
        for (StopSortStrategy b : StopSortStrategy.values()) {
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
