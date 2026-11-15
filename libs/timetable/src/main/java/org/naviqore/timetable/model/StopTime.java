package org.naviqore.timetable.model;

import org.naviqore.timetable.type.PickupDropOffType;
import org.naviqore.timetable.type.ServiceDayTime;

/**
 * The arrival and departure of a trip at a stop, as times of the service day of the trip, and whether passengers can
 * board and alight there.
 */
public interface StopTime {

    Stop stop();

    Trip trip();

    ServiceDayTime arrival();

    ServiceDayTime departure();

    /**
     * Retrieves whether passengers can board the trip at the stop.
     *
     * @return the pickup type
     */
    PickupDropOffType pickupType();

    /**
     * Retrieves whether passengers can alight from the trip at the stop.
     *
     * @return the drop-off type
     */
    PickupDropOffType dropOffType();

}
