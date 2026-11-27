package org.naviqore.service.repo;

import org.naviqore.timetable.model.Timetable;

import java.io.IOException;

/**
 * A fallback implementation of the TimetableRepository interface, used when no actual repository is configured. This
 * implementation throws an exception indicating the absence of a repository.
 */
public class NoTimetableRepository implements TimetableRepository {

    @Override
    public Timetable get() throws IOException, InterruptedException {
        throw new NoRepositoryConfiguredException(
                "No timetable repository is configured. This is a fallback implementation, and no data is available.");
    }

}
