package org.naviqore.app.infrastructure;

import lombok.AllArgsConstructor;
import org.naviqore.gtfs.schedule.GtfsScheduleReader;
import org.naviqore.gtfs.schedule.model.GtfsSchedule;
import org.naviqore.service.repo.GtfsScheduleRepository;

import java.io.IOException;

@AllArgsConstructor
public class GtfsScheduleFile implements GtfsScheduleRepository {

    private final String filePath;

    @Override
    public GtfsSchedule get() throws IOException {
        return new GtfsScheduleReader().read(filePath);
    }

}
