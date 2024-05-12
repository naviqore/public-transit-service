package ch.naviqore.gtfs.schedule.spatial;

public interface Location<T extends TwoDimensionalCoordinates> {
    T getCoordinates();
}
