package org.naviqore.timetable.model;

import java.time.LocalDate;

/**
 * The service days of the trips referring to it.
 */
public interface Calendar {

    String getId();

    /**
     * Determines if the service is operational on a specific day.
     *
     * @param date the date to check for service availability
     * @return true if the service is operational on the given date, false otherwise
     */
    boolean isServiceAvailable(LocalDate date);

}
