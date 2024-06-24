package ch.naviqore.raptor.impl;

import ch.naviqore.raptor.Leg;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@Getter
@ToString
class RaptorLeg implements Leg {

    private final String routeId;
    private final @Nullable String tripId;
    private final String fromStopId;
    private final String toStopId;
    private final int departureTime;
    private final int arrivalTime;
    private final Type type;

    @Override
    public int compareTo(@NotNull Leg other) {
        // sort legs first by departure time than by arrival time since there some legs that actually have the same
        // departure and arrival time (really short distance local service) and therefore the following leg may
        // have the same departure time but a later arrival time
        int comparison = Integer.compare(this.departureTime, other.getDepartureTime());
        if (comparison != 0) {
            return comparison;
        } else {
            return Integer.compare(this.arrivalTime, other.getArrivalTime());
        }

    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (Leg) obj;
        return Objects.equals(this.routeId, that.getRouteId()) && Objects.equals(this.tripId,
                that.getTripId()) && Objects.equals(this.fromStopId, that.getFromStopId()) && Objects.equals(
                this.toStopId,
                that.getToStopId()) && this.departureTime == that.getDepartureTime() && this.arrivalTime == that.getArrivalTime() && Objects.equals(
                this.type, that.getType());
    }

    @Override
    public int hashCode() {
        return Objects.hash(routeId, tripId, fromStopId, toStopId, departureTime, arrivalTime, type);
    }

}
