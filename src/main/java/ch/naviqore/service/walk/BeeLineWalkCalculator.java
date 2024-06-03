package ch.naviqore.service.walk;

import ch.naviqore.utils.spatial.GeoCoordinate;

/**
 * Calculates the walk duration between two points.
 */
public class BeeLineWalkCalculator implements WalkCalculator {

    private static final int SECONDS_IN_HOUR = 3600;
    private final int walkingSpeed;

    /**
     * Creates a new BeeLineWalkCalculator with the given walking speed.
     * <p>
     * TODO: Meter per hour? Default unit mostly is meter per seconds...
     *
     * @param walkingSpeed Walking speed in m/h.
     */
    public BeeLineWalkCalculator(int walkingSpeed) {
        if (walkingSpeed <= 0) {
            throw new IllegalArgumentException("The walkSpeed needs to be greater than 0.");
        }
        this.walkingSpeed = walkingSpeed;
    }

    @Override
    public Walk calculateWalk(GeoCoordinate from, GeoCoordinate to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("From and to cannot be null.");
        }

        double distance = from.distanceTo(to);
        int duration = (int) (distance * walkingSpeed / SECONDS_IN_HOUR);

        return new Walk(duration, (int) Math.round(distance));
    }
}
