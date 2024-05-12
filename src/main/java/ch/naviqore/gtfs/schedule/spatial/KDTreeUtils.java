package ch.naviqore.gtfs.schedule.spatial;

/**
 * Utility class for the KDTree.
 * <p> This class contains utility methods for the KDTree. </p>
 */
public class KDTreeUtils {

    public static <T extends Location<?>> KDNode<T> getNextNodeBasedOnAxisDirection(KDNode<T> node,
                                                                                                  T location,
                                                                                                  CoordinatesType axis) {
        TwoDimensionalCoordinates nodeCoordinate = node.getLocation().getCoordinates();

        return (axis.getCoordinateValue(location)) < axis.getCoordinateValue(nodeCoordinate)
                ? node.getLeft()
                : node.getRight();
    }

    public static <T extends Location<?>> boolean isDistanceGreaterThanCoordinateDifference(
            KDNode<T> node, T location, CoordinatesType axis) {
        TwoDimensionalCoordinates nodeCoordinate = node.getLocation().getCoordinates();

        double distance = nodeCoordinate.distanceTo(location.getCoordinates());
        double coordinateDifference = Math.abs(
                axis.getCoordinateValue(location) - axis.getCoordinateValue(nodeCoordinate));

        return distance > coordinateDifference;
    }
}
