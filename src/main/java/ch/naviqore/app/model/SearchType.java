package ch.naviqore.app.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public enum SearchType {

    EXACT("EXACT"),
    STARTS_WITH("STARTS_WITH"),
    CONTAINS("CONTAINS"),
    ENDS_WITH("ENDS_WITH"),
    FUZZY("FUZZY");

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

