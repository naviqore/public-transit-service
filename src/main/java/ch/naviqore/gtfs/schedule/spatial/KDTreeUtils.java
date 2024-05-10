package ch.naviqore.gtfs.schedule.spatial;

/**
 * Utility class for the KDTree.
 * <p> This class contains utility methods for the KDTree. </p>
 */
public class KDTreeUtils {

    public static <T extends TwoDimensionalCoordinates> KDNode<T> getNextNodeBasedOnAxisDirection(KDNode<T> node,
                                                                                                  T location,
                                                                                                  CoordinatesType axis) {
        TwoDimensionalCoordinates nodeCoordinate = node.getLocation();

        return (axis == CoordinatesType.PRIMARY ? location.getPrimaryCoordinate()
                : location.getSecondaryCoordinate()) < (axis == CoordinatesType.PRIMARY
                ? nodeCoordinate.getPrimaryCoordinate()
                : nodeCoordinate.getSecondaryCoordinate())
                ? node.getLeft()
                : node.getRight();
    }

    public static <T extends TwoDimensionalCoordinates> boolean isDistanceGreaterThanCoordinateDifference(
            KDNode<T> node, T location, CoordinatesType axis) {
        TwoDimensionalCoordinates nodeCoordinate = node.getLocation();

        double distance = nodeCoordinate.distanceTo(location);
        double coordinateDifference = Math.abs(
                (axis == CoordinatesType.PRIMARY
                        ? location.getPrimaryCoordinate()
                        : location.getSecondaryCoordinate()) - (axis == CoordinatesType.PRIMARY
                        ? nodeCoordinate.getPrimaryCoordinate()
                        : nodeCoordinate.getSecondaryCoordinate()));

        return distance > coordinateDifference;
    }
}
