package ch.naviqore.gtfs.schedule.spatial;

public class KDTree<T extends Location<U>, U extends TwoDimensionalCoordinates>{

    private KDNode<T> root;

    public void insert(T location) {
        int startDepth = 0;
        root = insert(root, location, startDepth);
    }

    private CoordinatesType getAxis(int depth) {
        final int kDimensions = 2;
        return depth % kDimensions == 0 ? CoordinatesType.PRIMARY : CoordinatesType.SECONDARY;
    }

    private KDNode<T> insert(KDNode<T> node, T location, int depth) {
        if (node == null) {
            return new KDNode<>(location);
        }
        // draw axis alternately between x and y coordinates for each depth level of the tree
        // (i.e. for depth 0, 2, 4, ... compare x coordinates, for depth 1, 3, 5, ... compare y coordinates)
        CoordinatesType axis = getAxis(depth);

        if ((axis.getCoordinateValue(location)) < axis.getCoordinateValue(node.getLocation())) {
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
        TwoDimensionalCoordinates nodeCoordinate = node.getLocation().getCoordinates();

        CoordinatesType axis = getAxis(depth);
        KDNode<T> next = KDTreeUtils.getNextNodeBasedOnAxisDirection(node, location, axis);
        // get the other side (node) of the tree
        KDNode<T> other = next == node.getLeft() ? node.getRight() : node.getLeft();
        KDNode<T> best = getNodeWithClosestDistance(node, nearestNeighbour(next, location, depth + 1), location.getCoordinates());

        if (KDTreeUtils.isDistanceGreaterThanCoordinateDifference(node, location, axis)) {
            best = getNodeWithClosestDistance(best, nearestNeighbour(other, location, depth + 1), location.getCoordinates());
        }

        return best;
    }


    private KDNode<T> getNodeWithClosestDistance(KDNode<T> node1, KDNode<T> node2, TwoDimensionalCoordinates location) {
        if (node1 == null) {
            return node2;
        }
        if (node2 == null) {
            return node1;
        }

        double dist1 = node1.getLocation().getCoordinates().distanceTo(location);
        double dist2 = node2.getLocation().getCoordinates().distanceTo(location);
        return dist1 < dist2 ? node1 : node2;
    }
}
