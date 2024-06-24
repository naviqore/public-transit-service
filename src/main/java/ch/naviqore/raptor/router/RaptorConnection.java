package ch.naviqore.raptor.router;

import ch.naviqore.raptor.Connection;
import ch.naviqore.raptor.Leg;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@NoArgsConstructor(access = AccessLevel.PACKAGE)
@Getter
@ToString
class RaptorConnection implements Connection, Comparable<Connection> {

    private List<Leg> legs = new ArrayList<>();

    private static void validateLegOrder(Leg current, Leg next) {
        if (!current.getToStopId().equals(next.getFromStopId())) {
            throw new IllegalStateException("Legs are not connected: " + current + " -> " + next);
        }
        if (current.getArrivalTime() < current.getDepartureTime()) {
            throw new IllegalStateException("Arrival time must be after departure time: " + current);
        }
        if (current.getArrivalTime() > next.getDepartureTime()) {
            throw new IllegalStateException(
                    "Arrival time must be before next departure time: " + current + " -> " + next);
        }
    }

    void addLeg(Leg leg) {
        this.legs.add(leg);
    }

    void initialize() {
        // sort legs by departure time
        Collections.sort(legs);
        // make sure that the legs are connected and times are consistent
        for (int i = 0; i < legs.size() - 1; i++) {
            Leg current = legs.get(i);
            Leg next = legs.get(i + 1);
            validateLegOrder(current, next);
        }
        // make legs immutable and remove unnecessary allocated memory
        this.legs = List.copyOf(legs);
    }

    @Override
    public int compareTo(@NotNull Connection other) {
        return Integer.compare(this.getArrivalTime(), other.getArrivalTime());
    }

    @Override
    public int getDepartureTime() {
        return legs.getFirst().getDepartureTime();
    }

    @Override
    public int getArrivalTime() {
        return legs.getLast().getArrivalTime();
    }

    @Override
    public String getFromStopId() {
        return legs.getFirst().getFromStopId();
    }

    @Override
    public String getToStopId() {
        return legs.getLast().getToStopId();
    }

    @Override
    public int getDuration() {
        return getArrivalTime() - getDepartureTime();
    }

    @Override
    public List<Leg> getWalkTransfers() {
        return legs.stream().filter(l -> l.getType() == Leg.Type.WALK_TRANSFER).toList();
    }

    @Override
    public List<Leg> getRouteLegs() {
        return legs.stream().filter(l -> l.getType() == Leg.Type.ROUTE).toList();
    }

    @Override
    public int getNumberOfSameStopTransfers() {
        int transferCounter = 0;
        for (int i = 0; i < legs.size() - 1; i++) {
            Leg current = legs.get(i);
            Leg next = legs.get(i + 1);
            if (current.getType() == Leg.Type.ROUTE && next.getType() == Leg.Type.ROUTE) {
                transferCounter++;
            }
        }
        return transferCounter;
    }

    @Override
    public int getNumberOfTotalTransfers() {
        return getWalkTransfers().size() + getNumberOfSameStopTransfers();
    }

}
