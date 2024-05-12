package ch.naviqore.utils.spatial;

public interface Location<T extends TwoDimensionalCoordinate> {
    T getCoordinate();
}
