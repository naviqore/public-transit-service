package ch.naviqore.raptor;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

    public List<Leg> getWalkTransfers() {
        return legs.stream().filter(l -> l.type == LegType.WALK_TRANSFER).toList();
    }

    public List<Leg> getRouteLegs() {
        return legs.stream().filter(l -> l.type == LegType.ROUTE).toList();
    }

    public int getNumberOfSameStopTransfers() {
        int transferCounter = 0;
        for (int i = 0; i < legs.size() - 1; i++) {
            Leg current = legs.get(i);
            Leg next = legs.get(i + 1);
            if (current.type == LegType.ROUTE && next.type == LegType.ROUTE) {
                transferCounter++;
            }
        }
        return transferCounter;
    }

    public int getNumberOfTotalTransfers() {
        return getWalkTransfers().size() + getNumberOfSameStopTransfers();
    }

    /**
     * Types of legs in a connection.
     */
    public enum LegType {
        WALK_TRANSFER,
        ROUTE
    }

    /**
     * A leg is a part of a connection that is travelled on the same route and transport mode, without a transfer.
     */
    public record Leg(String routeId, @Nullable String tripId, String fromStopId, String toStopId, int departureTime,
                      int arrivalTime, LegType type) implements Comparable<Leg> {

        @Override
        public int compareTo(@NotNull Connection.Leg other) {
            // sort legs first by departure time than by arrival time since there some legs that actually have the same
            // departure and arrival time (really short distance local service) and therefore the following leg may
            // have the same departure time but a later arrival time
            int comparison = Integer.compare(this.departureTime, other.departureTime);
            if (comparison != 0) {
                return comparison;
            } else {
                return Integer.compare(this.arrivalTime, other.arrivalTime);
            }

        }

    }

}
