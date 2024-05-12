package ch.naviqore.gtfs.schedule.spatial;

/**
 * Enum for the type of coordinates.
 * <p> This enum is used to specify the type of coordinates in 2 Dimensions. </p>
 */
public enum CoordinatesType {
    PRIMARY,
    SECONDARY;

    public double getCoordinateValue(TwoDimensionalCoordinates coordinates) {
        return this == PRIMARY ? coordinates.getPrimaryCoordinate() : coordinates.getSecondaryCoordinate();
    }

    public double getCoordinateValue(Location<? extends TwoDimensionalCoordinates> location) {
        return getCoordinateValue(location.getCoordinates());
    }

}
