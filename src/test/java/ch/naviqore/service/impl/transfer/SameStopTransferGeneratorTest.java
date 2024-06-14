package ch.naviqore.service.impl.transfer;

import ch.naviqore.gtfs.schedule.model.GtfsSchedule;
import ch.naviqore.gtfs.schedule.model.GtfsScheduleBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class SameStopTransferGeneratorTest {

    @Nested
    class Constructor {
        @Test
        void simpleTransferGenerator() {
            assertDoesNotThrow(() -> new SameStopTransferGenerator(120));
        }

        @Test
        void negativeSameStopTransferTime_shouldThrowException() {
            assertThrows(IllegalArgumentException.class, () -> new SameStopTransferGenerator(-1));
        }

        @Test
        void zeroSameStopTransferTime_shouldNotThrowException() {
            assertDoesNotThrow(() -> new SameStopTransferGenerator(0));
        }
    }

    @Nested
    class CreateTransfers {

        private GtfsSchedule schedule;

        @BeforeEach
        void setUp() {
            GtfsScheduleBuilder builder = GtfsSchedule.builder();
            builder.addStop("stop1", "Zürich, Stadelhofen", 47.366542, 8.548384);
            builder.addStop("stop2", "Zürich, Opernhaus", 47.365030, 8.547976);
            schedule = builder.build();
        }

        @Test
        void shouldCreateTransfers_withPositiveSameStopTransferTime() {
            SameStopTransferGenerator generator = new SameStopTransferGenerator(120);
            List<TransferGenerator.Transfer> transfers = generator.generateTransfers(schedule);

            assertEquals(2, transfers.size());
            for (TransferGenerator.Transfer transfer : transfers) {
                assertEquals(transfer.from(), transfer.to());
                assertEquals(120, transfer.duration());
            }
        }

        @Test
        void shouldCreateTransfers_withZeroSameStopTransferTime() {
            SameStopTransferGenerator generator = new SameStopTransferGenerator(0);
            List<TransferGenerator.Transfer> transfers = generator.generateTransfers(schedule);

            assertEquals(2, transfers.size());
            for (TransferGenerator.Transfer transfer : transfers) {
                assertEquals(transfer.from(), transfer.to());
                assertEquals(0, transfer.duration());
            }
        }
    }
}
