package ch.naviqore.utils.spatial;

/**
 * This interface represents a generic 2D coordinate. Implementations of this interface should provide methods to get
 * the two components of the coordinate and calculate distances.
 */
public interface Coordinate {

    /**
     * Examples for the first component are latitude or X coordinate.
     *
     * @return the first component of the 2D-coordinate
     */
    double getFirstComponent();

    /**
     * Examples for the first component are longitude or Y coordinate.
     *
     * @return the second component of the 2D-coordinate
     */
    double getSecondComponent();

    /**
     * Calculates the distance to another {@code Coordinate} object.
     * <p>
     * Note: Implementations may raise an {@code IllegalArgumentException} if the other {@code Coordinate} object is not
     * of the same type.
     */
    double distanceTo(Coordinate other);

    /**
     * Calculates the distance to another point specified by its components.
     */
    double distanceTo(double firstComponent, double secondComponent);

    /**
     * Gets the coordinate component based on the specified {@code Axis}.
     */
    default double getComponent(Axis axis) {
        return axis == Axis.FIRST ? getFirstComponent() : getSecondComponent();
    }

    /**
     * The axes of a 2D coordinate system.
     */
    enum Axis {
        FIRST,
        SECOND
    }
}
