package org.naviqore.utils.spatial;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.jetbrains.annotations.NotNull;

public record GeoCoordinate(double latitude, double longitude) implements Coordinate, Comparable<GeoCoordinate> {

    private static final int EARTH_RADIUS = 6371000;

    public GeoCoordinate {
        validateCoordinate(latitude, longitude);
    }

    private static void validateCoordinate(double latitude, double longitude) {
        if (latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("Latitude must be between -90 and 90 degrees");
        }
        if (longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("Longitude must be between -180 and 180 degrees");
        }
        if (Double.isNaN(latitude) || Double.isNaN(longitude)) {
            throw new IllegalArgumentException("Coordinates cannot be NaN");
        }
    }

    private void isOfSameType(Coordinate other) {
        if (other == null) {
            throw new IllegalArgumentException("Other coordinate must not be null");
        }
        if (other.getClass() != this.getClass()) {
            throw new IllegalArgumentException("Other coordinate must be of type " + this.getClass().getSimpleName());
        }
    }

    @JsonIgnore
    @Override
    public double getFirstComponent() {
        return latitude;
    }

    @JsonIgnore
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
    public double distanceTo(Coordinate other) {
        isOfSameType(other);
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
    public int compareTo(GeoCoordinate other) {
        double epsilon = 1e-5;

        double diffLatitude = this.latitude - other.latitude();
        if (Math.abs(diffLatitude) > epsilon) {
            return diffLatitude > 0 ? 1 : -1;
        }

        double diffLongitude = this.longitude - other.longitude();
        if (Math.abs(diffLongitude) > epsilon) {
            return diffLongitude > 0 ? 1 : -1;
        }

        return 0;
    }

    @Override
    public @NotNull String toString() {
        return this.getClass().getSimpleName() + "(lat=" + latitude + "°, lon=" + longitude + "°)";
    }
}
