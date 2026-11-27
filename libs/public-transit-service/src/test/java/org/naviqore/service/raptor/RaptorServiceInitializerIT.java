package org.naviqore.service.raptor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.naviqore.gtfs.schedule.GtfsScheduleDataset;
import org.naviqore.gtfs.schedule.GtfsScheduleReader;
import org.naviqore.service.config.ServiceConfig;
import org.naviqore.service.exception.StopNotFoundException;
import org.naviqore.service.repo.TimetableRepository;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
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

    @Test
    void shouldInitializeServiceWithRowsOfTransfersThatCannotBeApplied(
            @TempDir Path tempDir) throws IOException, InterruptedException, StopNotFoundException {
        // rows the reader skips, a rule for a route that does not call at its stops, and a trip continued in seat
        Path directory = GtfsScheduleDataset.SAMPLE_FEED_1.getUnzipped(tempDir).toPath();
        Files.writeString(directory.resolve("transfers.txt"), """
                from_stop_id,to_stop_id,from_route_id,to_route_id,from_trip_id,to_trip_id,transfer_type,min_transfer_time
                EMSI,NONE,,,,,2,300
                EMSI,STAGECOACH,,,STBA,CITY1,2,300
                EMSI,STAGECOACH,,,,,2,-60
                STAGECOACH,EMSI,AB,BFC,,,2,60
                ,,,,AB1,AB2,4,
                """);
        TimetableRepository repo = () -> new GtfsScheduleReader().read(directory.toString());

        RaptorService service = new RaptorServiceInitializer(
                ServiceConfig.builder().timetableRepository(repo).build()).get();

        assertEquals("BULLFROG", service.getStopById("BULLFROG").getId());
    }

}
