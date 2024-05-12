package ch.naviqore.utils.spatial;

/**
 * Enum for the type of coordinates.
 * <p> This enum is used to specify the type of coordinates in 2 Dimensions. </p>
 */
public enum CoordinateComponentType {
    FIRST,
    SECOND;

    public double getCoordinateComponent(TwoDimensionalCoordinate coordinate) {
        return this == FIRST ? coordinate.getFirstComponent() : coordinate.getSecondComponent();
    }

    public double getCoordinateComponent(Location<? extends TwoDimensionalCoordinate> location) {
        return getCoordinateComponent(location.getCoordinate());
    }

}
