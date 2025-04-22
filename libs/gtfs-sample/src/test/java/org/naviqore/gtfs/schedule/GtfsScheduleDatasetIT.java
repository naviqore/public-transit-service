package org.naviqore.gtfs.schedule;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GtfsScheduleDatasetIT {

    @Nested
    class Local {

        @Test
        void shouldReturnZipFile(@TempDir Path tempDir) throws Exception {
            File zipFile = GtfsScheduleDataset.SAMPLE_FEED_1.getZip(tempDir);

            assertNotNull(zipFile);
            assertTrue(zipFile.exists());
            assertTrue(zipFile.isFile());
            assertTrue(zipFile.getName().endsWith(".zip"));
        }

        @Test
        void shouldUnzipDataset(@TempDir Path tempDir) throws Exception {
            File unzippedDir = GtfsScheduleDataset.SAMPLE_FEED_1.getUnzipped(tempDir);

            assertNotNull(unzippedDir);
            assertTrue(unzippedDir.exists());
            assertTrue(unzippedDir.isDirectory());

            File[] files = unzippedDir.listFiles();
            assertNotNull(files);
            assertTrue(files.length > 0);
        }
    }

    @Disabled("Disabled to avoid dependency on external resources during CI testing.")
    @Nested
    class Remote {

        @Test
        void shouldDownloadAndUnzip(@TempDir Path tempDir) throws Exception {
            File unzipped = GtfsScheduleDataset.ZURICH_TRAMS.getUnzipped(tempDir);

            assertNotNull(unzipped);
            assertTrue(unzipped.exists());
            assertTrue(unzipped.isDirectory());

            File[] files = unzipped.listFiles();
            assertNotNull(files);
            assertTrue(files.length > 0);
        }
    }
}
