package ch.naviqore.raptor.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Getter
@NoArgsConstructor
@ToString
public class Connection {

    private final List<Leg> legs = new ArrayList<>();

    public Connection(List<Leg> legs) {
        this.addLegs(legs);
    }

    public void addLeg(String description, String fromStopId, String toStopId, int departureTime, int arrivalTime,
                       LegType type) {
        addLeg(new Leg(description, fromStopId, toStopId, departureTime, arrivalTime, type));
    }

    public void addLeg(Leg leg) {
        legs.add(leg);
        update();
    }

    public void addLegs(List<Leg> legs) {
        this.legs.addAll(legs);
        update();
    }

    public void update() {
        // sort legs by departure time
        legs.sort(Comparator.comparingInt(l -> l.departureTime));
        // make sure that the legs are connected and times are consistent
        for (int i = 0; i < legs.size() - 1; i++) {
            Leg current = legs.get(i);
            Leg next = legs.get(i + 1);
            if (!current.toStopId.equals(next.fromStopId)) {
                throw new IllegalArgumentException("Legs are not connected: " + current + " -> " + next);
            }
            if (current.arrivalTime < current.departureTime) {
                throw new IllegalArgumentException("Arrival time must be after departure time: " + current);
            }
            if (current.arrivalTime > next.departureTime) {
                throw new IllegalArgumentException(
                        "Arrival time must be before next departure time: " + current + " -> " + next);
            }
        }
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
        return (int) legs.stream().filter(l -> l.type == LegType.TRANSFER).count();
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



    public enum LegType {
        TRANSFER,
        ROUTE
    }

    public record Leg(String routeId, String fromStopId, String toStopId, int departureTime, int arrivalTime,
                      LegType type) {
    }

}
