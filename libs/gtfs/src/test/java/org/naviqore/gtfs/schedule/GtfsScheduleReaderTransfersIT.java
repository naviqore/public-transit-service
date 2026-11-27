package org.naviqore.gtfs.schedule;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.naviqore.gtfs.schedule.model.GtfsSchedule;
import org.naviqore.gtfs.schedule.model.Transfer;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The transfers of transfers.txt: a transfer of the schedule applies to all trips between its stops, so the rows for
 * all trips are read and the rows for particular routes or trips are left out.
 */
class GtfsScheduleReaderTransfersIT {

    private static final GtfsScheduleDataset GTFS_SCHEDULE_DATASET = GtfsScheduleDataset.SAMPLE_FEED_1;
    private static final String LEFT_OUT = "Left out rows of transfers.txt for particular routes or trips: ";

    /**
     * Reads a feed, keeping the messages the reader logged at the level info.
     */
    private static Reading read(Path directory) throws IOException {
        Logger logger = (Logger) LoggerFactory.getLogger(GtfsScheduleReader.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            GtfsSchedule schedule = new GtfsScheduleReader().read(directory.toString());
            return new Reading(schedule, appender.list.stream()
                    .filter(event -> event.getLevel() == Level.INFO)
                    .map(ILoggingEvent::getFormattedMessage)
                    .toList());
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void rowsForParticularRoutesOrTrips_areLeftOut(@TempDir Path tempDir) throws IOException {
        Path directory = GTFS_SCHEDULE_DATASET.getUnzipped(tempDir).toPath();
        // EMSI to STAGECOACH: 420 s for all trips, and 300 s from trip STBA to trip CITY1 alone, the later row, which
        // used to set the time for all trips; STAGECOACH to EMSI: 60 s from route AB to route BFC alone; BULLFROG to
        // FUR_CREEK_RES: staying on board from trip AB1 to trip AB2
        Files.writeString(directory.resolve("transfers.txt"), """
                from_stop_id,to_stop_id,from_route_id,to_route_id,from_trip_id,to_trip_id,transfer_type,min_transfer_time
                EMSI,STAGECOACH,,,,,2,420
                EMSI,STAGECOACH,,,STBA,CITY1,2,300
                STAGECOACH,EMSI,AB,BFC,,,2,60
                BULLFROG,FUR_CREEK_RES,,,AB1,AB2,4,
                """);

        Reading reading = read(directory);

        assertThat(reading.transfers("EMSI")).containsExactly("STAGECOACH:MINIMUM_TIME:420");
        assertThat(reading.transfers("STAGECOACH")).isEmpty();
        assertThat(reading.transfers("BULLFROG")).isEmpty();
        assertThat(reading.info()).contains(LEFT_OUT + 3);
    }

    @Test
    void rowsForParticularTrips_areLeftOut_withoutRouteFields(@TempDir Path tempDir) throws IOException {
        Path directory = GTFS_SCHEDULE_DATASET.getUnzipped(tempDir).toPath();
        Files.writeString(directory.resolve("transfers.txt"), """
                from_stop_id,to_stop_id,from_trip_id,to_trip_id,transfer_type,min_transfer_time
                EMSI,STAGECOACH,,,2,420
                EMSI,STAGECOACH,STBA,CITY1,2,300
                """);

        Reading reading = read(directory);

        assertThat(reading.transfers("EMSI")).containsExactly("STAGECOACH:MINIMUM_TIME:420");
        assertThat(reading.info()).contains(LEFT_OUT + 1);
    }

    @Test
    void rowsWithoutRouteAndTripFields_areReadForAllTrips(@TempDir Path tempDir) throws IOException {
        // the sample feed's transfers.txt, which has no route and trip fields
        Reading reading = read(GTFS_SCHEDULE_DATASET.getUnzipped(tempDir).toPath());

        assertThat(reading.transfers("EMSI")).containsExactly("STAGECOACH:MINIMUM_TIME:120");
        assertThat(reading.transfers("STAGECOACH")).containsExactly("EMSI:MINIMUM_TIME:180");
        assertThat(reading.transfers("FUR_CREEK_RES")).containsExactly("BULLFROG:NOT_POSSIBLE:null");
        assertThat(reading.info()).noneMatch(message -> message.startsWith(LEFT_OUT));
    }

    /**
     * A schedule read, and the messages the reader logged at the level info.
     */
    private record Reading(GtfsSchedule schedule, List<String> info) {

        private static String describe(Transfer transfer) {
            return String.format("%s:%s:%s", transfer.getToStop().getId(), transfer.getTransferType(),
                    transfer.getMinTransferTime().orElse(null));
        }

        /**
         * The transfers from a stop, each as the stop it leads to, its type and its minimum time.
         */
        List<String> transfers(String stopId) {
            return schedule.getStops().get(stopId).getTransfers().stream().map(Reading::describe).toList();
        }
    }
}
