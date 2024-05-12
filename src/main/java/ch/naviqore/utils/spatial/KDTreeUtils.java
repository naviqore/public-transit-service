package ch.naviqore.utils.spatial;

/**
 * Utility class for the KDTree.
 * <p> This class contains utility methods for the KDTree. </p>
 */
public class KDTreeUtils {

    public static <T extends Location<?>> KDNode<T> getNextNodeBasedOnAxisDirection(KDNode<T> node,
                                                                                                  T location,
                                                                                                  CoordinateComponentType axis) {
        TwoDimensionalCoordinate nodeCoordinate = node.getLocation().getCoordinate();

        return (axis.getCoordinateComponent(location)) < axis.getCoordinateComponent(nodeCoordinate)
                ? node.getLeft()
                : node.getRight();
    }

    public static <T extends Location<?>> boolean isDistanceGreaterThanCoordinateDifference(
            KDNode<T> node, T location, CoordinateComponentType axis) {
        TwoDimensionalCoordinate nodeCoordinate = node.getLocation().getCoordinate();

        double distance = nodeCoordinate.distanceTo(location.getCoordinate());
        double coordinateDifference = Math.abs(
                axis.getCoordinateComponent(location) - axis.getCoordinateComponent(nodeCoordinate));

        return distance > coordinateDifference;
    }
}
