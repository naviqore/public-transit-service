package org.naviqore.timetable.model;

import org.naviqore.timetable.type.ServiceDayTime;

/**
 * The arrival and departure of a trip at a stop, as times of the service day of the trip.
 */
public interface StopTime {

    Stop stop();

    Trip trip();

    ServiceDayTime arrival();

    ServiceDayTime departure();

}
