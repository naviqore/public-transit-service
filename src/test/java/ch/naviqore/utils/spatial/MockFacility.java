package ch.naviqore.utils.spatial;

import lombok.Getter;

@Getter
public class MockFacility implements Location<MockCoordinate> {
    private final String name;
    private final MockCoordinate coordinate;

    MockFacility(String name, MockCoordinate coordinate) {
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
        var that = (MockFacility) obj;
        return this.name.equals(that.name) && this.coordinate.equals(that.coordinate);
    }

}

