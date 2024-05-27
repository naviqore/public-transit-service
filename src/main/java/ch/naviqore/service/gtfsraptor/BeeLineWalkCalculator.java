package ch.naviqore.service.gtfsraptor;

import ch.naviqore.utils.spatial.GeoCoordinate;

/**
 * Calculates the walk duration between two points.
 */
public class BeeLineWalkCalculator implements WalkCalculator {

    private static final int SECONDS_IN_HOUR = 3600;
    private final int walkSpeed;

    /**
     * Creates a new BeeLineWalkCalculator with the given walking speed.
     *
     * @param walkSpeed Walking speed in m/h.
     */
    public BeeLineWalkCalculator(int walkSpeed) {
        this.walkSpeed = walkSpeed;
    }


    @Override
    public Walk calculateWalk(GeoCoordinate from, GeoCoordinate to) {
        double distance = from.distanceTo(to);
        int duration = (int) (distance * walkSpeed / SECONDS_IN_HOUR);
        return new Walk(duration, (int) Math.round(distance));
    }
}
