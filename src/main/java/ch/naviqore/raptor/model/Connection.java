package ch.naviqore.raptor.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A connection is a sequence of legs to travel from an origin stop to destination stop.
 */
@NoArgsConstructor
@Getter
@ToString
public class Connection implements Comparable<Connection> {

    private List<Leg> legs = new ArrayList<>();

    private static void validateLegOrder(Leg current, Leg next) {
        if (!current.toStopId.equals(next.fromStopId)) {
            throw new IllegalStateException("Legs are not connected: " + current + " -> " + next);
        }
        if (current.arrivalTime < current.departureTime) {
            throw new IllegalStateException("Arrival time must be after departure time: " + current);
        }
        if (current.arrivalTime > next.departureTime) {
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

    public int getDepartureTime() {
        return legs.getFirst().departureTime;
    }

    public int getArrivalTime() {
        return legs.getLast().arrivalTime;
    }

    public String getFromStopId() {
        return legs.getFirst().fromStopId;
    }

    public String getToStopId() {
        return legs.getLast().toStopId;
    }

    public int getDuration() {
        return getArrivalTime() - getDepartureTime();
    }

    public int getNumFootPathTransfers() {
        return (int) legs.stream().filter(l -> l.type == LegType.FOOTPATH).count();
    }

    public int getNumSameStationTransfers() {
        return getNumTransfers() - getNumFootPathTransfers();
    }

    public int getNumTransfers() {
        if (legs.isEmpty()) {
            return 0;
        }
        return getNumRouteLegs() - 1;
    }

    public int getNumRouteLegs() {
        return (int) legs.stream().filter(l -> l.type == LegType.ROUTE).count();
    }

    /**
     * Types of legs in a connection.
     */
    public enum LegType {
        FOOTPATH,
        ROUTE
    }

    /**
     * A leg is a part of a connection that is travelled on the same route and transport mode, without a transfer.
     */
    public record Leg(String routeId, String fromStopId, String toStopId, int departureTime, int arrivalTime,
                      LegType type) implements Comparable<Leg> {

        @Override
        public int compareTo(@NotNull Connection.Leg other) {
            return Integer.compare(this.departureTime, other.departureTime);
        }

    }

}
