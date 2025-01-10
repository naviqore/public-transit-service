package ch.naviqore.service.gtfs.raptor;

import ch.naviqore.gtfs.schedule.GtfsScheduleReader;
import ch.naviqore.gtfs.schedule.GtfsScheduleTestData;
import ch.naviqore.service.config.ServiceConfig;
import ch.naviqore.service.repo.GtfsScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class GtfsRaptorServiceInitializerIT {

    private GtfsRaptorServiceInitializer initializer;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws IOException, InterruptedException {
        File zipFile = GtfsScheduleTestData.prepareZipDataset(tempDir);
        ServiceConfig config = new ServiceConfig(zipFile.getAbsolutePath());
        GtfsScheduleRepository repo = () -> new GtfsScheduleReader().read(zipFile.toString());
        initializer = new GtfsRaptorServiceInitializer(config, repo.get());
    }

    @Test
    void shouldInitializeServiceCorrectly() {
        GtfsRaptorService service = initializer.get();

        assertNotNull(service);
        assertTrue(service.hasTravelModeInformation());
        assertFalse(service.hasAccessibilityInformation());
        assertFalse(service.hasBikeInformation());
    }

}
