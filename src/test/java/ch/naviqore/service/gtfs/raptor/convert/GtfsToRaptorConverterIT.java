package ch.naviqore.service.gtfs.raptor.convert;

import ch.naviqore.gtfs.schedule.GtfsScheduleReader;
import ch.naviqore.gtfs.schedule.GtfsScheduleTestData;
import ch.naviqore.gtfs.schedule.model.GtfsSchedule;
import ch.naviqore.raptor.RaptorAlgorithm;
import ch.naviqore.raptor.router.RaptorConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class GtfsToRaptorConverterIT {

    private GtfsSchedule schedule;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws IOException {
        File zipFile = GtfsScheduleTestData.prepareZipDataset(tempDir);
        schedule = new GtfsScheduleReader().read(zipFile.getAbsolutePath());
    }

    @Test
    void shouldConvertGtfsScheduleToRaptor() {
        GtfsToRaptorConverter mapper = new GtfsToRaptorConverter(schedule, new RaptorConfig());
        RaptorAlgorithm raptor = mapper.convert();
        assertThat(raptor).isNotNull();
    }
}