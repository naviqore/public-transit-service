package org.naviqore.app.infrastructure;

import lombok.RequiredArgsConstructor;
import org.naviqore.gtfs.schedule.GtfsScheduleReader;
import org.naviqore.gtfs.schedule.model.GtfsSchedule;
import org.naviqore.service.repo.GtfsScheduleRepository;
import org.naviqore.utils.network.FileDownloader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@RequiredArgsConstructor
public class GtfsScheduleUrl implements GtfsScheduleRepository {

    private static final String TMP_DIRECTORY_PREFIX = "tmp_gtfs_";
    private static final String FILE_NAME = "gtfs.zip";

    private final String url;

    @Override
    public GtfsSchedule get() throws IOException, InterruptedException {
        Path tempDir = Files.createTempDirectory(TMP_DIRECTORY_PREFIX);
        Path filePath = tempDir.resolve(FILE_NAME);
        try {
            new FileDownloader(url).downloadTo(tempDir, FILE_NAME, true);
            return new GtfsScheduleReader().read(filePath.toString());
        } finally {
            Files.deleteIfExists(filePath);
            Files.deleteIfExists(tempDir);
        }
    }
}
