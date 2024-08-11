package ch.naviqore.gtfs.schedule.model;

import ch.naviqore.gtfs.schedule.type.StopAccessibilityType;
import ch.naviqore.utils.spatial.GeoCoordinate;
import ch.naviqore.utils.spatial.Location;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@Getter
public final class Stop implements Initializable, Location<GeoCoordinate> {

    private final String id;
    private final String name;
    private final GeoCoordinate coordinate;
    @Getter(AccessLevel.NONE)
    private final StopAccessibilityType wheelchairBoarding;
    @Nullable
    @Setter(AccessLevel.PACKAGE)
    @Getter(AccessLevel.NONE)
    private Stop parent;
    @Setter(AccessLevel.PACKAGE)
    private List<Stop> children = new ArrayList<>();
    private List<StopTime> stopTimes = new ArrayList<>();
    private List<Transfer> transfers = new ArrayList<>();

    void addStopTime(StopTime stopTime) {
        stopTimes.add(stopTime);
    }

    void addTransfer(Transfer transfer) {
        transfers.add(transfer);
    }

    public Optional<Stop> getParent() {
        return Optional.ofNullable(parent);
    }

    public StopAccessibilityType getWheelchairBoarding() {
        /* According to the GTFS specification, a child stop with unknown wheelchair boarding information should inherit
         * the parent stop's wheelchair boarding information. https://gtfs.org/schedule/reference/#stopstxt */
        if (wheelchairBoarding == StopAccessibilityType.UNKNOWN && parent != null) {
            return parent.getWheelchairBoarding();
        }
        return wheelchairBoarding;
    }

    @Override
    public void initialize() {
        Collections.sort(stopTimes);
        children = List.copyOf(children);
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
