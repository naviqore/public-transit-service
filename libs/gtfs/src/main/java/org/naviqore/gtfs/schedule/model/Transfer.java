package org.naviqore.gtfs.schedule.model;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.naviqore.timetable.type.TransferType;

import java.util.Objects;
import java.util.Optional;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@Getter
public class Transfer implements org.naviqore.timetable.model.Transfer {

    private final Stop fromStop;
    private final Stop toStop;
    @Nullable
    @Getter(AccessLevel.NONE)
    private final Route fromRoute;
    @Nullable
    @Getter(AccessLevel.NONE)
    private final Route toRoute;
    @Nullable
    @Getter(AccessLevel.NONE)
    private final Trip fromTrip;
    @Nullable
    @Getter(AccessLevel.NONE)
    private final Trip toTrip;
    private final TransferType transferType;
    @Nullable
    private final Integer minTransferTime;

    @Override
    public Optional<Route> getFromRoute() {
        return Optional.ofNullable(fromRoute);
    }

    @Override
    public Optional<Route> getToRoute() {
        return Optional.ofNullable(toRoute);
    }

    @Override
    public Optional<Trip> getFromTrip() {
        return Optional.ofNullable(fromTrip);
    }

    @Override
    public Optional<Trip> getToTrip() {
        return Optional.ofNullable(toTrip);
    }

    @Override
    public Optional<Integer> getMinTransferTime() {
        return Optional.ofNullable(minTransferTime);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Transfer that = (Transfer) obj;
        return Objects.equals(fromStop, that.fromStop) && Objects.equals(toStop, that.toStop) && Objects.equals(
                fromRoute, that.fromRoute) && Objects.equals(toRoute, that.toRoute) && Objects.equals(fromTrip,
                that.fromTrip) && Objects.equals(toTrip, that.toTrip) && Objects.equals(transferType,
                that.transferType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fromStop, toStop, fromRoute, toRoute, fromTrip, toTrip, transferType);
    }

    @Override
    public String toString() {
        return "Transfer[" + "fromStopId='" + fromStop.getId() + '\'' + ", toStopId='" + toStop.getId() + '\'' + (fromRoute == null ? "" : ", fromRouteId='" + fromRoute.getId() + '\'') + (toRoute == null ? "" : ", toRouteId='" + toRoute.getId() + '\'') + (fromTrip == null ? "" : ", fromTripId='" + fromTrip.getId() + '\'') + (toTrip == null ? "" : ", toTripId='" + toTrip.getId() + '\'') + ", transferType=" + transferType + ']';
    }
}
