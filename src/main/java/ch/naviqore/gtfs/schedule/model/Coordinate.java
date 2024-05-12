package ch.naviqore.gtfs.schedule.model;

import ch.naviqore.utils.spatial.TwoDimensionalCoordinate;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

@RequiredArgsConstructor
@EqualsAndHashCode
@ToString
@Getter
public class Coordinate implements TwoDimensionalCoordinate, Comparable<Coordinate> {

    private static final int EARTH_RADIUS = 6371000;
    private final double latitude;
    private final double longitude;

    // TODO  how do we create the relation to the `Stop` object in the GTFS model? backref, or stop id as a private member ?

    private double getLatitudeDistance(double otherLatitude) {
        return Math.toRadians(otherLatitude - this.getFirstComponent());
    }

    private double getLongitudeDistance(double otherLongitude) {
        return Math.toRadians(otherLongitude - this.getSecondComponent());
    }

    private double calculateHaversineFormulaComponent(double latDistance, double lonDistance) {
        return Math.sin(latDistance / 2) * Math.sin(latDistance / 2) + Math.cos(
                Math.toRadians(this.getFirstComponent())) * Math.cos(Math.toRadians(this.getSecondComponent())) * Math.sin(
                lonDistance / 2) * Math.sin(lonDistance / 2);
    }

    private double calculateHaversineDistance(double a) {
        return 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    @Override
    public double getFirstComponent() {
        return latitude;
    }

    @Override
    public double getSecondComponent() {
        return longitude;
    }

    /**
     * Calculates the distance to another Coordinates object using the Haversine formula.
     *
     * @param other The other Coordinates object to calculate the distance to.
     * @return The distance in meters.
     */
    @Override
    public double distanceTo(TwoDimensionalCoordinate other) {
        // TODO: Do we need to handle the case where the other object is null? If so, use Optional instead of Exception
        double latDistance = getLatitudeDistance(other.getFirstComponent());
        double lonDistance = getLongitudeDistance(other.getSecondComponent());
        double a = calculateHaversineFormulaComponent(latDistance, lonDistance);
        double c = calculateHaversineDistance(a);
        return EARTH_RADIUS * c;
    }

    @Override
    public int compareTo(Coordinate other) {
        double epsilon = 1e-5;

        double diffLatitude = this.latitude - other.getLatitude();
        if (Math.abs(diffLatitude) > epsilon) {
            return diffLatitude > 0 ? 1 : -1;
        }

        double diffLongitude = this.longitude - other.getLongitude();
        if (Math.abs(diffLongitude) > epsilon) {
            return diffLongitude > 0 ? 1 : -1;
        }

        return 0;
    }
}
