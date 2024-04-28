package ch.naviqore.gtfs.schedule.spatial;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

@RequiredArgsConstructor
@EqualsAndHashCode
@ToString
@Getter
public class Coordinate {

    private static final int EARTH_RADIUS = 6371000;
    private final double latitude;
    private final double longitude;

    /**
     * Calculates the distance to another Coordinates object using the Haversine formula.
     *
     * @param other The other Coordinates object to calculate the distance to.
     * @return The distance in meters.
     */
    public double distanceTo(Coordinate other) {
        double latDistance = Math.toRadians(other.latitude - this.latitude);
        double lonDistance = Math.toRadians(other.longitude - this.longitude);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2) + Math.cos(
                Math.toRadians(this.latitude)) * Math.cos(Math.toRadians(other.latitude)) * Math.sin(
                lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS * c;
    }
}
