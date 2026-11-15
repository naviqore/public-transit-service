package org.naviqore.timetable.type;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Whether passengers can board or alight at a stop of a trip, with the values and codes specified in the GTFS feed
 * standards for the pickup and the drop-off of a stop time.
 * <p>
 * For more information, see <a href="https://gtfs.org/schedule/reference/#stop_timestxt">GTFS Stop Times</a>.
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public enum PickupDropOffType {

    REGULAR(0, "Regularly scheduled"),
    NOT_AVAILABLE(1, "Not available"),
    PHONE_AGENCY(2, "Must phone the agency to arrange"),
    COORDINATE_WITH_DRIVER(3, "Must coordinate with the driver to arrange");

    private final int code;
    private final String description;

    public static PickupDropOffType parse(String code) {
        if (code == null || code.isEmpty()) {
            return REGULAR;
        }
        return parse(Integer.parseInt(code));
    }

    public static PickupDropOffType parse(int code) {
        for (PickupDropOffType type : PickupDropOffType.values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("No pickup or drop-off type with code " + code + " found");
    }

    /**
     * Whether passengers can board (alight), also if it has to be arranged.
     *
     * @return false if it is not available, true otherwise
     */
    public boolean isPossible() {
        return this != NOT_AVAILABLE;
    }

}
