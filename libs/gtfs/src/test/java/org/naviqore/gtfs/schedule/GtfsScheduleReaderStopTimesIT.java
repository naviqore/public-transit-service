package org.naviqore.gtfs.schedule;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.naviqore.gtfs.schedule.model.GtfsSchedule;
import org.naviqore.gtfs.schedule.model.StopTime;
import org.naviqore.timetable.type.PickupDropOffType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The pickup and drop-off types of stop_times.txt, which are regular where a row leaves them empty or out.
 */
class GtfsScheduleReaderStopTimesIT {

    @Test
    void pickupAndDropOffTypes_areRead(@TempDir Path tempDir) throws IOException {
        Path directory = GtfsScheduleDataset.SAMPLE_FEED_1.getUnzipped(tempDir).toPath();
        // the sample feed misnames the drop-off type; trip STBA does not set down at its first stop, and does not pick up
        // at its last
        Path stopTimesFile = directory.resolve("stop_times.txt");
        Files.writeString(stopTimesFile, Files.readString(stopTimesFile)
                .replace("drop_off_time", "drop_off_type")
                .replace("STBA,6:00:00,6:00:00,STAGECOACH,1,,,,", "STBA,6:00:00,6:00:00,STAGECOACH,1,,0,1,")
                .replace("STBA,6:20:00,6:20:00,BEATTY_AIRPORT,2,,,,", "STBA,6:20:00,6:20:00,BEATTY_AIRPORT,2,,1,,"));

        GtfsSchedule schedule = new GtfsScheduleReader().read(directory.toString());

        List<StopTime> stopTimes = schedule.getTrips().get("STBA").getStopTimes();
        assertThat(stopTimes.getFirst().pickupType()).isEqualTo(PickupDropOffType.REGULAR);
        assertThat(stopTimes.getFirst().dropOffType()).isEqualTo(PickupDropOffType.NOT_AVAILABLE);
        assertThat(stopTimes.getLast().pickupType()).isEqualTo(PickupDropOffType.NOT_AVAILABLE);
        assertThat(stopTimes.getLast().dropOffType()).isEqualTo(PickupDropOffType.REGULAR);

        // the rows of trip AB1 leave out their last fields, which are empty
        assertThat(schedule.getTrips().get("AB1").getStopTimes()).hasSize(2)
                .allMatch(stopTime -> stopTime.pickupType() == PickupDropOffType.REGULAR)
                .allMatch(stopTime -> stopTime.dropOffType() == PickupDropOffType.REGULAR);
    }

}
