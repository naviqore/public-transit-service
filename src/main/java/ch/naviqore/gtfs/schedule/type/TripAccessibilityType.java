package ch.naviqore.gtfs.schedule.type;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Defines the types of accessibility for trips as specified in the GTFS feed standards.
 * <p>
 * For more information on trip accessibility types, see <a href="https://gtfs.org/schedule/reference/#tripstxt">GTFS Trips</a>.
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public enum TripAccessibilityType {

    UNKNOWN(0, "No accessibility information for the trip."),
    ACCESSIBLE(1, "Vehicle being used on this particular trip can accommodate at least one rider in a wheelchair."),
    NOT_ACCESSIBLE(2, "No riders in wheelchairs can be accommodated on this trip.");

    private final int code;
    private final String description;

    public static TripAccessibilityType parse(String code) {
        if (code == null || code.isEmpty()) {
            return UNKNOWN;
        }
        return parse(Integer.parseInt(code));
    }

    public static TripAccessibilityType parse(int code) {
        for (TripAccessibilityType type : TripAccessibilityType.values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("No trip accessibility type with code " + code + " found");
    }

}
