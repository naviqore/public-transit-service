package ch.naviqore.utils.spatial;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.ArrayList;

@NoArgsConstructor(access = AccessLevel.PACKAGE)
public class KDTree<T extends Location<?>> {

    static final int K_DIMENSIONS = 2;
    KDNode<T> root;

    public void insert(T location) {
        if (location == null) {
            throw new IllegalArgumentException("Location cannot be null");
        }
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
        if (location == null) {
            throw new IllegalArgumentException("Location cannot be null");
        }
        return nearestNeighbour(location.getCoordinate());
    }

    public T nearestNeighbour(TwoDimensionalCoordinate coordinate) {
        if (coordinate == null) {
            throw new IllegalArgumentException("Coordinate cannot be null");
        }
        return nearestNeighbour(coordinate.getFirstComponent(), coordinate.getSecondComponent());
    }

    public T nearestNeighbour(double firstComponent, double secondComponent) {
        if (root == null) {
            throw new IllegalStateException("Tree is empty");
        }
        return nearestNeighbour(root, new KDCoordinate(firstComponent, secondComponent), 0).getLocation();
    }

    private KDNode<T> nearestNeighbour(KDNode<T> node, KDCoordinate coordinate, int depth) {
        if (node == null) {
            return null;
        }

        CoordinateComponentType axis = KDTreeUtils.getAxis(depth);
        KDNode<T> next = KDTreeUtils.getNextNodeBasedOnAxisDirection(node, coordinate, axis);
        // get the other side (node) of the tree
        KDNode<T> other = next == node.getLeft() ? node.getRight() : node.getLeft();
        KDNode<T> best = getNodeWithClosestDistance(node, nearestNeighbour(next, coordinate, depth + 1), coordinate);

        if (KDTreeUtils.isDistanceGreaterThanCoordinateDifference(node, coordinate, axis)) {
            best = getNodeWithClosestDistance(best, nearestNeighbour(other, coordinate, depth + 1), coordinate);
        }

        return best;
    }

    private KDNode<T> getNodeWithClosestDistance(KDNode<T> node1, KDNode<T> node2, KDCoordinate coordinate) {
        if (node1 == null) {
            return node2;
        }
        if (node2 == null) {
            return node1;
        }

        double dist1 = node1.getLocation()
                .getCoordinate()
                .distanceTo(coordinate.firstComponent(), coordinate.secondComponent());
        double dist2 = node2.getLocation()
                .getCoordinate()
                .distanceTo(coordinate.firstComponent(), coordinate.secondComponent());
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
        rangeSearch(root, new KDCoordinate(firstComponent, secondComponent), radius, 0, result);
        return result;
    }

    private void rangeSearch(KDNode<T> node, KDCoordinate coordinate, double radius, int depth, ArrayList<T> result) {
        if (node == null) {
            return;
        }

        double distance = node.getLocation()
                .getCoordinate()
                .distanceTo(coordinate.firstComponent(), coordinate.secondComponent());

        // Check if the current node is within the range
        if (distance <= radius) {
            result.add(node.getLocation());
        }

        CoordinateComponentType axis = KDTreeUtils.getAxis(depth);

        double centerCoordinateOfInterest = axis.getCoordinateComponent(coordinate);
        double nodeCoordinateOfInterest = axis.getCoordinateComponent(node.getLocation());

        // Recursively search left subtree if necessary
        if (centerCoordinateOfInterest - radius < nodeCoordinateOfInterest) {
            rangeSearch(node.getLeft(), coordinate, radius, depth + 1, result);
        }

        // Recursively search right subtree if necessary
        if (centerCoordinateOfInterest + radius >= nodeCoordinateOfInterest) {
            rangeSearch(node.getRight(), coordinate, radius, depth + 1, result);
        }
    }

}
