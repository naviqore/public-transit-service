package ch.naviqore.gtfs.schedule.type;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Defines the types of accessibility for stops as specified in the GTFS feed standards.
 * <p>
 * For more information on stop accessibility types, see <a href="https://gtfs.org/schedule/reference/#stopstxt">GTFS Stops</a>.
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public enum StopAccessibilityType {

    UNKNOWN(0, "Unknown accessibility, maybe check parent stop for accessibility information"),
    ACCESSIBLE(1, "Stop is wheelchair accessible"),
    NOT_ACCESSIBLE(2, "Stop is not wheelchair accessible");

    private final int code;
    private final String description;

    public static StopAccessibilityType parse(String code) {
        if (code == null || code.isEmpty()) {
            return UNKNOWN;
        }
        return parse(Integer.parseInt(code));
    }

    public static StopAccessibilityType parse(int code) {
        for (StopAccessibilityType type : StopAccessibilityType.values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("No stop accessibility type with code " + code + " found");
    }

}
