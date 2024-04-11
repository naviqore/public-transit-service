package ch.naviqore.gtfs.schedule.type;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public enum ExceptionType {
    ADDED(1, "Service has been added for the specified date."),
    REMOVED(2, "Service has been removed for the specified date.");

    private final int value;
    private final String description;

    public static ExceptionType parse(String value) {
        return parse(Integer.parseInt(value));
    }

    public static ExceptionType parse(int value) {
        for (ExceptionType type : ExceptionType.values()) {
            if (type.value == value) {
                return type;
            }
        }
        throw new IllegalArgumentException("No exception type with value " + value + " found");
    }
}
