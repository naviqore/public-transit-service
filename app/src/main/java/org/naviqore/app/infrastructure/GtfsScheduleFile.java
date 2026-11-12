package org.naviqore.app.infrastructure;

import lombok.RequiredArgsConstructor;
import org.naviqore.gtfs.schedule.GtfsScheduleReader;
import org.naviqore.gtfs.schedule.model.GtfsSchedule;
import org.naviqore.service.repo.TimetableRepository;

import java.io.IOException;

@RequiredArgsConstructor
public class GtfsScheduleFile implements TimetableRepository {

    private final String filePath;

    @Override
    public GtfsSchedule get() throws IOException {
        return new GtfsScheduleReader().read(filePath);
    }

}
