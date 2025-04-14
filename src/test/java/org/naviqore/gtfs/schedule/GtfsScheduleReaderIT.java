package org.naviqore.gtfs.schedule;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.naviqore.gtfs.schedule.model.GtfsSchedule;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    void shouldReadFromZipFile(@TempDir Path tempDir) throws IOException {
        File zipFile = GtfsScheduleTestData.prepareZipDataset(tempDir);
        GtfsSchedule schedule = gtfsScheduleReader.read(zipFile.getAbsolutePath());
        assertScheduleSizes(schedule);
    }

    @Test
    void shouldReadFromDirectory(@TempDir Path tempDir) throws IOException {
        File unzippedDir = GtfsScheduleTestData.prepareUnzippedDataset(tempDir);
        GtfsSchedule schedule = gtfsScheduleReader.read(unzippedDir.getAbsolutePath());
        assertScheduleSizes(schedule);
    }

    @Test
    void shouldRead_withoutCalendarFile(@TempDir Path tempDir) throws IOException {
        File unzippedDir = GtfsScheduleTestData.prepareUnzippedDataset(tempDir);
        File calendarFile = new File(unzippedDir, "calendar.txt");
        if (calendarFile.exists() && !calendarFile.delete()) {
            throw new IOException("Failed to delete calendar file");
        }
        GtfsSchedule schedule = gtfsScheduleReader.read(unzippedDir.getAbsolutePath());
        assertScheduleSizes(schedule, false);

        // Re Run with Zipped Directory
        File zipFile = new File(tempDir.toFile(), "gtfs_schedule.zip");
        zipDirectory(unzippedDir, zipFile);
        schedule = gtfsScheduleReader.read(zipFile.getAbsolutePath());
        assertScheduleSizes(schedule, false);
    }

    @Test
    void shouldRead_withoutCalendarDatesFile(@TempDir Path tempDir) throws IOException {
        File unzippedDir = GtfsScheduleTestData.prepareUnzippedDataset(tempDir);
        File calendarDatesFile = new File(unzippedDir, "calendar_dates.txt");
        if (calendarDatesFile.exists() && !calendarDatesFile.delete()) {
            throw new IOException("Failed to delete calendar dates file");
        }
        GtfsSchedule schedule = gtfsScheduleReader.read(unzippedDir.getAbsolutePath());
        assertScheduleSizes(schedule);

        // Re Run with Zipped Directory
        File zipFile = new File(tempDir.toFile(), "gtfs_schedule.zip");
        zipDirectory(unzippedDir, zipFile);
        schedule = gtfsScheduleReader.read(zipFile.getAbsolutePath());
        assertScheduleSizes(schedule);
    }

    @Test
    void shouldRead_withoutTransfersFile(@TempDir Path tempDir) throws IOException {
        File unzippedDir = GtfsScheduleTestData.prepareUnzippedDataset(tempDir);
        File calendarDatesFile = new File(unzippedDir, "transfers.txt");
        if (calendarDatesFile.exists() && !calendarDatesFile.delete()) {
            throw new IOException("Failed to delete transfer file");
        }
        GtfsSchedule schedule = gtfsScheduleReader.read(unzippedDir.getAbsolutePath());
        assertScheduleSizes(schedule);

        // Re Run with Zipped Directory
        File zipFile = new File(tempDir.toFile(), "gtfs_schedule.zip");
        zipDirectory(unzippedDir, zipFile);
        schedule = gtfsScheduleReader.read(zipFile.getAbsolutePath());
        assertScheduleSizes(schedule);
    }

    @Test
    void shouldNotReadFromDirectory_withoutCalendarAndCalendarDatesFiles(@TempDir Path tempDir) throws IOException {
        File unzippedDir = GtfsScheduleTestData.prepareUnzippedDataset(tempDir);
        File calendarFile = new File(unzippedDir, "calendar.txt");
        if (calendarFile.exists() && !calendarFile.delete()) {
            throw new IOException("Failed to delete calendar file");
        }
        File calendarDatesFile = new File(unzippedDir, "calendar_dates.txt");
        if (calendarDatesFile.exists() && !calendarDatesFile.delete()) {
            throw new IOException("Failed to delete calendar dates file");
        }
        // make sure gtfsScheduleReader.read(unzippedDir.getAbsolutePath()); throws FileNotFoundException
        assertThatThrownBy(() -> gtfsScheduleReader.read(unzippedDir.getAbsolutePath())).isInstanceOf(
                FileNotFoundException.class).hasMessageContaining("Conditional requirement not met:");

        // Re Run with Zipped Directory
        File zipFile = new File(tempDir.toFile(), "gtfs_schedule.zip");
        zipDirectory(unzippedDir, zipFile);
        assertThatThrownBy(() -> gtfsScheduleReader.read(zipFile.getAbsolutePath())).isInstanceOf(
                FileNotFoundException.class).hasMessageContaining("Conditional requirement not met:");
    }

    @Test
    void shouldNotReadFromDirectory_withoutRequiredFile(@TempDir Path tempDir) throws IOException {
        List<String> requiredFiles = List.of("agency.txt", "stops.txt", "routes.txt", "trips.txt", "stop_times.txt");
        for (String missingFile : requiredFiles) {
            String fileName = missingFile.substring(0, missingFile.indexOf('.'));
            // create directory in temp directory with name of missing file
            Path subTestTempDir = tempDir.resolve(fileName);
            if (!subTestTempDir.toFile().mkdir()) {
                throw new IOException("Could not create directory for unzipped GTFS data");
            }
            File unzippedDir = GtfsScheduleTestData.prepareUnzippedDataset(subTestTempDir);
            File file = new File(unzippedDir, missingFile);
            if (file.exists() && !file.delete()) {
                throw new IOException("Failed to delete " + missingFile);
            }
            // make sure gtfsScheduleReader.read(unzippedDir.getAbsolutePath()); throws FileNotFoundException
            assertThatThrownBy(() -> gtfsScheduleReader.read(unzippedDir.getAbsolutePath())).isInstanceOf(
                    FileNotFoundException.class).hasMessageContaining("Required GTFS CSV file");

            // Re Run with Zipped Directory
            File zipFile = new File(subTestTempDir.toFile(), "gtfs_schedule.zip");
            zipDirectory(unzippedDir, zipFile);
            assertThatThrownBy(() -> gtfsScheduleReader.read(zipFile.getAbsolutePath())).isInstanceOf(
                    FileNotFoundException.class).hasMessageContaining("Required GTFS CSV file");
        }
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

    // Helper method to zip a directory
    private void zipDirectory(File sourceDir, File zipFile) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            Path sourcePath = sourceDir.toPath();
            Files.walk(sourcePath).filter(path -> !Files.isDirectory(path)).forEach(path -> {
                ZipEntry zipEntry = new ZipEntry(sourcePath.relativize(path).toString());
                try {
                    zos.putNextEntry(zipEntry);
                    Files.copy(path, zos);
                    zos.closeEntry();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
    }
}