package org.naviqore.gtfs.schedule.type;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Defines the types of transfers between routes as specified in the GTFS feed standards.
 * <p>
 * For more information on transfer types, see <a
 * href="https://support.google.com/transitpartners/answer/6377424?hl=en">GTFS Transfers</a>.
 *
 * @author munterfi
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public enum TransferType {
    RECOMMENDED(0, "Recommended transfer point between two routes."),
    TIMED(1, "Timed transfer between two routes. The departing vehicle is expected to wait for the arriving one."),
    MINIMUM_TIME(2, "Transfer requires a minimum amount of time between arrival and departure to ensure a connection."),
    NOT_POSSIBLE(3, "Transfer is not possible between routes at this location."),
    IN_SEAT(4, "Passengers can transfer from one trip to another by staying onboard the same vehicle."),
    IN_SEAT_FORBIDDEN(5, "In-seat transfers are not allowed. The passenger must alight from the vehicle and re-board.");

    private final int code;
    private final String description;

    public static TransferType parse(String code) {
        return parse(Integer.parseInt(code));
    }

    public static TransferType parse(int code) {
        for (TransferType type : TransferType.values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("No transfer type with code " + code + " found");
    }
}
