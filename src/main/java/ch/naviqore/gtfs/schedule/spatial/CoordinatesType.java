package ch.naviqore.gtfs.schedule.spatial;

/**
 * Enum for the type of coordinates.
 * <p> This enum is used to specify the type of coordinates in 2 Dimensions. </p>
 */
public enum CoordinatesType {
    PRIMARY(0),
    SECONDARY(1);

    private int value;

    void setValue(int value) {
        this.value = value;
    }

    int getValue() {
        return value;
    }

    CoordinatesType(int value) {
        this.value = value;
    }

}
