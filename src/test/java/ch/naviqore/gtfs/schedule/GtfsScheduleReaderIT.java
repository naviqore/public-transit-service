package ch.naviqore.gtfs.schedule;

import ch.naviqore.gtfs.schedule.model.GtfsSchedule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for {@link GtfsScheduleReader}
 * <p>
 * Read GTFS schedule data from a ZIP file and a directory.
 *
 * @author munterfi
 */
class GtfsScheduleReaderIT {

    private GtfsScheduleReader gtfsScheduleReader;

    @BeforeEach
    void setUp() {
        gtfsScheduleReader = new GtfsScheduleReader();
    }

    @Test
    void readFromZipFile(@TempDir Path tempDir) throws IOException {
        File zipFile = GtfsScheduleTestData.prepareZipDataset(tempDir);
        GtfsSchedule schedule = gtfsScheduleReader.read(zipFile.getAbsolutePath());
        assertScheduleSizes(schedule);
    }

    @Test
    void readFromDirectory(@TempDir Path tempDir) throws IOException {
        File unzippedDir = GtfsScheduleTestData.prepareUnzippedDataset(tempDir);
        GtfsSchedule schedule = gtfsScheduleReader.read(unzippedDir.getAbsolutePath());
        assertScheduleSizes(schedule);
    }

    private void assertScheduleSizes(GtfsSchedule schedule) {
        assertThat(schedule.getAgencies()).as("Agencies").hasSize(1);
        assertThat(schedule.getRoutes()).as("Routes").hasSize(5);
        assertThat(schedule.getStops()).as("Stops").hasSize(9);
        assertThat(schedule.getTrips()).as("Trips").hasSize(11);
    }
}