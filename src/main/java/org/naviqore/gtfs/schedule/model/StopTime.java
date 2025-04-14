package org.naviqore.gtfs.schedule.model;

import org.naviqore.gtfs.schedule.type.ServiceDayTime;

public record StopTime(Stop stop, Trip trip, ServiceDayTime arrival,
                       ServiceDayTime departure) implements Comparable<StopTime> {

    public StopTime {
        if (arrival.compareTo(departure) > 0) {
            throw new IllegalArgumentException("Arrival time must be before departure time.");
        }
    }

    /**
     * StopTimes are compared based on departures.
     */
    @Override
    public int compareTo(StopTime o) {
        return this.departure.compareTo(o.departure);
    }
}
