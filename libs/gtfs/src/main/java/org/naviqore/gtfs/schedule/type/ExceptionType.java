package org.naviqore.gtfs.schedule.type;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public enum ExceptionType {
    ADDED(1, "Service has been added for the specified date."),
    REMOVED(2, "Service has been removed for the specified date.");

    private final int code;
    private final String description;

    public static ExceptionType parse(String code) {
        return parse(Integer.parseInt(code));
    }

    public static ExceptionType parse(int code) {
        for (ExceptionType type : ExceptionType.values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("No exception type with code " + code + " found");
    }
}
