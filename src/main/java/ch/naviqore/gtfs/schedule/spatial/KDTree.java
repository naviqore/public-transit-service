package ch.naviqore.gtfs.schedule.spatial;

public class KDTree {

    private KDNode root;
    private final int kDimensions = 2;

    public void insert(Coordinate location) {
        root = insert(root, location, 0);
    }

    private KDNode insert(KDNode node, Coordinate location, int depth) {
        if (node == null) {
            return new KDNode(location);
        }

        int axis = depth % kDimensions;
        if ((axis == 0 ? location.getLatitude() : location.getLongitude()) < (axis == 0 ? node.getLocation().getLatitude() : node.getLocation().getLongitude())) {
            node.setLeft(insert(node.getLeft(), location, depth + 1));
        } else {
            node.setRight(insert(node.getRight(), location, depth + 1));
        }

        return node;
    }

    public Coordinate nearestNeighbour(Coordinate location) {
        return nearestNeighbour(root, location, 0).getLocation();
    }

    private KDNode nearestNeighbour(KDNode node, Coordinate location, int depth) {
        if (node == null) {
            return null;
        }

        int axis = depth % kDimensions;
        KDNode next = KDTreeUtils.getNextNodeBasedOnAxis(node, location, axis);
        KDNode other = next == node.getLeft() ? node.getRight() : node.getLeft();

        KDNode best = min(node, nearestNeighbour(next, location, depth + 1), location);

        if (isDistanceGreaterThanCoordinateDifference(node, location, axis)) {
            best = min(best, nearestNeighbour(other, location, depth + 1), location);
        }

        return best;
    }

    private static boolean isDistanceGreaterThanCoordinateDifference(KDNode node, Coordinate location, int axis) {
        return KDTreeUtils.distance(node.getLocation(), location)
                > Math.abs(
                (axis == 0
                        ? location.getLatitude() : location.getLongitude()) - (axis == 0 ? node.getLocation().getLatitude()
                        : node.getLocation().getLongitude()));
    }

    private static boolean isCurrentNodeCloserThanBest(KDNode node, Coordinate location, KDNode best) {
        return best == null
                || (best.getLocation() != null
                && KDTreeUtils.distance(node.getLocation(), location)
                < KDTreeUtils.distance(best.getLocation(), location));
    }


    private KDNode min(KDNode node1, KDNode node2, Coordinate location) {
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

    public static void main(String[] args) {
        System.out.println("Hello World");
        KDTree kdTree = new KDTree();

        kdTree.insert(new Coordinate(48.137154, 11.576124)); // Munich
        kdTree.insert(new Coordinate(52.520008, 13.404954)); // Berlin
        kdTree.insert(new Coordinate(50.110924, 8.682127));  // Frankfurt
        kdTree.insert(new Coordinate(47.3769, 8.5417)); // Zurich
        kdTree.insert(new Coordinate(47.42100820116168, 9.35977158264066)); // St. Gallen Militärkantine
        kdTree.insert(new Coordinate(47.41984757221546, 9.361976306041305)); // St. Gallen Sportanlage Kreuzbleiche

        //  Coordinate location = new Coordinate(47.4245, 9.3767); // St. Gallen
        var location = new Coordinate(47.4202611944959, 9.362182342510467); // St. Gallen Parkgarage Kreuzbleiche

        Coordinate nearestNeighbour = kdTree.nearestNeighbour(location);
        if (nearestNeighbour != null) {
            System.out.println("Nearest neighbour to " + location.getLongitude() + " " + location.getLatitude() + " is " + nearestNeighbour.getLongitude() + " " + nearestNeighbour.getLatitude());
        }
    }
}
