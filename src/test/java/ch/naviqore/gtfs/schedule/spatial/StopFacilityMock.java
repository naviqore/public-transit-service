package ch.naviqore.gtfs.schedule.spatial;

import ch.naviqore.gtfs.schedule.model.Coordinate;
import ch.naviqore.utils.spatial.Location;
import lombok.Getter;

@Getter
public class StopFacilityMock implements Location<Coordinate> {
    private final String id;
    private final String name;
    private final Coordinate coordinate;

    StopFacilityMock(String id, String name, Coordinate coordinate) {
        if (id == null || name == null || coordinate == null) {
            throw new IllegalArgumentException("Arguments cannot be null");
        }
        this.id = id;
        this.name = name;
        this.coordinate = coordinate;
    }

    @Override
    public Coordinate getCoordinate() {
        return coordinate;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (StopFacilityMock) obj;
        return this.id.equals(that.id)
                && this.name.equals(that.name)
                && this.coordinate.equals(that.coordinate);
    }
}

