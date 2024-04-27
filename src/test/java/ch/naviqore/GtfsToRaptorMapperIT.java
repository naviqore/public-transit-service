package ch.naviqore;

import ch.naviqore.gtfs.schedule.GtfsScheduleReader;
import ch.naviqore.gtfs.schedule.GtfsScheduleTestData;
import ch.naviqore.gtfs.schedule.model.GtfsSchedule;
import ch.naviqore.raptor.model.RouteTraversal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;

class GtfsToRaptorMapperIT {

    private static final LocalDate DATE = LocalDate.of(2009, 4, 26);
    private GtfsSchedule schedule;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws IOException {
        File zipFile = GtfsScheduleTestData.prepareZipDataset(tempDir);
        schedule = new GtfsScheduleReader().read(zipFile.getAbsolutePath());
    }

    @Test
    void shouldConvertGtfsScheduleToRaptor() {
        GtfsToRaptorMapper mapper = new GtfsToRaptorMapper(RouteTraversal.builder());
        mapper.map(schedule, DATE);
    }
}