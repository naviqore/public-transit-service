package ch.naviqore.service.impl.transfer;

import ch.naviqore.gtfs.schedule.model.GtfsSchedule;
import ch.naviqore.gtfs.schedule.model.GtfsScheduleBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class SameStationTransferGeneratorTest {

    private GtfsSchedule schedule;

    @BeforeEach
    void setUp() {
        GtfsScheduleBuilder builder = GtfsSchedule.builder();
        builder.addStop("stop1", "Zürich, Stadelhofen", "stop1", 47.366542, 8.548384);
        builder.addStop("stop2", "Zürich, Opernhaus", "stop2", 47.365030, 8.547976);
        schedule = builder.build();
    }

    @Nested
    class Constructor {

        @Test
        void shouldNotThrowException_validMinimumTransferTime() {
            assertDoesNotThrow(() -> new SameStationTransferGenerator(120));
        }

        @Test
        void shouldThrowException_negativeSameStationTransferTime() {
            assertThrows(IllegalArgumentException.class, () -> new SameStationTransferGenerator(-1));
        }

        @Test
        void shouldNotThrowException_zeroSameStationTransferTime() {
            assertDoesNotThrow(() -> new SameStationTransferGenerator(0));
        }
    }

    @Nested
    class CreateTransfers {
        @Test
        void shouldCreateTransfers_withPositiveSameStationTransferTime() {
            SameStationTransferGenerator generator = new SameStationTransferGenerator(120);
            List<TransferGenerator.Transfer> transfers = generator.generateTransfers(schedule);

            assertEquals(2, transfers.size());
            for (TransferGenerator.Transfer transfer : transfers) {
                assertEquals(transfer.from(), transfer.to());
                assertEquals(120, transfer.duration());
            }
        }

        @Test
        void shouldCreateTransfers_withZeroSameStationTransferTime() {
            SameStationTransferGenerator generator = new SameStationTransferGenerator(0);
            List<TransferGenerator.Transfer> transfers = generator.generateTransfers(schedule);

            assertEquals(2, transfers.size());
            for (TransferGenerator.Transfer transfer : transfers) {
                assertEquals(transfer.from(), transfer.to());
                assertEquals(0, transfer.duration());
            }
        }
    }

}
