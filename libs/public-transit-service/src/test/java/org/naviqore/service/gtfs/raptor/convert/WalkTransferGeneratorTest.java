package org.naviqore.service.gtfs.raptor.convert;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.naviqore.gtfs.schedule.model.GtfsSchedule;
import org.naviqore.gtfs.schedule.model.GtfsScheduleBuilder;
import org.naviqore.gtfs.schedule.model.Stop;
import org.naviqore.service.config.ServiceConfig;
import org.naviqore.service.walk.BeeLineWalkCalculator;
import org.naviqore.service.walk.WalkCalculator;
import org.naviqore.utils.spatial.index.KDTree;
import org.naviqore.utils.spatial.index.KDTreeBuilder;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;

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
    private static final WalkCalculator DEFAULT_CALCULATOR = new BeeLineWalkCalculator(
            ServiceConfig.DEFAULT_WALKING_SPEED);
    private static final int DEFAULT_MINIMUM_TRANSFER_TIME = 120;
    private static final int DEFAULT_ACCESS_EGRESS_TIME = 15;
    private static final int DEFAULT_SEARCH_RADIUS = 500;

    private GtfsSchedule schedule;
    private KDTree<Stop> spatialIndex;
    private WalkTransferGenerator generator;

    private static TransferGenerator.Transfer transferWasCreated(List<TransferGenerator.Transfer> transfers,
                                                                 String fromStopId, String toStopId) {
        for (TransferGenerator.Transfer transfer : transfers) {
            if (transfer.from().getId().equals(fromStopId) && transfer.to().getId().equals(toStopId)) {
                return transfer;
            }
        }
        throw new NoSuchElementException();
    }

    private static void assertNoSameStopTransfers(List<TransferGenerator.Transfer> transfers) {
        for (TransferGenerator.Transfer transfer : transfers) {
            assertNotEquals(transfer.from(), transfer.to());
        }
    }

    private static void assertTransfersGenerated(List<TransferGenerator.Transfer> transfers, String fromStopId,
                                                 String toStopId) {
        assertTransfersGenerated(transfers, fromStopId, toStopId,
                WalkTransferGeneratorTest.DEFAULT_MINIMUM_TRANSFER_TIME, false);
    }

    private static void assertTransferNotGenerated(List<TransferGenerator.Transfer> transfers, String fromStopId,
                                                   String toStopId) {
        assertThrows(NoSuchElementException.class, () -> transferWasCreated(transfers, fromStopId, toStopId));
        assertThrows(NoSuchElementException.class, () -> transferWasCreated(transfers, toStopId, fromStopId));
    }

    private static void assertTransfersGenerated(List<TransferGenerator.Transfer> transfers, String fromStopId,
                                                 String toStopId, int minimumTransferTime,
                                                 boolean shouldBeMinimumTransferTime) {
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

    @BeforeEach
    void setUp() {
        GtfsScheduleBuilder builder = GtfsSchedule.builder();
        TEST_STOPS.values()
                .forEach(stopData -> builder.addStop(stopData.id(), stopData.name(), stopData.lat(), stopData.lon()));
        builder.addCalendar("ALWAYS", EnumSet.allOf(DayOfWeek.class), LocalDate.MIN, LocalDate.MAX);
        schedule = builder.build();
        spatialIndex = new KDTreeBuilder<Stop>().addLocations(schedule.getStops().values()).build();
        generator = new WalkTransferGenerator(DEFAULT_CALCULATOR, DEFAULT_MINIMUM_TRANSFER_TIME,
                DEFAULT_ACCESS_EGRESS_TIME, DEFAULT_SEARCH_RADIUS, spatialIndex);
    }

    private record StopData(String id, String name, double lat, double lon) {
    }

    @Nested
    class Constructor {

        @Test
        void simpleTransferGenerator() {
            assertDoesNotThrow(() -> new WalkTransferGenerator(DEFAULT_CALCULATOR, DEFAULT_MINIMUM_TRANSFER_TIME,
                    DEFAULT_ACCESS_EGRESS_TIME, DEFAULT_SEARCH_RADIUS, spatialIndex));
        }

        @Test
        void nullWalkCalculator_shouldThrowException() {
            assertThrows(IllegalArgumentException.class,
                    () -> new WalkTransferGenerator(null, DEFAULT_MINIMUM_TRANSFER_TIME, DEFAULT_ACCESS_EGRESS_TIME,
                            DEFAULT_SEARCH_RADIUS, spatialIndex));
        }

        @Test
        void negativeSameStopTransferTime_shouldThrowException() {
            assertThrows(IllegalArgumentException.class,
                    () -> new WalkTransferGenerator(DEFAULT_CALCULATOR, -1, DEFAULT_ACCESS_EGRESS_TIME,
                            DEFAULT_SEARCH_RADIUS, spatialIndex));
        }

        @Test
        void negativeAccessEgressTime_shouldThrowException() {
            assertThrows(IllegalArgumentException.class,
                    () -> new WalkTransferGenerator(DEFAULT_CALCULATOR, DEFAULT_MINIMUM_TRANSFER_TIME, -1,
                            DEFAULT_SEARCH_RADIUS, spatialIndex));
        }

        @Test
        void zeroSameStopTransferTime_shouldNotThrowException() {
            assertDoesNotThrow(() -> new WalkTransferGenerator(DEFAULT_CALCULATOR, 0, DEFAULT_ACCESS_EGRESS_TIME,
                    DEFAULT_SEARCH_RADIUS, spatialIndex));
        }

        @Test
        void zeroSameAccessEgressTime_shouldNotThrowException() {
            assertDoesNotThrow(() -> new WalkTransferGenerator(DEFAULT_CALCULATOR, DEFAULT_MINIMUM_TRANSFER_TIME, 0,
                    DEFAULT_SEARCH_RADIUS, spatialIndex));
        }

        @Test
        void negativeSearchRadius_shouldThrowException() {
            assertThrows(IllegalArgumentException.class,
                    () -> new WalkTransferGenerator(DEFAULT_CALCULATOR, DEFAULT_MINIMUM_TRANSFER_TIME,
                            DEFAULT_ACCESS_EGRESS_TIME, -1, spatialIndex));
        }

        @Test
        void zeroSearchRadius_shouldThrowException() {
            assertThrows(IllegalArgumentException.class,
                    () -> new WalkTransferGenerator(DEFAULT_CALCULATOR, DEFAULT_MINIMUM_TRANSFER_TIME,
                            DEFAULT_ACCESS_EGRESS_TIME, 0, spatialIndex));
        }

        @Test
        void nullSpatialIndex_shouldThrowException() {
            assertThrows(IllegalArgumentException.class,
                    () -> new WalkTransferGenerator(DEFAULT_CALCULATOR, DEFAULT_MINIMUM_TRANSFER_TIME,
                            DEFAULT_ACCESS_EGRESS_TIME, DEFAULT_SEARCH_RADIUS, null));
        }

    }

    @Nested
    class CreateTransfers {

        private Set<Stop> stops;

        @BeforeEach
        void setUp() {
            stops = new HashSet<>(schedule.getStops().values());
        }

        @Test
        void expectedBehavior() {
            List<TransferGenerator.Transfer> transfers = generator.generateTransfers(stops);
            assertNoSameStopTransfers(transfers);
            assertTransfersGenerated(transfers, "stop1", "stop2");
            assertTransfersGenerated(transfers, "stop1", "stop3");
            assertTransferNotGenerated(transfers, "stop2", "stop3");
        }

        @Test
        void expectedBehavior_withIncreasedSearchRadius() {
            WalkTransferGenerator generator = new WalkTransferGenerator(DEFAULT_CALCULATOR,
                    DEFAULT_MINIMUM_TRANSFER_TIME, DEFAULT_ACCESS_EGRESS_TIME, 1000, spatialIndex);
            List<TransferGenerator.Transfer> transfers = generator.generateTransfers(stops);
            assertNoSameStopTransfers(transfers);
            assertTransfersGenerated(transfers, "stop1", "stop2");
            assertTransfersGenerated(transfers, "stop1", "stop3");
            assertTransfersGenerated(transfers, "stop2", "stop3");
        }

        @Test
        void expectedBehavior_shouldBeMinimumTransferTime() {
            int minimumTransferTime = 2000;
            WalkTransferGenerator highMinTimeGenerator = new WalkTransferGenerator(DEFAULT_CALCULATOR,
                    minimumTransferTime, DEFAULT_ACCESS_EGRESS_TIME, DEFAULT_SEARCH_RADIUS, spatialIndex);
            List<TransferGenerator.Transfer> transfers = highMinTimeGenerator.generateTransfers(stops);
            assertNoSameStopTransfers(transfers);
            assertTransfersGenerated(transfers, "stop1", "stop2");
            assertTransfersGenerated(transfers, "stop1", "stop3");
            assertTransferNotGenerated(transfers, "stop2", "stop3");
            assertTransferNotGenerated(transfers, "stop1", "stop1");
        }
    }

}
