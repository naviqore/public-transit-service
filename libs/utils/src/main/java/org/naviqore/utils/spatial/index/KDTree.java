package org.naviqore.utils.spatial.index;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.naviqore.utils.spatial.Coordinate;
import org.naviqore.utils.spatial.Location;

import java.util.ArrayList;

/**
 * A k-dimensional tree (k-d tree) for fast, efficient proximity searches. This implementation only supports
 * 2-dimensional spatial data.
 * <p>
 * A search skips the other side of a split when no location there can be near enough, by a lower bound of their
 * distance in the geometry of the coordinates.
 *
 * @param <T> The type of location stored in the tree.
 */
@NoArgsConstructor(access = AccessLevel.PACKAGE)
public class KDTree<T extends Location<?>> {

    private static final int K_DIMENSIONS = 2;
    Node<T> root;

    static Coordinate.Axis getAxis(int depth) {
        return depth % K_DIMENSIONS == 0 ? Coordinate.Axis.FIRST : Coordinate.Axis.SECOND;
    }

    private static <T extends Location<?>> Node<T> getNextNodeBasedOnAxisDirection(Node<T> node,
                                                                                   InternalCoordinate coordinate,
                                                                                   Coordinate.Axis axis) {
        Coordinate nodeCoordinate = node.location.getCoordinate();

        return coordinate.getComponent(axis) < nodeCoordinate.getComponent(axis) ? node.left : node.right;
    }

    private static <T extends Location<?>> double distanceToOtherSide(Node<T> node, InternalCoordinate coordinate,
                                                                      Coordinate.Axis axis) {
        return SplitDistance.toOtherSide(node.location.getCoordinate(), axis, coordinate.firstComponent(),
                coordinate.secondComponent());
    }

    void insert(T location) {
        if (location == null) {
            throw new IllegalArgumentException("Location cannot be null");
        }
        int startDepth = 0;
        root = insert(root, location, startDepth);
    }

    private Node<T> insert(Node<T> node, T location, int depth) {
        if (node == null) {
            return new Node<>(location);
        }
        // draw axis alternately between first and second component of the coordinates for each depth level of the tree
        // (i.e. for depth 0, 2, 4, ... compare first (x, lat) component, for depth 1, 3, 5, ... compare second (y,
        // lon) component)
        Coordinate.Axis axis = getAxis(depth);

        if ((location.getCoordinate().getComponent(axis)) < node.location.getCoordinate().getComponent(axis)) {
            // build KDTree left side
            node.left = insert(node.left, location, depth + 1);
        } else {
            // build KDTree right side
            node.right = insert(node.right, location, depth + 1);
        }

        return node;
    }

    /**
     * Finds the nearest neighbor to a given location.
     *
     * @param location The location to find the nearest neighbor for.
     * @return The nearest neighbor to the given location.
     */
    public T nearestNeighbor(T location) {
        if (location == null) {
            throw new IllegalArgumentException("Location cannot be null");
        }
        return nearestNeighbor(location.getCoordinate());
    }

    /**
     * Finds the nearest neighbor to a given coordinate.
     *
     * @param coordinate The coordinate to find the nearest neighbor for.
     * @return The nearest neighbor to the given coordinate.
     */
    public T nearestNeighbor(Coordinate coordinate) {
        if (coordinate == null) {
            throw new IllegalArgumentException("Coordinate cannot be null");
        }
        return nearestNeighbor(coordinate.getFirstComponent(), coordinate.getSecondComponent());
    }

    /**
     * Finds the nearest neighbor to a coordinate specified by its components.
     *
     * @param firstComponent  The first component of the coordinate.
     * @param secondComponent The second component of the coordinate.
     * @return The nearest neighbor to the coordinate specified by firstComponent and secondComponent.
     */
    public T nearestNeighbor(double firstComponent, double secondComponent) {
        if (root == null) {
            throw new IllegalStateException("Tree is empty");
        }
        Nearest<T> nearest = new Nearest<>();
        nearestNeighbor(root, new InternalCoordinate(firstComponent, secondComponent), 0, nearest);
        return nearest.location;
    }

    private void nearestNeighbor(Node<T> node, InternalCoordinate coordinate, int depth, Nearest<T> nearest) {
        if (node == null) {
            return;
        }

        double distance = node.location.getCoordinate()
                .distanceTo(coordinate.firstComponent(), coordinate.secondComponent());
        // of equally near locations, the one found last
        if (distance <= nearest.distance) {
            nearest.location = node.location;
            nearest.distance = distance;
        }

        Coordinate.Axis axis = getAxis(depth);
        Node<T> next = getNextNodeBasedOnAxisDirection(node, coordinate, axis);
        // get the other side (node) of the tree
        Node<T> other = next == node.left ? node.right : node.left;
        nearestNeighbor(next, coordinate, depth + 1, nearest);

        // the other side can only hold a location as near as the nearest found so far if it is no farther away
        if (distanceToOtherSide(node, coordinate, axis) <= nearest.distance) {
            nearestNeighbor(other, coordinate, depth + 1, nearest);
        }
    }

    /**
     * Performs a range search to find all locations within a certain radius of a center location.
     *
     * @param center The center location for the range search.
     * @param radius The radius within which to search.
     * @return A list of all locations within the radius of the center.
     */
    public ArrayList<T> rangeSearch(T center, double radius) {
        if (center == null) {
            throw new IllegalArgumentException("Center location cannot be null");
        }
        return rangeSearch(center.getCoordinate(), radius);
    }

    /**
     * Performs a range search to find all locations within a certain radius of a center coordinate.
     *
     * @param center The center coordinate for the range search.
     * @param radius The radius within which to search.
     * @return A list of all locations within the radius of the center.
     */
    public ArrayList<T> rangeSearch(Coordinate center, double radius) {
        if (center == null) {
            throw new IllegalArgumentException("Center coordinate cannot be null");
        }
        return rangeSearch(center.getFirstComponent(), center.getSecondComponent(), radius);
    }

    /**
     * Performs a range search to find all locations within a certain radius of a coordinate specified by its
     * components.
     *
     * @param firstComponent  The first component of the center coordinate.
     * @param secondComponent The second component of the center coordinate.
     * @param radius          The radius within which to search.
     * @return A list of all locations within the radius of the specified center coordinate.
     */
    public ArrayList<T> rangeSearch(double firstComponent, double secondComponent, double radius) {
        if (root == null) {
            throw new IllegalStateException("Tree is empty");
        }
        if (radius <= 0) {
            throw new IllegalArgumentException("Radius cannot be negative or zero");
        }
        ArrayList<T> result = new ArrayList<>();
        rangeSearch(root, new InternalCoordinate(firstComponent, secondComponent), radius, 0, result);
        return result;
    }

    private void rangeSearch(Node<T> node, InternalCoordinate coordinate, double radius, int depth,
                             ArrayList<T> result) {
        if (node == null) {
            return;
        }

        double distance = node.location.getCoordinate()
                .distanceTo(coordinate.firstComponent(), coordinate.secondComponent());

        // Check if the current node is within the range
        if (distance <= radius) {
            result.add(node.location);
        }

        Coordinate.Axis axis = getAxis(depth);
        // the side of the center can hold locations at any distance, the other side none nearer than its distance
        boolean centerOnLeft = coordinate.getComponent(axis) < node.location.getCoordinate().getComponent(axis);
        boolean otherSideInRange = distanceToOtherSide(node, coordinate, axis) <= radius;

        // Recursively search left subtree if necessary
        if (centerOnLeft || otherSideInRange) {
            rangeSearch(node.left, coordinate, radius, depth + 1, result);
        }

        // Recursively search right subtree if necessary
        if (!centerOnLeft || otherSideInRange) {
            rangeSearch(node.right, coordinate, radius, depth + 1, result);
        }
    }

    /**
     * Tree-internal node.
     */
    @Getter
    @RequiredArgsConstructor(access = AccessLevel.PACKAGE)
    static class Node<U> {
        private final U location;
        private Node<U> left;
        private Node<U> right;
    }

    /**
     * The nearest location found so far, and its distance.
     */
    private static class Nearest<U> {
        private U location;
        private double distance = Double.POSITIVE_INFINITY;
    }

    /**
     * Tree-internal 2D coordinate.
     */
    private record InternalCoordinate(double firstComponent, double secondComponent) {

        private double getComponent(Coordinate.Axis axis) {
            return axis == Coordinate.Axis.FIRST ? firstComponent : secondComponent;
        }
    }
}
