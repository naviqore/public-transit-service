package org.naviqore.service.repo;

import org.naviqore.timetable.model.Timetable;

import java.io.IOException;

/**
 * TimetableRepository interface provides a contract for retrieving the timetable the service is built from.
 * Implementations of this interface can source data from files, URLs, or databases, in any schedule format that
 * provides a {@link Timetable}.
 */
public interface TimetableRepository {

    /**
     * Retrieves the timetable data.
     *
     * @return a Timetable object containing the timetable data.
     */
    Timetable get() throws IOException, InterruptedException;

}
