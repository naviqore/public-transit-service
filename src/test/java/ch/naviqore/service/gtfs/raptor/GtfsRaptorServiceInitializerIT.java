package ch.naviqore.service.gtfs.raptor;

import ch.naviqore.gtfs.schedule.GtfsScheduleReader;
import ch.naviqore.gtfs.schedule.GtfsScheduleTestData;
import ch.naviqore.gtfs.schedule.model.GtfsSchedule;
import ch.naviqore.gtfs.schedule.model.Stop;
import ch.naviqore.service.config.ServiceConfig;
import ch.naviqore.service.gtfs.raptor.transfer.TransferGenerator;
import ch.naviqore.service.repo.GtfsScheduleRepository;
import ch.naviqore.service.walk.WalkCalculator;
import ch.naviqore.utils.search.SearchIndex;
import ch.naviqore.utils.spatial.index.KDTree;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;

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
    void shouldSetConfigCorrectly() {
        assertNotNull(initializer.getConfig());
    }

    @Test
    void shouldInitializeWalkCalculator() {
        WalkCalculator walkCalculator = initializer.getWalkCalculator();
        assertNotNull(walkCalculator);
    }

    @Test
    void shouldReadGtfsScheduleCorrectly() {
        GtfsSchedule schedule = initializer.getSchedule();
        assertNotNull(schedule);
    }

    @Test
    void shouldGenerateStopSearchIndex() {
        SearchIndex<Stop> stopSearchIndex = initializer.getStopSearchIndex();
        assertNotNull(stopSearchIndex);
    }

    @Test
    void shouldGenerateSpatialStopIndex() {
        KDTree<Stop> spatialStopIndex = initializer.getSpatialStopIndex();
        assertNotNull(spatialStopIndex);
    }

    @Test
    void shouldGenerateAdditionalTransfers() {
        List<TransferGenerator.Transfer> additionalTransfers = initializer.getAdditionalTransfers();
        assertNotNull(additionalTransfers);
    }

}
