package ch.naviqore.gtfs.schedule.model;

import ch.naviqore.utils.spatial.TwoDimensionalCoordinate;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@EqualsAndHashCode
@ToString
@Getter
public class Coordinate implements TwoDimensionalCoordinate, Comparable<Coordinate> {

    private static final int EARTH_RADIUS = 6371000;
    private final double latitude;
    private final double longitude;

    public Coordinate(double latitude, double longitude) {
        validateCoordinate(latitude, longitude);
        this.latitude = latitude;
        this.longitude = longitude;
    }

    private static void validateCoordinate(double latitude, double longitude) {
        if (latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("Latitude must be between -90 and 90 degrees");
        }
        if (longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("Longitude must be between -180 and 180 degrees");
        }
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
        if (other == null) {
            throw new IllegalArgumentException("Other coordinate cannot be null");
        }
        return distanceTo(other.getFirstComponent(), other.getSecondComponent());
    }

    @Override
    public double distanceTo(double firstComponent, double secondComponent) {
        validateCoordinate(firstComponent, secondComponent);

        double lat1 = Math.toRadians(this.latitude);
        double lat2 = Math.toRadians(firstComponent);
        double lon1 = Math.toRadians(this.longitude);
        double lon2 = Math.toRadians(secondComponent);

        double dLat = lat2 - lat1;
        double dLon = lon2 - lon1;

        double a = Math.pow(Math.sin(dLat / 2), 2) + Math.pow(Math.sin(dLon / 2), 2) * Math.cos(lat1) * Math.cos(lat2);
        double c = 2 * Math.asin(Math.sqrt(a));
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
