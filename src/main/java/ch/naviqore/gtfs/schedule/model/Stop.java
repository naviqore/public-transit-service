package ch.naviqore.gtfs.schedule.model;

import ch.naviqore.utils.spatial.GeoCoordinate;
import ch.naviqore.utils.spatial.Location;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@Getter
public final class Stop implements Initializable, Location<GeoCoordinate> {

    private final String id;
    private final String name;
    @Nullable
    private final String parentId;
    private final GeoCoordinate coordinate;
    private List<StopTime> stopTimes = new ArrayList<>();
    private List<Transfer> transfers = new ArrayList<>();

    void addStopTime(StopTime stopTime) {
        stopTimes.add(stopTime);
    }

    void addTransfer(Transfer transfer) {
        transfers.add(transfer);
    }

    @Override
    public void initialize() {
        Collections.sort(stopTimes);
        stopTimes = List.copyOf(stopTimes);
        transfers = List.copyOf(transfers);
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
