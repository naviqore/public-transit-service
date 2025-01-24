package ch.naviqore.service.walk;

import ch.naviqore.utils.spatial.GeoCoordinate;

/**
 * Approximates the walk duration between two points using a beeline distance factor to adjust the straight-line
 * distance.
 */
public class BeeLineWalkCalculator implements WalkCalculator {

    /**
     * Beeline distance factor used to account for the reality that walking routes are not straight lines. This factor
     * helps to provide a more accurate representation of walking distances by considering deviations such as obstacles,
     * detours, stairways, doors, and buildings that pedestrians encounter.
     * <p>
     * In transportation models, a beeline distance factor typically ranges from 1.3 to 1.5 to account for these
     * real-world inefficiencies in walking routes. The factor of 1.3 is often used to adjust the straight-line
     * (beeline) distance to a more realistic walking distance.
     * <p>
     * Source: <a
     * href="https://github.com/matsim-org/matsim-libs/blob/4f5bbf1bf38b35278746f629b9eb9641dbeda274/matsim/src/main/java/org/matsim/core/config/groups/RoutingConfigGroup.java#L71">...</a>
     */
    public static final double BEELINE_DISTANCE_FACTOR = 1.3;

    private final double walkingSpeed;

    /**
     * Creates a new BeeLineWalkCalculator with the given walking speed.
     *
     * @param walkingSpeed Walking speed in meters per second (m/s).
     */
    public BeeLineWalkCalculator(double walkingSpeed) {
        if (walkingSpeed <= 0) {
            throw new IllegalArgumentException("Walking speed needs to be greater than 0.");
        }
        this.walkingSpeed = walkingSpeed;
    }

    @Override
    public Walk calculateWalk(GeoCoordinate from, GeoCoordinate to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("The from and to coordinates cannot be null.");
        }

        double beelineDistance = from.distanceTo(to);
        double adjustedDistance = beelineDistance * BEELINE_DISTANCE_FACTOR;
        int duration = (int) Math.round(adjustedDistance / walkingSpeed);

        return new Walk(duration, (int) Math.round(adjustedDistance));
    }
}
