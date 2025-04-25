package org.naviqore.raptor.router;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.naviqore.raptor.Connection;
import org.naviqore.raptor.Leg;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@NoArgsConstructor(access = AccessLevel.PACKAGE)
@Getter
@ToString
class RaptorConnection implements Connection {

    private List<Leg> legs = new ArrayList<>();

    private static void validateLegOrder(Leg current, Leg next) {
        if (!current.getToStopId().equals(next.getFromStopId())) {
            throw new IllegalStateException("Legs are not connected: " + current + " -> " + next);
        }
        if (current.getArrivalTime().isBefore(current.getDepartureTime())) {
            throw new IllegalStateException("Arrival time must be after departure time: " + current);
        }
        if (current.getArrivalTime().isAfter(next.getDepartureTime())) {
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
    public LocalDateTime getDepartureTime() {
        return legs.getFirst().getDepartureTime();
    }

    @Override
    public LocalDateTime getArrivalTime() {
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
    public int getDurationInSeconds() {
        return (int) Duration.between(getDepartureTime(), getArrivalTime()).getSeconds();
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
