package ch.naviqore.gtfs.schedule.model;

import ch.naviqore.gtfs.schedule.type.RouteType;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@Getter
public final class Route implements Initializable {

    private final String id;
    private final Agency agency;
    private final String shortName;
    private final String longName;
    private final RouteType type;
    private List<Trip> trips = new ArrayList<>();

    void addTrip(Trip trip) {
        trips.add(trip);
    }

    @Override
    public void initialize() {
        Collections.sort(trips);
        trips = List.copyOf(trips);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (Route) obj;
        return Objects.equals(this.id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Route[" + "id=" + id + ", " + "agency=" + agency + ", " + "shortName=" + shortName + ", " + "longName=" + longName + ", " + "type=" + type + ']';
    }

}
