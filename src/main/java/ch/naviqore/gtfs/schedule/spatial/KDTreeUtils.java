package ch.naviqore.gtfs.schedule.spatial;

/**
 * Utility class for the KDTree.
 * <p> This class contains utility methods for the KDTree. </p>
 */
public class KDTreeUtils {

//    static class Metric {
//        static final double EARTH_RADIUS = 6_371;
//    }

    static KDNode getNextNodeBasedOnAxisDirection(KDNode node, HasCoordinate location, CoordinatesType axis) {
        return (axis == CoordinatesType.LATITUDE ? location.getLatitude() : location.getLongitude())
                < (axis == CoordinatesType.LATITUDE ? node.getLocation().getLatitude() : node.getLocation().getLongitude())
                ? node.getLeft()
                : node.getRight();
    }

//    static double squaredHalfSine(double val) {
//        return Math.pow(Math.sin(val / 2), 2);
//    }
//
//    /**
//     * Returns the node with the smallest distance to the target location. Uses the Haversine formula to calculate the
//     * distance. <a href="https://www.baeldung.com/java-find-distance-between-points#calculate-the-distance-using-the-haversine-formula">...</a>
//     *
//     * @param aLeft  The left node.
//     * @param aRight The right node.
//     * @return The distance in meters.
//     */
//    static double distance(HasCoordinate aLeft, HasCoordinate aRight) {
//        final var deltaLatitude = Math.toRadians((aRight.getLatitude() - aLeft.getLatitude()));
//        final var deltaLongitude = Math.toRadians((aRight.getLongitude() - aLeft.getLongitude()));
//
//        var a = squaredHalfSine(deltaLatitude) + (Math.cos(Math.toRadians(aLeft.getLatitude()))
//                * Math.cos(Math.toRadians(aRight.getLatitude()))
//                * squaredHalfSine(deltaLongitude));
//        var c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
//
//        return KDTreeUtils.Metric.EARTH_RADIUS * c;
//    }
}
