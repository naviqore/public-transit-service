package ch.naviqore.utils.spatial;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.ArrayList;

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
        return nearestNeighbour(location.getCoordinate());
    }

    public T nearestNeighbour(TwoDimensionalCoordinate coordinate) {
        return nearestNeighbour(coordinate.getFirstComponent(), coordinate.getSecondComponent());
    }

    public T nearestNeighbour(double firstComponent, double secondComponent) {
        return nearestNeighbour(root, firstComponent, secondComponent, 0).getLocation();
    }

    private KDNode<T> nearestNeighbour(KDNode<T> node, double firstComponent, double secondComponent, int depth) {
        if (node == null) {
            return null;
        }

        CoordinateComponentType axis = KDTreeUtils.getAxis(depth);
        KDNode<T> next = KDTreeUtils.getNextNodeBasedOnAxisDirection(node, firstComponent, secondComponent, axis);
        // get the other side (node) of the tree
        KDNode<T> other = next == node.getLeft() ? node.getRight() : node.getLeft();
        KDNode<T> best = getNodeWithClosestDistance(node,
                nearestNeighbour(next, firstComponent, secondComponent, depth + 1), firstComponent, secondComponent);

        if (KDTreeUtils.isDistanceGreaterThanCoordinateDifference(node, firstComponent, secondComponent, axis)) {
            best = getNodeWithClosestDistance(best, nearestNeighbour(other, firstComponent, secondComponent, depth + 1),
                    firstComponent, secondComponent);
        }

        return best;
    }

    private KDNode<T> getNodeWithClosestDistance(KDNode<T> node1, KDNode<T> node2, double firstComponent,
                                                 double secondComponent) {
        if (node1 == null) {
            return node2;
        }
        if (node2 == null) {
            return node1;
        }

        double dist1 = node1.getLocation().getCoordinate().distanceTo(firstComponent, secondComponent);
        double dist2 = node2.getLocation().getCoordinate().distanceTo(firstComponent, secondComponent);
        return dist1 < dist2 ? node1 : node2;
    }

    public ArrayList<T> rangeSearch(T center, double radius) {
        if (center == null) {
            throw new IllegalArgumentException("Center location cannot be null");
        }
        return rangeSearch(center.getCoordinate(), radius);
    }

    public ArrayList<T> rangeSearch(TwoDimensionalCoordinate center, double radius) {
        if (center == null) {
            throw new IllegalArgumentException("Center coordinate cannot be null");
        }
        return rangeSearch(center.getFirstComponent(), center.getSecondComponent(), radius);
    }

    public ArrayList<T> rangeSearch(double firstComponent, double secondComponent, double radius) {
        ArrayList<T> result = new ArrayList<>();
        rangeSearch(root, firstComponent, secondComponent, radius, 0, result);
        return result;
    }

    private void rangeSearch(KDNode<T> node, double firstComponent, double secondComponent, double radius, int depth,
                             ArrayList<T> result) {
        if (node == null) {
            return;
        }

        double distance = node.getLocation().getCoordinate().distanceTo(firstComponent, secondComponent);

        // Check if the current node is within the range
        if (distance <= radius) {
            result.add(node.getLocation());
        }

        CoordinateComponentType axis = KDTreeUtils.getAxis(depth);

        double centerCoordinateOfInterest = axis.getCoordinateComponent(firstComponent, secondComponent);
        double nodeCoordinateOfInterest = axis.getCoordinateComponent(node.getLocation());

        // Recursively search left subtree if necessary
        if (centerCoordinateOfInterest - radius < nodeCoordinateOfInterest) {
            rangeSearch(node.getLeft(), firstComponent, secondComponent, radius, depth + 1, result);
        }

        // Recursively search right subtree if necessary
        if (centerCoordinateOfInterest + radius >= nodeCoordinateOfInterest) {
            rangeSearch(node.getRight(), firstComponent, secondComponent, radius, depth + 1, result);
        }
    }

}
