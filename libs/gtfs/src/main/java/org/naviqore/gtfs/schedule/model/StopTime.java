package org.naviqore.gtfs.schedule.model;

import org.naviqore.timetable.type.PickupDropOffType;
import org.naviqore.timetable.type.ServiceDayTime;

/**
 * A stop time of a trip.
 * <p>
 * The pickup and the drop-off type are held in one byte, two bits each, as a feed has tens of millions of stop times.
 *
 * @param pickupAndDropOff the pickup type in the two lowest bits, and the drop-off type in the two above.
 */
public record StopTime(Stop stop, Trip trip, ServiceDayTime arrival, ServiceDayTime departure,
                       byte pickupAndDropOff) implements Comparable<StopTime>, org.naviqore.timetable.model.StopTime {

    private static final PickupDropOffType[] PICKUP_DROP_OFF_TYPES = PickupDropOffType.values();

    public StopTime {
        if (arrival.compareTo(departure) > 0) {
            throw new IllegalArgumentException("Arrival time must be before departure time.");
        }
    }

    public StopTime(Stop stop, Trip trip, ServiceDayTime arrival, ServiceDayTime departure,
                    PickupDropOffType pickupType, PickupDropOffType dropOffType) {
        this(stop, trip, arrival, departure, encode(pickupType, dropOffType));
    }

    private static byte encode(PickupDropOffType pickupType, PickupDropOffType dropOffType) {
        return (byte) (pickupType.ordinal() | dropOffType.ordinal() << 2);
    }

    @Override
    public PickupDropOffType pickupType() {
        return PICKUP_DROP_OFF_TYPES[pickupAndDropOff & 3];
    }

    @Override
    public PickupDropOffType dropOffType() {
        return PICKUP_DROP_OFF_TYPES[pickupAndDropOff >> 2 & 3];
    }

    /**
     * StopTimes are compared based on departures.
     */
    @Override
    public int compareTo(StopTime o) {
        return this.departure.compareTo(o.departure);
    }
}
