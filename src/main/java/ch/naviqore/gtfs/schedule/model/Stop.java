package ch.naviqore.gtfs.schedule.model;

import ch.naviqore.gtfs.schedule.spatial.Coordinate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@Getter
public final class Stop implements Initializable {
    private final String id;
    private final String name;
    private final Coordinate coordinate;
    private final List<StopTime> stopTimes = new ArrayList<>();
    private final List<Transfer> transfers = new ArrayList<>();

    void addStopTime(StopTime stopTime) {
        stopTimes.add(stopTime);
    }

    void addTransfer(Transfer transfer) {
        transfers.add(transfer);
    }

    @Override
    public void initialize() {
        Collections.sort(stopTimes);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (Stop) obj;
        return Objects.equals(this.id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Stop[" + "id=" + id + ", " + "name=" + name + ']';
    }
}
