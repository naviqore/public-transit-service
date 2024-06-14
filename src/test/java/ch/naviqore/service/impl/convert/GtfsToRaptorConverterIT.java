package ch.naviqore.service.impl.convert;

import ch.naviqore.gtfs.schedule.GtfsScheduleReader;
import ch.naviqore.gtfs.schedule.GtfsScheduleTestData;
import ch.naviqore.gtfs.schedule.model.GtfsSchedule;
import ch.naviqore.raptor.Raptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class GtfsToRaptorConverterIT {

    private static final LocalDate DATE = LocalDate.of(2009, 4, 26);
    private static final int SAME_STOP_TRANSFER_TIME = 120;
    private GtfsSchedule schedule;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws IOException {
        File zipFile = GtfsScheduleTestData.prepareZipDataset(tempDir);
        schedule = new GtfsScheduleReader().read(zipFile.getAbsolutePath());
    }

    @Test
    void shouldConvertGtfsScheduleToRaptor() {
        GtfsToRaptorConverter mapper = new GtfsToRaptorConverter(schedule, SAME_STOP_TRANSFER_TIME);
        Raptor raptor = mapper.convert(DATE);
        assertThat(raptor).isNotNull();
    }
}