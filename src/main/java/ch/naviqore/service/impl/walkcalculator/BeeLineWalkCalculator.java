package ch.naviqore.service.impl.walkcalculator;

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
        if (walkSpeed <= 0){
            throw new IllegalArgumentException("walkSpeed needs to be greater than 0");
        }
        this.walkSpeed = walkSpeed;
    }


    @Override
    public Walk calculateWalk(GeoCoordinate from, GeoCoordinate to) {
        if( from == null || to == null ){
            throw new IllegalArgumentException("from and to cannot be null");
        }
        double distance = from.distanceTo(to);
        int duration = (int) (distance * walkSpeed / SECONDS_IN_HOUR);
        return new Walk(duration, (int) Math.round(distance));
    }
}
