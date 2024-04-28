package ch.naviqore.gtfs.schedule.spatial;

public class KDTreeUtils {
    static KDNode getNextNodeBasedOnAxis(KDNode node, Coordinate location, int axis) {
        return (axis == 0 ? location.getLatitude() : location.getLongitude())
                < (axis == 0 ? node.getLocation().getLatitude() : node.getLocation().getLongitude()) ? node.getLeft() : node.getRight();
    }

    static double distance(Coordinate aLeft, Coordinate aRight) {
        double dx = aLeft.getLatitude() - aRight.getLatitude();
        double dy = aLeft.getLongitude() - aRight.getLongitude();
        return Math.sqrt(dx * dx + dy * dy);
    }
}
