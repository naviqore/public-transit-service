package ch.naviqore.gtfs.schedule;

import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Test helper class
 *
 * @author munterfi
 */
public class GtfsScheduleTestData {

    public static final String SAMPLE_FEED = "sample-feed-1";
    public static final String SAMPLE_FEED_ZIP = SAMPLE_FEED + ".zip";
    public static final String RESOURCE_PATH = "gtfs/schedule/" + SAMPLE_FEED_ZIP;

    public static File prepareZipDataset(@TempDir Path tempDir) throws IOException {
        try (InputStream is = GtfsScheduleReaderIT.class.getClassLoader().getResourceAsStream(RESOURCE_PATH)) {
            if (is == null) {
                throw new FileNotFoundException("Resource file not found: " + RESOURCE_PATH);
            }

            File tempFile = tempDir.resolve(SAMPLE_FEED_ZIP).toFile();
            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                is.transferTo(fos);
            }
            return tempFile;
        }
    }

    public static File prepareUnzippedDataset(@TempDir Path tempDir) throws IOException {
        File unzippedDir = tempDir.resolve(SAMPLE_FEED).toFile();
        if (!unzippedDir.mkdir()) {
            throw new IOException("Could not create directory for unzipped GTFS data");
        }

        try (InputStream is = GtfsScheduleReaderIT.class.getClassLoader().getResourceAsStream(RESOURCE_PATH)) {
            if (is == null) {
                throw new FileNotFoundException("Resource file not found: " + RESOURCE_PATH);
            }
            try (ZipInputStream zis = new ZipInputStream(is)) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    File file = new File(unzippedDir, entry.getName());
                    if (entry.isDirectory()) {
                        if (!file.isDirectory() && !file.mkdirs()) {
                            throw new IOException("Failed to create directory " + file);
                        }
                    } else {
                        try (FileOutputStream fos = new FileOutputStream(file)) {
                            zis.transferTo(fos);
                        }
                    }
                    zis.closeEntry();
                }
            }
        }

        return unzippedDir;
    }

}

