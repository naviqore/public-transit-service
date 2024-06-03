package ch.naviqore.service.impl.transfer;

import ch.naviqore.gtfs.schedule.model.GtfsSchedule;
import ch.naviqore.gtfs.schedule.model.GtfsScheduleBuilder;
import ch.naviqore.gtfs.schedule.model.Stop;
import ch.naviqore.service.walk.BeeLineWalkCalculator;
import ch.naviqore.service.walk.WalkCalculator;
import ch.naviqore.utils.spatial.index.KDTree;
import ch.naviqore.utils.spatial.index.KDTreeBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class WalkTransferGeneratorTest {

    /**
     * Distances between locations:
     * <ul>
     *   <li>Stadelhofen <-> Opernhaus = 171 m</li>
     *   <li>Stadelhofen <-> Kunsthaus = 573 m</li>
     *   <li>Opernhaus <-> Kunsthaus = 403 m</li>
     * </ul>
     */
    private static final Map<String, StopData> TEST_STOPS = Map.of("stop1",
            new StopData("stop1", "Zürich, Stadelhofen", 47.366542, 8.548384), "stop2",
            new StopData("stop2", "Zürich, Opernhaus", 47.365030, 8.547976), "stop3",
            new StopData("stop3", "Zürich, Kunsthaus", 47.370160, 8.548749));
    private static final WalkCalculator DEFAULT_CALCULATOR = new BeeLineWalkCalculator(4000);
    private static final int DEFAULT_MINIMUM_TRANSFER_TIME = 120;
    private static final int DEFAULT_MAX_WALK_DISTANCE = 500;

    private GtfsSchedule schedule;
    private KDTree<Stop> spatialIndex;
    private WalkTransferGenerator generator;

    private static void assertNoSameStationTransfers(List<TransferGenerator.Transfer> transfers) {
        transfers.forEach(transfer -> assertNotEquals(transfer.from(), transfer.to(),
                "Transfer between same station generated."));
    }

    private static void assertTransfersGenerated(List<TransferGenerator.Transfer> transfers,
                                                 String fromStopId, String toStopId) {
        assertTrue(transfers.stream()
                .anyMatch(transfer -> transfer.from().getId().equals(fromStopId) && transfer.to()
                        .getId()
                        .equals(toStopId)), "Expected transfer not found.");
    }

    private static void assertTransferNotGenerated(List<TransferGenerator.Transfer> transfers,
                                                   String fromStopId, String toStopId) {
        assertTrue(transfers.stream()
                .noneMatch(transfer -> transfer.from().getId().equals(fromStopId) && transfer.to()
                        .getId()
                        .equals(toStopId)), "Unexpected transfer found.");
    }

    @BeforeEach
    void setUp() {
        GtfsScheduleBuilder builder = GtfsSchedule.builder();
        TEST_STOPS.values()
                .forEach(stopData -> builder.addStop(stopData.id(), stopData.name(), stopData.id(), stopData.lat(),
                        stopData.lon()));
        schedule = builder.build();
        spatialIndex = new KDTreeBuilder<Stop>().addLocations(schedule.getStops().values()).build();
        generator = new WalkTransferGenerator(DEFAULT_CALCULATOR, DEFAULT_MINIMUM_TRANSFER_TIME,
                DEFAULT_MAX_WALK_DISTANCE, spatialIndex);
    }

    private record StopData(String id, String name, double lat, double lon) {
    }

    @Nested
    class Constructor {

        @Test
        void simpleTransferGenerator_shouldNotThrowException() {
            assertDoesNotThrow(() -> new WalkTransferGenerator(DEFAULT_CALCULATOR, DEFAULT_MINIMUM_TRANSFER_TIME,
                    DEFAULT_MAX_WALK_DISTANCE, spatialIndex));
        }

        @Test
        void nullWalkCalculator_shouldThrowException() {
            assertThrows(IllegalArgumentException.class,
                    () -> new WalkTransferGenerator(null, DEFAULT_MINIMUM_TRANSFER_TIME, DEFAULT_MAX_WALK_DISTANCE,
                            spatialIndex));
        }

        @Test
        void negativeSameStationTransferTime_shouldThrowException() {
            assertThrows(IllegalArgumentException.class,
                    () -> new WalkTransferGenerator(DEFAULT_CALCULATOR, -1, DEFAULT_MAX_WALK_DISTANCE, spatialIndex));
        }

        @Test
        void zeroSameStationTransferTime_shouldNotThrowException() {
            assertDoesNotThrow(
                    () -> new WalkTransferGenerator(DEFAULT_CALCULATOR, 0, DEFAULT_MAX_WALK_DISTANCE, spatialIndex));
        }

        @Test
        void negativeMaxWalkDistance_shouldThrowException() {
            assertThrows(IllegalArgumentException.class,
                    () -> new WalkTransferGenerator(DEFAULT_CALCULATOR, DEFAULT_MINIMUM_TRANSFER_TIME, -1,
                            spatialIndex));
        }

        @Test
        void zeroMaxWalkDistance_shouldThrowException() {
            assertThrows(IllegalArgumentException.class,
                    () -> new WalkTransferGenerator(DEFAULT_CALCULATOR, DEFAULT_MINIMUM_TRANSFER_TIME, 0,
                            spatialIndex));
        }

        @Test
        void nullSpatialIndex_shouldThrowException() {
            assertThrows(IllegalArgumentException.class,
                    () -> new WalkTransferGenerator(DEFAULT_CALCULATOR, DEFAULT_MINIMUM_TRANSFER_TIME,
                            DEFAULT_MAX_WALK_DISTANCE, null));
        }
    }

    @Nested
    class CreateTransfers {

        @Test
        void expectedBehavior() {
            List<TransferGenerator.Transfer> transfers = generator.generateTransfers(schedule);
            assertNoSameStationTransfers(transfers);
            assertTransfersGenerated(transfers, "stop1", "stop2");
            assertTransfersGenerated(transfers, "stop1", "stop3");
            assertTransferNotGenerated(transfers, "stop2", "stop3");
        }

        @Test
        void expectedBehavior_withIncreasedSearchRadius() {
            WalkTransferGenerator extendedRadiusGenerator = new WalkTransferGenerator(DEFAULT_CALCULATOR,
                    DEFAULT_MINIMUM_TRANSFER_TIME, 1000, spatialIndex);
            List<TransferGenerator.Transfer> transfers = extendedRadiusGenerator.generateTransfers(schedule);
            assertNoSameStationTransfers(transfers);
            assertTransfersGenerated(transfers, "stop1", "stop2");
            assertTransfersGenerated(transfers, "stop1", "stop3");
            assertTransfersGenerated(transfers, "stop2", "stop3");
        }

        @Test
        void expectedBehavior_shouldBeMinimumTransferTime() {
            int minimumTransferTime = 2000;
            WalkTransferGenerator highMinTimeGenerator = new WalkTransferGenerator(DEFAULT_CALCULATOR,
                    minimumTransferTime, DEFAULT_MAX_WALK_DISTANCE, spatialIndex);
            List<TransferGenerator.Transfer> transfers = highMinTimeGenerator.generateTransfers(schedule);
            assertNoSameStationTransfers(transfers);
            assertTransfersGenerated(transfers, "stop1", "stop2");
            assertTransfersGenerated(transfers, "stop1", "stop3");
            assertTransferNotGenerated(transfers, "stop2", "stop3");
            assertTransferNotGenerated(transfers, "stop1", "stop1");
        }
    }
}
