package org.naviqore.timetable.model;

import org.naviqore.timetable.type.AccessibilityInformation;
import org.naviqore.timetable.type.BikeInformation;

import java.util.List;

/**
 * A trip of a route, running on the service days of its calendar.
 */
public interface Trip {

    String getId();

    Route getRoute();

    Calendar getCalendar();

    String getHeadSign();

    AccessibilityInformation getWheelchairAccessible();

    BikeInformation getBikesAllowed();

    /**
     * Retrieves the stop times of the trip.
     *
     * @return the stop times, in stop sequence order
     */
    List<? extends StopTime> getStopTimes();

}
