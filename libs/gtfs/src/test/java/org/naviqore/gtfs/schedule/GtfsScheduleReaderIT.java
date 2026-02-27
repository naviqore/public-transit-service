package org.naviqore.gtfs.schedule;

import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.naviqore.gtfs.schedule.model.GtfsSchedule;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test for {@link GtfsScheduleReader}
 * <p>
 * Read GTFS schedule data from a ZIP file and a directory.
 */
class GtfsScheduleReaderIT {

    private static final GtfsScheduleDataset GTFS_SCHEDULE_DATASET = GtfsScheduleDataset.SAMPLE_FEED_1;
    private GtfsScheduleReader gtfsScheduleReader;

    @BeforeEach
    void setUp() {
        gtfsScheduleReader = new GtfsScheduleReader();
    }

    @Test
    void shouldReadFromZipFile(@TempDir Path tempDir) throws IOException {
        Path zipFile = GTFS_SCHEDULE_DATASET.getZip(tempDir).toPath();
        GtfsSchedule schedule = gtfsScheduleReader.read(zipFile.toAbsolutePath().toString());
        assertScheduleSizes(schedule);
    }

    @Test
    void shouldReadFromDirectory(@TempDir Path tempDir) throws IOException {
        Path unzippedDir = GTFS_SCHEDULE_DATASET.getUnzipped(tempDir).toPath();
        GtfsSchedule schedule = gtfsScheduleReader.read(unzippedDir.toAbsolutePath().toString());
        assertScheduleSizes(schedule);
    }

    @Test
    void shouldRead_withoutCalendarFile(@TempDir Path tempDir) throws IOException {
        Path unzippedDir = GTFS_SCHEDULE_DATASET.getUnzipped(tempDir).toPath();
        Files.deleteIfExists(unzippedDir.resolve("calendar.txt"));

        GtfsSchedule schedule = gtfsScheduleReader.read(unzippedDir.toString());
        assertScheduleSizes(schedule, false);

        // re-run with zipped directory
        Path zipFile = tempDir.resolve("gtfs_schedule_no_calendar.zip");
        zipDirectory(unzippedDir, zipFile);
        schedule = gtfsScheduleReader.read(zipFile.toString());
        assertScheduleSizes(schedule, false);
    }

    @Test
    void shouldRead_withoutCalendarDatesFile(@TempDir Path tempDir) throws IOException {
        Path unzippedDir = GTFS_SCHEDULE_DATASET.getUnzipped(tempDir).toPath();
        Files.deleteIfExists(unzippedDir.resolve("calendar_dates.txt"));

        GtfsSchedule schedule = gtfsScheduleReader.read(unzippedDir.toString());
        assertScheduleSizes(schedule);

        Path zipFile = tempDir.resolve("gtfs_schedule_no_calendar_dates.zip");
        zipDirectory(unzippedDir, zipFile);
        schedule = gtfsScheduleReader.read(zipFile.toString());
        assertScheduleSizes(schedule);
    }

    @Test
    void shouldRead_withoutTransfersFile(@TempDir Path tempDir) throws IOException {
        Path unzippedDir = GTFS_SCHEDULE_DATASET.getUnzipped(tempDir).toPath();
        Files.deleteIfExists(unzippedDir.resolve("transfers.txt"));

        GtfsSchedule schedule = gtfsScheduleReader.read(unzippedDir.toString());
        assertScheduleSizes(schedule);

        Path zipFile = tempDir.resolve("gtfs_schedule_no_transfers.zip");
        zipDirectory(unzippedDir, zipFile);
        schedule = gtfsScheduleReader.read(zipFile.toString());
        assertScheduleSizes(schedule);
    }

    @Test
    void shouldNotReadFromDirectory_withoutCalendarAndCalendarDatesFiles(@TempDir Path tempDir) throws IOException {
        Path unzippedDir = GTFS_SCHEDULE_DATASET.getUnzipped(tempDir).toPath();
        Files.deleteIfExists(unzippedDir.resolve("calendar.txt"));
        Files.deleteIfExists(unzippedDir.resolve("calendar_dates.txt"));

        assertThatThrownBy(() -> gtfsScheduleReader.read(unzippedDir.toString())).isInstanceOf(
                FileNotFoundException.class).hasMessageContaining("Conditional requirement not met:");

        Path zipFile = tempDir.resolve("gtfs_schedule_failing.zip");
        zipDirectory(unzippedDir, zipFile);
        assertThatThrownBy(() -> gtfsScheduleReader.read(zipFile.toString())).isInstanceOf(FileNotFoundException.class)
                .hasMessageContaining("Conditional requirement not met:");
    }

    @ParameterizedTest
    @ValueSource(strings = {"agency.txt", "stops.txt", "routes.txt", "trips.txt", "stop_times.txt"})
    void shouldNotRead_withoutRequiredFile(String missingFile, @TempDir Path tempDir) throws IOException {
        Path unzippedDir = GTFS_SCHEDULE_DATASET.getUnzipped(tempDir).toPath();
        Files.delete(unzippedDir.resolve(missingFile));

        // check directory
        assertThatThrownBy(() -> gtfsScheduleReader.read(unzippedDir.toString())).isInstanceOf(
                FileNotFoundException.class).hasMessageContaining("Required GTFS CSV file");

        // check zip
        Path zipFile = tempDir.resolve("gtfs_missing_" + missingFile + ".zip");
        zipDirectory(unzippedDir, zipFile);
        assertThatThrownBy(() -> gtfsScheduleReader.read(zipFile.toString())).isInstanceOf(FileNotFoundException.class)
                .hasMessageContaining("Required GTFS CSV file");
    }

    private void assertScheduleSizes(GtfsSchedule schedule) {
        assertScheduleSizes(schedule, true);
    }

    private void assertScheduleSizes(GtfsSchedule schedule, boolean withCalendarTextFile) {
        assertThat(schedule.getAgencies()).as("Agencies").hasSize(1);
        assertThat(schedule.getRoutes()).as("Routes").hasSize(5);
        assertThat(schedule.getStops()).as("Stops").hasSize(9);
        assertThat(schedule.getTrips()).as("Trips").hasSize(withCalendarTextFile ? 11 : 7);
    }

    private void zipDirectory(Path sourcePath, Path zipFilePath) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipFilePath))) {
            Files.walkFileTree(sourcePath, new SimpleFileVisitor<>() {
                @Override
                public @NonNull FileVisitResult visitFile(@NonNull Path file,
                                                          @NonNull BasicFileAttributes attrs) throws IOException {
                    String entryName = sourcePath.relativize(file).toString().replace("\\", "/");
                    zos.putNextEntry(new ZipEntry(entryName));
                    Files.copy(file, zos);
                    zos.closeEntry();

                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }
}