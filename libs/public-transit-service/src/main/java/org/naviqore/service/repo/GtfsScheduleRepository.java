package org.naviqore.service.repo;

import org.naviqore.gtfs.schedule.model.GtfsSchedule;

import java.io.IOException;

/**
 * GtfsScheduleRepository interface provides a contract for retrieving GTFS schedule data. Implementations of this
 * interface can source data from files, URLs, or databases.
 */
public interface GtfsScheduleRepository {

    /**
     * Retrieves the GTFS schedule data.
     *
     * @return a GtfsSchedule object containing the schedule data.
     */
    GtfsSchedule get() throws IOException, InterruptedException;

}
