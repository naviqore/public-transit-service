package ch.naviqore.raptor.router;

import ch.naviqore.raptor.Leg;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.Objects;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@Getter
@ToString
class RaptorLeg implements Leg {

    private final String routeId;
    private final @Nullable String tripId;
    private final String fromStopId;
    private final String toStopId;
    private final LocalDateTime departureTime;
    private final LocalDateTime arrivalTime;
    private final Type type;

    @Override
    public int compareTo(@NotNull Leg other) {
        // sort legs first by departure time than by arrival time since there some legs that actually have the same
        // departure and arrival time (really short distance local service) and therefore the following leg may
        // have the same departure time but a later arrival time
        int comparison = getDepartureTime().compareTo(other.getDepartureTime());
        if (comparison != 0) {
            return comparison;
        } else {
            return getArrivalTime().compareTo(other.getArrivalTime());
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
                that.getToStopId()) && this.getArrivalTime() == that.getArrivalTime() && this.getDepartureTime() == that.getDepartureTime() && Objects.equals(
                this.type, that.getType());
    }

    @Override
    public int hashCode() {
        return Objects.hash(routeId, tripId, fromStopId, toStopId, departureTime, arrivalTime, type);
    }

}
