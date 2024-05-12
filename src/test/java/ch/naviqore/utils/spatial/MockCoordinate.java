package ch.naviqore.utils.spatial;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class MockCoordinate implements TwoDimensionalCoordinate {
    private final double x;
    private final double y;

    @Override
    public double getFirstComponent() {
        return x;
    }

    @Override
    public double getSecondComponent() {
        return y;
    }

    @Override
    public double distanceTo(TwoDimensionalCoordinate other) {
        return Math.sqrt(Math.pow(x - other.getFirstComponent(), 2) + Math.pow(y - other.getSecondComponent(), 2));
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (MockCoordinate) obj;
        return this.x == that.x && this.y == that.y;
    }

}
