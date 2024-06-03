package ch.naviqore.service.impl.transfer;

import ch.naviqore.gtfs.schedule.model.GtfsSchedule;
import ch.naviqore.gtfs.schedule.model.GtfsScheduleBuilder;
import ch.naviqore.gtfs.schedule.model.Stop;
import ch.naviqore.service.walk.BeeLineWalkCalculator;
import ch.naviqore.service.walk.WalkCalculator;
import ch.naviqore.utils.spatial.index.KDTree;
import ch.naviqore.utils.spatial.index.KDTreeBuilder;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

public class WalkTransferGeneratorTest {

    static WalkCalculator defaultCalculator = new BeeLineWalkCalculator(4000);
    static int defaultMinimumTransferTime = 120;
    static int defaultMaxWalkDistance = 500;

    // distances between:
    // Stadelhofen <-> Opernhaus = 171 m
    // Stadelhofen <-> Kunsthaus = 573 m
    // Opernhaus <-> Kunsthaus = 403 m
    static Map<String, StopData> testStops = Map.of("stop1",
            new StopData("stop1", "Zürich, Stadelhofen", 47.366542, 8.548384), "stop2",
            new StopData("stop2", "Zürich, Opernhaus", 47.365030, 8.547976), "stop3",
            new StopData("stop3", "Zürich, Kunsthaus", 47.370160, 8.548749));

    static void assertNoSameStationTransfers(List<TransferGenerator.Transfer> transfers) {
        for (TransferGenerator.Transfer transfer : transfers) {
            assertNotEquals(transfer.from(), transfer.to());
        }
    }

    static TransferGenerator.Transfer transferWasCreated(List<TransferGenerator.Transfer> transfers, String fromStopId,
                                                  String toStopId) {
        for (TransferGenerator.Transfer transfer : transfers) {
            if (transfer.from().getId().equals(fromStopId) && transfer.to().getId().equals(toStopId)) {
                return transfer;
            }
        }
        throw new NoSuchElementException();
    }

    static GtfsSchedule getSchedule() {
        GtfsScheduleBuilder builder = GtfsSchedule.builder();
        for (StopData stopData : testStops.values()) {
            builder.addStop(stopData.id(), stopData.name(), stopData.id(), stopData.lat(), stopData.lon());
        }
        return builder.build();
    }

    static KDTree<Stop> getSpatialStopIndex(GtfsSchedule schedule) {
        return new KDTreeBuilder<Stop>().addLocations(schedule.getStops().values()).build();
    }

    static WalkTransferGenerator getDefaultWalkTransferGenerator() {
        return getDefaultWalkTransferGenerator(getSchedule());
    }

    static WalkTransferGenerator getDefaultWalkTransferGenerator(GtfsSchedule schedule) {
        return new WalkTransferGenerator(defaultCalculator, defaultMinimumTransferTime, defaultMaxWalkDistance,
                getSpatialStopIndex(schedule));
    }

    public void assertTransfersGenerated(List<TransferGenerator.Transfer> transfers, String fromStopId, String toStopId,
                                         int minimumTransferTime) {
        assertTransfersGenerated(transfers, fromStopId, toStopId, minimumTransferTime, false);
    }

    public void assertTransfersGenerated(List<TransferGenerator.Transfer> transfers, String fromStopId, String toStopId,
                                         int minimumTransferTime, boolean shouldBeMinimumTransferTime) {
        try {
            TransferGenerator.Transfer transfer1 = transferWasCreated(transfers, fromStopId, toStopId);
            TransferGenerator.Transfer transfer2 = transferWasCreated(transfers, toStopId, fromStopId);

            if (shouldBeMinimumTransferTime) {
                assertEquals(minimumTransferTime, transfer1.duration());
                assertEquals(minimumTransferTime, transfer2.duration());
            } else {
                assertTrue(transfer1.duration() > minimumTransferTime);
                assertTrue(transfer2.duration() > minimumTransferTime);
            }

        } catch (NoSuchElementException e) {
            throw new AssertionError(
                    String.format("Transfer not found between stops: %s and %s", fromStopId, toStopId));
        }
    }

    public void assertTransferNotGenerated(List<TransferGenerator.Transfer> transfers, String fromStopId, String toStopId) {
        assertThrows(NoSuchElementException.class, () -> transferWasCreated(transfers, fromStopId, toStopId));
        assertThrows(NoSuchElementException.class, () -> transferWasCreated(transfers, toStopId, fromStopId));
    }

    record StopData(String id, String name, double lat, double lon) {
    }

    @Nested
    class Constructor {

        @Test
        void simpleTransferGenerator() {
            assertDoesNotThrow(() -> getDefaultWalkTransferGenerator());
        }

        @Test
        void nullWalkCalculator_shouldThrowException() {
            KDTree<Stop> spatialIndex = getSpatialStopIndex(getSchedule());
            assertThrows(IllegalArgumentException.class,
                    () -> new WalkTransferGenerator(null, defaultMinimumTransferTime, defaultMaxWalkDistance,
                            spatialIndex));
        }

        @Test
        void negativeSameStationTransferTime_shouldThrowException() {
            assertThrows(IllegalArgumentException.class,
                    () -> new WalkTransferGenerator(defaultCalculator, -1, defaultMaxWalkDistance,
                            getSpatialStopIndex(getSchedule())));
        }

        @Test
        void zeroSameStationTransferTime_shouldNotThrowException() {
            assertDoesNotThrow(() -> new WalkTransferGenerator(defaultCalculator, 0, defaultMaxWalkDistance,
                    getSpatialStopIndex(getSchedule())));
        }

        @Test
        void negativeMaxWalkDistance_shouldThrowException() {
            assertThrows(IllegalArgumentException.class,
                    () -> new WalkTransferGenerator(defaultCalculator, defaultMinimumTransferTime, -1,
                            getSpatialStopIndex(getSchedule())));
        }

        @Test
        void zeroMaxWalkDistance_shouldThrowException() {
            assertThrows(IllegalArgumentException.class,
                    () -> new WalkTransferGenerator(defaultCalculator, defaultMinimumTransferTime, 0,
                            getSpatialStopIndex(getSchedule())));
        }

        @Test
        void nullSpatialIndex_shouldThrowException() {
            assertThrows(IllegalArgumentException.class,
                    () -> new WalkTransferGenerator(defaultCalculator, defaultMinimumTransferTime,
                            defaultMaxWalkDistance, null));
        }

    }

    @Nested
    class CreateTransfers {

        @Test
        void expectedBehavior() {
            GtfsSchedule schedule = getSchedule();
            WalkTransferGenerator generator = getDefaultWalkTransferGenerator(schedule);

            List<TransferGenerator.Transfer> transfers = generator.generateTransfers(schedule);

            assertNoSameStationTransfers(transfers);

            // all transfers from / to Stadelhofen should be within search radius
            assertTransfersGenerated(transfers, "stop1", "stop2", defaultMinimumTransferTime);
            assertTransfersGenerated(transfers, "stop1", "stop3", defaultMinimumTransferTime);

            // transfer between Kunsthaus and Opernhaus should not be created
            assertTransferNotGenerated(transfers, "stop2", "stop3");
        }

        @Test
        void expectedBehavior_withIncreasedSearchRadius() {
            GtfsSchedule schedule = getSchedule();
            WalkTransferGenerator generator = new WalkTransferGenerator(defaultCalculator, defaultMinimumTransferTime,
                    1000, getSpatialStopIndex(schedule));

            List<TransferGenerator.Transfer> transfers = generator.generateTransfers(schedule);

            assertNoSameStationTransfers(transfers);
            assertTransfersGenerated(transfers, "stop1", "stop2", defaultMinimumTransferTime);
            assertTransfersGenerated(transfers, "stop1", "stop3", defaultMinimumTransferTime);
            assertTransfersGenerated(transfers, "stop2", "stop3", defaultMinimumTransferTime);
        }

        @Test
        void expectedBehavior_shouldBeMinimumTransferTime() {
            int minimumTransferTime = 2000; // should be applied to all stops within search radius
            GtfsSchedule schedule = getSchedule();
            WalkTransferGenerator generator = new WalkTransferGenerator(defaultCalculator, minimumTransferTime,
                    defaultMaxWalkDistance, getSpatialStopIndex(schedule));

            List<TransferGenerator.Transfer> transfers = generator.generateTransfers(schedule);

            assertNoSameStationTransfers(transfers);
            // all transfers from / to Stadelhofen should be within search radius
            assertTransfersGenerated(transfers, "stop1", "stop2", minimumTransferTime, true);
            assertTransfersGenerated(transfers, "stop1", "stop3", minimumTransferTime, true);

            // transfer between Kunsthaus and Opernhaus should not be created
            assertTransferNotGenerated(transfers, "stop2", "stop3");
        }
    }

}
