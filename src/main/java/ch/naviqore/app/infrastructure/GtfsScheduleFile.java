package ch.naviqore.app.infrastructure;

import ch.naviqore.gtfs.schedule.GtfsScheduleReader;
import ch.naviqore.gtfs.schedule.model.GtfsSchedule;
import ch.naviqore.service.repo.GtfsScheduleRepository;
import lombok.AllArgsConstructor;

import java.io.IOException;

@AllArgsConstructor
public class GtfsScheduleFile implements GtfsScheduleRepository {

    private final String filePath;

    @Override
    public GtfsSchedule get() throws IOException {
        return new GtfsScheduleReader().read(filePath);
    }

}
