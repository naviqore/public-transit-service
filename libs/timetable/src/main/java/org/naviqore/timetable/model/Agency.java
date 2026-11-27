package org.naviqore.timetable.model;

import java.time.ZoneId;

/**
 * The agency operating routes; the stop times of its trips are given in its time zone.
 */
public interface Agency {

    String name();

    ZoneId timezone();

}
