package org.naviqore.service.raptor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.naviqore.gtfs.schedule.GtfsScheduleDataset;
import org.naviqore.gtfs.schedule.GtfsScheduleReader;
import org.naviqore.service.config.ServiceConfig;
import org.naviqore.service.repo.TimetableRepository;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class RaptorServiceInitializerIT {

    private RaptorServiceInitializer initializer;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws IOException, InterruptedException {
        File zipFile = GtfsScheduleDataset.SAMPLE_FEED_1.getZip(tempDir);
        TimetableRepository repo = () -> new GtfsScheduleReader().read(zipFile.toString());
        ServiceConfig config = ServiceConfig.builder().timetableRepository(repo).build();
        initializer = new RaptorServiceInitializer(config);
    }

    @Test
    void shouldInitializeServiceCorrectly() {
        RaptorService service = initializer.get();

        assertNotNull(service);
        assertTrue(service.hasTravelModeInformation());
        assertFalse(service.hasAccessibilityInformation());
        assertFalse(service.hasBikeInformation());
    }

}
