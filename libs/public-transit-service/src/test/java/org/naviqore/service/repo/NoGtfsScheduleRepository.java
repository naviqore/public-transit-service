package org.naviqore.service.repo;

import org.naviqore.gtfs.schedule.model.GtfsSchedule;

import java.io.IOException;

/**
 * A fallback implementation of the GtfsScheduleRepository interface, used when no actual repository is configured. This
 * implementation throws an exception indicating the absence of a repository.
 */
public class NoGtfsScheduleRepository implements GtfsScheduleRepository {

    @Override
    public GtfsSchedule get() throws IOException, InterruptedException {
        throw new NoRepositoryConfiguredException(
                "No GTFS schedule repository is configured. This is a fallback implementation, and no data is available.");
    }

}
