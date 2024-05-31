package ch.naviqore.service.gtfsraptor;

import ch.naviqore.gtfs.schedule.model.GtfsSchedule;
import ch.naviqore.gtfs.schedule.model.GtfsScheduleBuilder;
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

    @Nested
    class Constructor {

        @Test
        void simpleTransferGenerator() {
            assertDoesNotThrow(WalkTransferGeneratorTest::getDefaultWalkTransferGenerator);
        }

        @Test
        void nullWalkCalculator_shouldThrowException() {
            assertThrows(IllegalArgumentException.class,
                    () -> new WalkTransferGenerator(null, defaultMinimumTransferTime, defaultMaxWalkDistance));
        }

        @Test
        void negativeSameStationTransferTime_shouldThrowException() {
            assertThrows(IllegalArgumentException.class,
                    () -> new WalkTransferGenerator(defaultCalculator, -1, defaultMaxWalkDistance));
        }

        @Test
        void zeroSameStationTransferTime_shouldNotThrowException() {
            assertDoesNotThrow(() -> new WalkTransferGenerator(defaultCalculator, 0, defaultMaxWalkDistance));
        }

        @Test
        void negativeMaxWalkDistance_shouldThrowException() {
            assertThrows(IllegalArgumentException.class,
                    () -> new WalkTransferGenerator(defaultCalculator, defaultMinimumTransferTime, -1));
        }

        @Test
        void zeroMaxWalkDistance_shouldThrowException() {
            assertThrows(IllegalArgumentException.class,
                    () -> new WalkTransferGenerator(defaultCalculator, defaultMinimumTransferTime, 0));
        }

    }

    @Nested
    class CreateTransfers {

        @Test
        void expectedBehavior() {
            WalkTransferGenerator generator = getDefaultWalkTransferGenerator();
            GtfsSchedule schedule = getSchedule();

            List<MinimumTimeTransfer> transfers = generator.generateTransfers(schedule);

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
                    1000);

            List<MinimumTimeTransfer> transfers = generator.generateTransfers(schedule);

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
                    defaultMaxWalkDistance);

            List<MinimumTimeTransfer> transfers = generator.generateTransfers(schedule);

            assertNoSameStationTransfers(transfers);
            // all transfers from / to Stadelhofen should be within search radius
            assertTransfersGenerated(transfers, "stop1", "stop2", minimumTransferTime, true);
            assertTransfersGenerated(transfers, "stop1", "stop3", minimumTransferTime, true);

            // transfer between Kunsthaus and Opernhaus should not be created
            assertTransferNotGenerated(transfers, "stop2", "stop3");
        }
    }

    static void assertNoSameStationTransfers(List<MinimumTimeTransfer> transfers) {
        for (MinimumTimeTransfer transfer : transfers) {
            assertNotEquals(transfer.from(), transfer.to());
        }
    }

    public void assertTransfersGenerated(List<MinimumTimeTransfer> transfers, String fromStopId, String toStopId,
                                         int minimumTransferTime) {
        assertTransfersGenerated(transfers, fromStopId, toStopId, minimumTransferTime, false);
    }

    public void assertTransfersGenerated(List<MinimumTimeTransfer> transfers, String fromStopId, String toStopId,
                                         int minimumTransferTime, boolean shouldBeMinimumTransferTime) {
        try {
            MinimumTimeTransfer transfer1 = transferWasCreated(transfers, fromStopId, toStopId);
            MinimumTimeTransfer transfer2 = transferWasCreated(transfers, toStopId, fromStopId);

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

    public void assertTransferNotGenerated(List<MinimumTimeTransfer> transfers, String fromStopId, String toStopId) {
        assertThrows(NoSuchElementException.class, () -> transferWasCreated(transfers, fromStopId, toStopId));
        assertThrows(NoSuchElementException.class, () -> transferWasCreated(transfers, toStopId, fromStopId));
    }

    static MinimumTimeTransfer transferWasCreated(List<MinimumTimeTransfer> transfers, String fromStopId,
                                                  String toStopId) {
        for (MinimumTimeTransfer transfer : transfers) {
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

    static WalkTransferGenerator getDefaultWalkTransferGenerator() {
        return new WalkTransferGenerator(defaultCalculator, defaultMinimumTransferTime, defaultMaxWalkDistance);
    }

    record StopData(String id, String name, double lat, double lon) {

    }

}
