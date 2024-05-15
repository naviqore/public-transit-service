package ch.naviqore.utils.spatial.index;

import ch.naviqore.utils.spatial.CartesianCoordinate;
import ch.naviqore.utils.spatial.Location;
import lombok.Getter;

@Getter
public class TestFacility implements Location<CartesianCoordinate> {
    private final String name;
    private final CartesianCoordinate coordinate;

    TestFacility(String name, CartesianCoordinate coordinate) {
        if (name == null || coordinate == null) {
            throw new IllegalArgumentException("Arguments cannot be null");
        }
        this.name = name;
        this.coordinate = coordinate;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (TestFacility) obj;
        return this.name.equals(that.name) && this.coordinate.equals(that.coordinate);
    }

    @Override
    public String toString() {
        return "[Facility: " + name + ", " + coordinate + "]";
    }

}

