package ch.naviqore.gtfs.schedule.type;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Informs about bike options for trips as specified in the GTFS feed standards.
 * <p>
 * For more information on trip bike option types, see <a href="https://gtfs.org/schedule/reference/#tripstxt">GTFS Trips</a>.
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public enum BikeInformation {

    UNKNOWN(0, "No bike information for the trip."),
    ALLOWED(1, "Vehicle being used on this particular trip can accommodate at least one bicycle."),
    NOT_ALLOWED(2, "No bicycles are allowed on this trip.");

    private final int code;
    private final String description;

    public static BikeInformation parse(String code) {
        if (code == null || code.isEmpty()) {
            return UNKNOWN;
        }
        return parse(Integer.parseInt(code));
    }

    public static BikeInformation parse(int code) {
        for (BikeInformation type : BikeInformation.values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("No bike information with code " + code + " found");
    }

}
