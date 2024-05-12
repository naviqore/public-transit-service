package ch.naviqore.utils.spatial;

public interface TwoDimensionalCoordinate {

    /**
     * Examples for the first component are latitude or X coordinate.
     * @return the first component of the 2D-coordinate
     */
    double getFirstComponent();

    /**
     * Examples for the first component are longitude or Y coordinate.
     * @return the second component of the 2D-coordinate
     */
    double getSecondComponent();
    double distanceTo(TwoDimensionalCoordinate other);
}
