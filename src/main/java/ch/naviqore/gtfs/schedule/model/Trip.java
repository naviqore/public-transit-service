package ch.naviqore.gtfs.schedule.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@RequiredArgsConstructor
@Getter
public final class Trip implements Comparable<Trip>, Initializable {

    private final String id;
    private final Route route;
    private final Calendar calendar;
    private List<StopTime> stopTimes = new ArrayList<>();

    void addStopTime(StopTime stopTime) {
        stopTimes.add(stopTime);
    }

    @Override
    public void initialize() {
        Collections.sort(stopTimes);
        stopTimes = List.copyOf(stopTimes);
    }

    @Override
    public int compareTo(Trip o) {
        return this.stopTimes.getFirst().compareTo(o.stopTimes.getFirst());
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
