package ch.naviqore.service.impl.transfergenerator;

import ch.naviqore.gtfs.schedule.model.GtfsSchedule;
import ch.naviqore.gtfs.schedule.model.GtfsScheduleBuilder;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class SameStationTransferGeneratorTest {

    @Nested
    class Constructor {
        @Test
        void simpleTransferGenerator() {
            Assertions.assertDoesNotThrow(() -> new SameStationTransferGenerator(120));
        }

        @Test
        void negativeSameStationTransferTime_shouldThrowException() {
            Assertions.assertThrows(IllegalArgumentException.class, () -> new SameStationTransferGenerator(-1));
        }

        @Test
        void zeroSameStationTransferTime_shouldNotThrowException() {
            Assertions.assertDoesNotThrow(() -> new SameStationTransferGenerator(0));
        }
    }

    @Nested
    class CreateTransfers {
        @Test
        void shouldCreateTransfers_withPositiveSameStationTransferTime() {
            GtfsSchedule schedule = getSchedule();
            SameStationTransferGenerator generator = new SameStationTransferGenerator(120);
            List<MinimumTimeTransfer> transfers = generator.generateTransfers(schedule);

            Assertions.assertEquals(2, transfers.size());
            for (MinimumTimeTransfer transfer : transfers) {
                Assertions.assertEquals(transfer.from(), transfer.to());
                Assertions.assertEquals(120, transfer.duration());
            }
        }

        @Test
        void shouldCreateTransfers_withZeroSameStationTransferTime() {
            GtfsSchedule schedule = getSchedule();
            SameStationTransferGenerator generator = new SameStationTransferGenerator(0);
            List<MinimumTimeTransfer> transfers = generator.generateTransfers(schedule);

            Assertions.assertEquals(2, transfers.size());
            for (MinimumTimeTransfer transfer : transfers) {
                Assertions.assertEquals(transfer.from(), transfer.to());
                Assertions.assertEquals(0, transfer.duration());
            }
        }
    }

    static GtfsSchedule getSchedule() {
        GtfsScheduleBuilder builder = GtfsSchedule.builder();
        builder.addStop("stop1", "Zürich, Stadelhofen", "stop1", 47.366542, 8.548384);
        builder.addStop("stop2", "Zürich, Opernhaus", "stop2", 47.365030, 8.547976);
        return builder.build();
    }

}
