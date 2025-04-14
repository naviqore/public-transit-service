package org.naviqore.utils.spatial;

public class CartesianCoordinate implements Coordinate {
    private final double x;
    private final double y;

    public CartesianCoordinate(double x, double y) {
        validateCoordinate(x, y);
        this.x = x;
        this.y = y;
    }

    private static void validateCoordinate(double x, double y) {
        if (Double.isNaN(x) || Double.isNaN(y)) {
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

    @Override
    public double getFirstComponent() {
        return x;
    }

    @Override
    public double getSecondComponent() {
        return y;
    }

    @Override
    public double distanceTo(Coordinate other) {
        isOfSameType(other);
        return distanceTo(other.getFirstComponent(), other.getSecondComponent());
    }

    @Override
    public double distanceTo(double x, double y) {
        validateCoordinate(x, y);
        return Math.sqrt(Math.pow(this.x - x, 2) + Math.pow(this.y - y, 2));
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (CartesianCoordinate) obj;
        return this.x == that.x && this.y == that.y;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "(x=" + x + ", y=" + y + ")";
    }

}
