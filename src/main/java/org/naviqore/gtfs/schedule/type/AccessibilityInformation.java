package org.naviqore.gtfs.schedule.type;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Informs about accessibility for stops and trips as specified in the GTFS feed standards.
 * <p>
 * For more information on stop accessibility types, see <a href="https://gtfs.org/schedule/reference/#stopstxt">GTFS
 * Stops</a> and <a href="https://gtfs.org/schedule/reference/#tripstxt">GTFS Trips</a>.
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public enum AccessibilityInformation {

    UNKNOWN(0, "Unknown accessibility, maybe check parent stop for accessibility information"),
    ACCESSIBLE(1, "Is wheelchair accessible"),
    NOT_ACCESSIBLE(2, "Is not wheelchair accessible");

    private final int code;
    private final String description;

    public static AccessibilityInformation parse(String code) {
        if (code == null || code.isEmpty()) {
            return UNKNOWN;
        }
        return parse(Integer.parseInt(code));
    }

    public static AccessibilityInformation parse(int code) {
        for (AccessibilityInformation type : AccessibilityInformation.values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("No accessibility information with code " + code + " found");
    }

}
