package ch.naviqore.utils.spatial;

/**
 * Utility class for the KDTree.
 * <p> This class contains utility methods for the KDTree. </p>
 */
public class KDTreeUtils {

    public static CoordinateComponentType getAxis(int depth) {
        return depth % KDTree.K_DIMENSIONS == 0 ? CoordinateComponentType.FIRST : CoordinateComponentType.SECOND;
    }

    static <T extends Location<?>> KDNode<T> getNextNodeBasedOnAxisDirection(KDNode<T> node, KDCoordinate coordinate,
                                                                             CoordinateComponentType axis) {
        TwoDimensionalCoordinate nodeCoordinate = node.getLocation().getCoordinate();

        return axis.getCoordinateComponent(coordinate) < axis.getCoordinateComponent(
                nodeCoordinate) ? node.getLeft() : node.getRight();
    }

    static <T extends Location<?>> boolean isDistanceGreaterThanCoordinateDifference(KDNode<T> node,
                                                                                     KDCoordinate coordinate,
                                                                                     CoordinateComponentType axis) {
        TwoDimensionalCoordinate nodeCoordinate = node.getLocation().getCoordinate();

        double distance = nodeCoordinate.distanceTo(coordinate.firstComponent(), coordinate.secondComponent());
        double coordinateDifference = Math.abs(
                axis.getCoordinateComponent(coordinate) - axis.getCoordinateComponent(nodeCoordinate));

        return distance > coordinateDifference;
    }
}
