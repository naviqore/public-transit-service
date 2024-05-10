package ch.naviqore.gtfs.schedule.spatial;

public class KDTree {

    private KDNode root;

    public void insert(HasCoordinate location) {
        int startDepth = 0;
        root = insert(root, location, startDepth);
    }

    private CoordinatesType getAxis(int depth) {
        final int kDimensions = 2;
        return depth % kDimensions == 0 ? CoordinatesType.LATITUDE : CoordinatesType.LONGITUDE;
    }

    private KDNode insert(KDNode node, HasCoordinate location, int depth) {
        if (node == null) {
            return new KDNode(location);
        }
        // draw axis alternately between x and y coordinates for each depth level of the tree
        // (i.e. for depth 0, 2, 4, ... compare x coordinates, for depth 1, 3, 5, ... compare y coordinates)
        var axis = getAxis(depth);
        if ((axis == CoordinatesType.LATITUDE ? location.getLatitude() : location.getLongitude())
                < (axis == CoordinatesType.LATITUDE ? node.getLocation().getLatitude() : node.getLocation().getLongitude())) {
            // build KDTree left side
            node.setLeft(insert(node.getLeft(), location, depth + 1));
        } else {
            // build KDTree right side
            node.setRight(insert(node.getRight(), location, depth + 1));
        }

        return node;
    }

    public HasCoordinate nearestNeighbour(HasCoordinate location) {
        return nearestNeighbour(root, location, 0).getLocation();
    }

    private KDNode nearestNeighbour(KDNode node, HasCoordinate location, int depth) {
        if (node == null) {
            return null;
        }

        var axis = getAxis(depth);
        KDNode next = KDTreeUtils.getNextNodeBasedOnAxisDirection(node, location, axis);
        // get the other side (node) of the tree
        KDNode other = next == node.getLeft() ? node.getRight() : node.getLeft();

        KDNode best = getNodeWithClosestDistance(node, nearestNeighbour(next, location, depth + 1), location);

        if (isDistanceGreaterThanCoordinateDifference(node, location, axis)) {
            best = getNodeWithClosestDistance(best, nearestNeighbour(other, location, depth + 1), location);
        }

        return best;
    }

    private static boolean isDistanceGreaterThanCoordinateDifference(KDNode node, HasCoordinate location, CoordinatesType axis) {
        return KDTreeUtils.distance(node.getLocation(), location) > Math.abs(
                (axis == CoordinatesType.LATITUDE ? location.getLatitude() : location.getLongitude()) - (axis == CoordinatesType.LATITUDE ? node.getLocation()
                        .getLatitude() : node.getLocation().getLongitude()));
    }

    private KDNode getNodeWithClosestDistance(KDNode node1, KDNode node2, HasCoordinate location) {
        if (node1 == null) {
            return node2;
        }
        if (node2 == null) {
            return node1;
        }
        double dist1 = KDTreeUtils.distance(node1.getLocation(), location);
        double dist2 = KDTreeUtils.distance(node2.getLocation(), location);
        return dist1 < dist2 ? node1 : node2;
    }
}
