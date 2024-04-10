package ch.naviqore.gtfs.schedule;

import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;

class GtfsScheduleReaderIT {

    private GtfsScheduleReader gtfsScheduleReader;

    @BeforeEach
    void setUp() {
        gtfsScheduleReader = new GtfsScheduleReader();
    }

    @Test
    void readFromZipFile(@TempDir Path tempDir) throws IOException {
        File zipFile = GtfsScheduleTestData.prepareZipDataset(tempDir);
        Map<GtfsScheduleReader.GtfsFile, List<CSVRecord>> records = gtfsScheduleReader.read(zipFile.getAbsolutePath());
        assertFalse(records.isEmpty(), "The records map should not be empty");
    }

    @Test
    void readFromDirectory(@TempDir Path tempDir) throws IOException {
        File unzippedDir = GtfsScheduleTestData.prepareUnzippedDataset(tempDir);
        Map<GtfsScheduleReader.GtfsFile, List<CSVRecord>> records = gtfsScheduleReader.read(unzippedDir.getAbsolutePath());
        assertFalse(records.isEmpty(), "The records map should not be empty");
    }
}