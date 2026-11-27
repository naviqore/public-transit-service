package org.naviqore.gtfs.schedule.type;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * The type of location of stops.txt: a stop with an empty location type is a stop or platform.
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public enum LocationType {
    STOP(0, "A location where passengers board or disembark from a transit vehicle, a platform within a station."),
    STATION(1, "A physical structure or area that contains one or more platforms."),
    ENTRANCE_EXIT(2, "A location where passengers can enter or exit a station from the street."),
    GENERIC_NODE(3, "A location within a station that links pathways together."),
    BOARDING_AREA(4, "A specific location on a platform, where passengers can board and alight vehicles.");

    private final int code;
    private final String description;

    public static LocationType parse(String code) {
        return code.isEmpty() ? STOP : parse(Integer.parseInt(code));
    }

    public static LocationType parse(int code) {
        for (LocationType type : LocationType.values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("No location type with code " + code + " found");
    }
}
