package org.naviqore.utils.spatial;

/**
 * A location in a generic spatial coordinate system.
 *
 * @param <T> The type of the coordinates that defines this location, must extend {@code Coordinate}.
 */
public interface Location<T extends Coordinate> {

    /**
     * Gets the coordinate that defines this location.
     */
    T getCoordinate();

}
