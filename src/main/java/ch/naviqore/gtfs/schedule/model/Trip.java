package ch.naviqore.gtfs.schedule.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@RequiredArgsConstructor
@Getter
public final class Trip implements Comparable<Trip> {
    private final String id;
    private final Route route;
    private final Calendar calendar;
    private final List<StopTime> stopTimes = new ArrayList<>();

    void addStopTime(StopTime stopTime) {
        stopTimes.add(stopTime);
    }

    @Override
    public int compareTo(Trip o) {
        // TODO: Sort stopTimes, then return first stop.
        return 0;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (Trip) obj;
        return Objects.equals(this.id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Trip[" + "id=" + id + ", " + "route=" + route + ", " + "calendar=" + calendar + ']';
    }
}
