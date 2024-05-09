package ch.naviqore.gtfs.schedule.spatial;

public interface HasCoordinate extends GeographicalCoordinates {
    Coordinate getCoordinate();
}
