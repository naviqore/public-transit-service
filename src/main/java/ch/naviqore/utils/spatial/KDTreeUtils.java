package ch.naviqore.utils.spatial;

/**
 * Utility class for the KDTree.
 * <p> This class contains utility methods for the KDTree. </p>
 */
public class KDTreeUtils {

    public static CoordinateComponentType getAxis(int depth) {
        return depth % KDTree.K_DIMENSIONS == 0 ? CoordinateComponentType.FIRST : CoordinateComponentType.SECOND;
    }

    public static <T extends Location<?>> KDNode<T> getNextNodeBasedOnAxisDirection(KDNode<T> node, double firstComponent, double secondComponent,
                                                                                    CoordinateComponentType axis) {
        TwoDimensionalCoordinate nodeCoordinate = node.getLocation().getCoordinate();

        return axis.getCoordinateComponent(firstComponent, secondComponent) < axis.getCoordinateComponent(nodeCoordinate) ? node.getLeft() : node.getRight();
    }

    public static <T extends Location<?>> boolean isDistanceGreaterThanCoordinateDifference(KDNode<T> node, double firstComponent, double secondComponent,
                                                                                            CoordinateComponentType axis) {
        TwoDimensionalCoordinate nodeCoordinate = node.getLocation().getCoordinate();

        double distance = nodeCoordinate.distanceTo(firstComponent, secondComponent);
        double coordinateDifference = Math.abs(
                axis.getCoordinateComponent(firstComponent, secondComponent) - axis.getCoordinateComponent(nodeCoordinate));

        return distance > coordinateDifference;
    }
}
