package org.naviqore.utils.spatial.index;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.naviqore.utils.spatial.CartesianCoordinate;
import org.naviqore.utils.spatial.Coordinate;
import org.naviqore.utils.spatial.GeoCoordinate;

/**
 * How near a location on the other side of a split can be to a point. The k-d tree splits at a coordinate on an axis:
 * the locations with a lesser component lie on one side, the others on the other. The other side is the one the point
 * is not on, and the tree searches it only if this lower bound of the distance from the point to its locations is
 * within the distance it searches.
 * <p>
 * The bound depends on the geometry of the coordinates, in the unit of their distance. For a coordinate type without
 * one here it is 0, and the tree searches both sides.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
class SplitDistance {

    /**
     * Calculates a lower bound of the distance from a point, specified by its components, to every location on the
     * other side of a split.
     *
     * @param split the coordinate the tree splits at
     * @param axis  the axis it splits on
     */
    static double toOtherSide(Coordinate split, Coordinate.Axis axis, double firstComponent, double secondComponent) {
        return switch (split) {
            // the nearest point across the line of the split lies straight across it
            case CartesianCoordinate _ -> Math.abs(
                    split.getComponent(axis) - (axis == Coordinate.Axis.FIRST ? firstComponent : secondComponent));
            case GeoCoordinate geo when axis == Coordinate.Axis.FIRST -> toParallel(geo, firstComponent);
            case GeoCoordinate geo -> toMeridians(geo, firstComponent, secondComponent);
            default -> 0;
        };
    }

    // along the meridian of the point to the parallel of the split
    private static double toParallel(GeoCoordinate split, double latitude) {
        return GeoCoordinate.EARTH_RADIUS * Math.toRadians(Math.abs(split.latitude() - latitude));
    }

    // the other side lies between the meridian of the split and the antimeridian, where the longitudes wrap around: the
    // distance to the nearer of the two, perpendicular to it, or to the nearer pole if both are more than 90° away
    private static double toMeridians(GeoCoordinate split, double latitude, double longitude) {
        double towardsMeridian = Math.abs(longitude - split.longitude());
        double towardsAntimeridian = longitude < split.longitude() ? longitude + 180 : 180 - longitude;
        double longitudeDifference = Math.min(Math.min(towardsMeridian, towardsAntimeridian), 90);

        return GeoCoordinate.EARTH_RADIUS * Math.asin(
                Math.cos(Math.toRadians(latitude)) * Math.sin(Math.toRadians(longitudeDifference)));
    }

}
