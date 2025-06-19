package org.naviqore.gtfs.schedule.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.naviqore.gtfs.schedule.type.ServiceDayTime;

@Getter
@ToString
@EqualsAndHashCode
public final class StopTime implements Comparable<StopTime> {
    private final Stop stop;
    private final Trip trip;
    private final int arrival;
    private final int departure;

    public StopTime(Stop stop, Trip trip, int arrival, int departure) {
        if (arrival < 0) {
            throw new IllegalArgumentException("Arrival time cannot be negative.");
        }
        if (departure < 0) {
            throw new IllegalArgumentException("Departure time cannot be negative.");
        }
        if (departure < arrival) {
            throw new IllegalArgumentException("Arrival time must be before departure time.");
        }
        this.stop = stop;
        this.trip = trip;
        this.arrival = arrival;
        this.departure = departure;
    }

    /**
     * StopTimes are compared based on departures.
     */
    @Override
    public int compareTo(StopTime o) {
        return Integer.compare(this.departure, o.departure);
    }

    public ServiceDayTime getArrival() {
        return new ServiceDayTime(arrival);
    }

    public ServiceDayTime getDeparture() {
        return new ServiceDayTime(departure);
    }
}
