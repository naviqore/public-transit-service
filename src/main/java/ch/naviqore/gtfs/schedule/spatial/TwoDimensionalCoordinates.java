package ch.naviqore.gtfs.schedule.spatial;

public interface TwoDimensionalCoordinates {

    /**
     * Primary coordinate can be e.g. latitude or X coordinate.
     * @return the primary coordinate of the geographical coordinates
     */
    double getPrimaryCoordinate();

    /**
     * Secondary coordinate can be e.g. longitude or Y coordinate.
     * @return the secondary coordinate of the geographical coordinates
     */
    double getSecondaryCoordinate();
    double distanceTo(TwoDimensionalCoordinates other);
}
