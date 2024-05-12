package ch.naviqore.utils.spatial;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PACKAGE)
public class KDTree<T extends Location<?>> {

    static final int K_DIMENSIONS = 2;
    KDNode<T> root;

    void insert(T location) {
        int startDepth = 0;
        root = insert(root, location, startDepth);
    }

    private KDNode<T> insert(KDNode<T> node, T location, int depth) {
        if (node == null) {
            return new KDNode<>(location);
        }
        // draw axis alternately between first and second component of the coordinates for each depth level of the tree
        // (i.e. for depth 0, 2, 4, ... compare first (x, lat) component, for depth 1, 3, 5, ... compare second (y,
        // lon) component)
        CoordinateComponentType axis = KDTreeUtils.getAxis(depth);

        if ((axis.getCoordinateComponent(location)) < axis.getCoordinateComponent(node.getLocation())) {
            // build KDTree left side
            node.setLeft(insert(node.getLeft(), location, depth + 1));
        } else {
            // build KDTree right side
            node.setRight(insert(node.getRight(), location, depth + 1));
        }

        return node;
    }

    public T nearestNeighbour(T location) {
        return nearestNeighbour(root, location, 0).getLocation();
    }

    private KDNode<T> nearestNeighbour(KDNode<T> node, T location, int depth) {
        if (node == null) {
            return null;
        }

        CoordinateComponentType axis = KDTreeUtils.getAxis(depth);
        KDNode<T> next = KDTreeUtils.getNextNodeBasedOnAxisDirection(node, location, axis);
        // get the other side (node) of the tree
        KDNode<T> other = next == node.getLeft() ? node.getRight() : node.getLeft();
        KDNode<T> best = getNodeWithClosestDistance(node, nearestNeighbour(next, location, depth + 1), location);

        if (KDTreeUtils.isDistanceGreaterThanCoordinateDifference(node, location, axis)) {
            best = getNodeWithClosestDistance(best, nearestNeighbour(other, location, depth + 1), location);
        }

        return best;
    }

    private KDNode<T> getNodeWithClosestDistance(KDNode<T> node1, KDNode<T> node2, T location) {
        if (node1 == null) {
            return node2;
        }
        if (node2 == null) {
            return node1;
        }

        double dist1 = node1.getLocation().getCoordinate().distanceTo(location.getCoordinate());
        double dist2 = node2.getLocation().getCoordinate().distanceTo(location.getCoordinate());
        return dist1 < dist2 ? node1 : node2;
    }
}
