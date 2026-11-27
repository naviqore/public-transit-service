package org.naviqore.gtfs.schedule;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.naviqore.gtfs.schedule.model.GtfsSchedule;
import org.naviqore.gtfs.schedule.model.Route;
import org.naviqore.gtfs.schedule.model.Stop;
import org.naviqore.gtfs.schedule.model.Transfer;
import org.naviqore.gtfs.schedule.model.Trip;
import org.naviqore.gtfs.schedule.type.LocationType;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The transfers of transfers.txt: a row applies to all trips between its stops, or to the routes or trips it names, and
 * a row of trips that the rider stays on board of may leave out its stops. A row that cannot be applied is skipped with
 * a warning.
 */
class GtfsScheduleReaderTransfersIT {

    private static final GtfsScheduleDataset GTFS_SCHEDULE_DATASET = GtfsScheduleDataset.SAMPLE_FEED_1;
    private static final String HEADER = "from_stop_id,to_stop_id,from_route_id,to_route_id,from_trip_id,to_trip_id,transfer_type,min_transfer_time\n";

    private static GtfsSchedule read(Path directory) throws IOException {
        return new GtfsScheduleReader().read(directory.toString());
    }

    /**
     * Reads a feed, and the warnings of reading it.
     */
    private static Reading readWarning(Path directory) throws IOException {
        List<Logger> loggers = Stream.of(GtfsScheduleParser.class, GtfsScheduleReader.class)
                .map(type -> (Logger) LoggerFactory.getLogger(type))
                .toList();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        loggers.forEach(logger -> logger.addAppender(appender));
        try {
            GtfsSchedule schedule = read(directory);
            return new Reading(schedule, appender.list.stream()
                    .filter(event -> event.getLevel() == Level.WARN)
                    .map(ILoggingEvent::getFormattedMessage)
                    .toList());
        } finally {
            loggers.forEach(logger -> logger.detachAppender(appender));
        }
    }

    private static String describe(Transfer transfer) {
        String description = String.format("%s:%s:%s", transfer.getToStop().getId(), transfer.getTransferType(),
                transfer.getMinTransferTime().orElse(null));
        if (transfer.getFromRoute().isPresent() || transfer.getToRoute().isPresent()) {
            description += String.format(" routes %s>%s", transfer.getFromRoute().map(Route::getId).orElse(""),
                    transfer.getToRoute().map(Route::getId).orElse(""));
        }
        if (transfer.getFromTrip().isPresent() || transfer.getToTrip().isPresent()) {
            description += String.format(" trips %s>%s", transfer.getFromTrip().map(Trip::getId).orElse(""),
                    transfer.getToTrip().map(Trip::getId).orElse(""));
        }
        return description;
    }

    /**
     * The transfers from a stop, each as the stop it leads to, its type, its minimum time, and the routes and trips it
     * applies to.
     */
    private static List<String> transfers(GtfsSchedule schedule, String stopId) {
        return schedule.getStops()
                .get(stopId)
                .getTransfers()
                .stream()
                .map(GtfsScheduleReaderTransfersIT::describe)
                .toList();
    }

    /**
     * Writes the stops of the sample feed, where BULLFROG, where the trips call, is a platform of the station
     * BULLFROG_STATION, and the rows given after them.
     */
    private static void writeStopsOfBullfrogStation(Path directory, String rows) throws IOException {
        Files.writeString(directory.resolve("stops.txt"), """
                stop_id,stop_name,stop_lat,stop_lon,location_type,parent_station
                FUR_CREEK_RES,Furnace Creek Resort,36.425288,-117.133162,,
                BEATTY_AIRPORT,Nye County Airport,36.868446,-116.784582,,
                BULLFROG_STATION,Bullfrog,36.88108,-116.81797,1,
                BULLFROG,Bullfrog,36.88108,-116.81797,0,BULLFROG_STATION
                STAGECOACH,Stagecoach Hotel & Casino,36.915682,-116.751677,,
                NADAV,North Ave / D Ave N,36.914893,-116.76821,,
                NANAA,North Ave / N A Ave,36.914944,-116.761472,,
                DADAN,Doing Ave / D Ave N,36.909489,-116.768242,,
                EMSI,E Main St / S Irving St,36.905697,-116.76218,,
                AMV,Amargosa Valley,36.641496,-116.40094,,
                """ + rows);
    }

    @Test
    void rowsForParticularRoutesOrTrips_areReadWithThem(@TempDir Path tempDir) throws IOException {
        Path directory = GTFS_SCHEDULE_DATASET.getUnzipped(tempDir).toPath();
        // EMSI to STAGECOACH: 420 s for all trips, and 300 s from trip CITY1 to trip STBA; STAGECOACH to EMSI: 60 s
        // from route AB to route BFC, and a recommended transfer for all trips; BULLFROG: staying on board from trip
        // AB1 to trip AB2
        Files.writeString(directory.resolve("transfers.txt"), HEADER + """
                EMSI,STAGECOACH,,,,,2,420
                EMSI,STAGECOACH,,,CITY1,STBA,2,300
                STAGECOACH,EMSI,AB,BFC,,,2,60
                STAGECOACH,EMSI,,,,,,
                BULLFROG,BULLFROG,,,AB1,AB2,4,
                """);

        GtfsSchedule schedule = read(directory);

        assertThat(transfers(schedule, "EMSI")).containsExactly("STAGECOACH:MINIMUM_TIME:420",
                "STAGECOACH:MINIMUM_TIME:300 trips CITY1>STBA");
        assertThat(transfers(schedule, "STAGECOACH")).containsExactly("EMSI:MINIMUM_TIME:60 routes AB>BFC",
                "EMSI:RECOMMENDED:null");
        assertThat(transfers(schedule, "BULLFROG")).containsExactly("BULLFROG:IN_SEAT:null trips AB1>AB2");
    }

    @ParameterizedTest
    @ValueSource(strings = {",,,,AB1,AB2,4,", "BULLFROG,,,,AB1,AB2,4,", ",BULLFROG,,,AB1,AB2,4,"})
    void rowsOfTripsStayingOnBoard_mayLeaveOutTheirStops(String row, @TempDir Path tempDir) throws IOException {
        Path directory = GTFS_SCHEDULE_DATASET.getUnzipped(tempDir).toPath();
        // from the last stop of AB1 to the first stop of AB2, both BULLFROG; their stop times are in reverse order
        Path stopTimes = directory.resolve("stop_times.txt");
        List<String> lines = Files.readAllLines(stopTimes);
        Files.write(stopTimes, Stream.concat(lines.stream().filter(line -> !line.startsWith("AB")),
                lines.stream().filter(line -> line.startsWith("AB")).toList().reversed().stream()).toList());
        Files.writeString(directory.resolve("transfers.txt"), HEADER + row + "\n");

        assertThat(transfers(read(directory), "BULLFROG")).containsExactly("BULLFROG:IN_SEAT:null trips AB1>AB2");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            // a stop, a route and a trip that do not exist, and a trip of another route than the one named
            "EMSI,NONE,,,,,2,300                | [from_stop_id=EMSI, to_stop_id=NONE]: Stop NONE does not exist",
            "EMSI,STAGECOACH,NONE,,,,2,300      | Route NONE does not exist",
            "EMSI,STAGECOACH,,,CITY1,NONE,2,300 | Trip NONE does not exist",
            "EMSI,STAGECOACH,AB,,CITY1,,2,300   | Trip CITY1 does not belong to route AB",
            // a trip that does not call at the stop of its side: STBA leaves STAGECOACH for BEATTY_AIRPORT
            "EMSI,STAGECOACH,,,STBA,CITY1,2,300 | Trip STBA does not call at stop EMSI",
            // a type that does not exist, and a minimum time without its time, negative or not a number
            "EMSI,STAGECOACH,,,,,6,             | No transfer type with code 6",
            "EMSI,STAGECOACH,,,CITY1,STBA,2,    | Minimal transfer time is not present",
            "EMSI,STAGECOACH,,,,,2,-60          | Minimal transfer time -60 is negative",
            "EMSI,STAGECOACH,,,,,2,soon         | For input string: \"soon\"",
            // trips staying on board that are not named, and no stop for trips that do not stay on board
            "EMSI,STAGECOACH,,,,,4,             | does not name both trips",
            "EMSI,,,,,,0,                       | does not name both stops",
            ",STAGECOACH,,,CITY1,STBA,2,300     | [to_stop_id=STAGECOACH, from_trip_id=CITY1, to_trip_id=STBA]: "
                    + "Transfer of type MINIMUM_TIME does not name both stops"})
    void anInvalidRow_isSkippedWithAWarning(String row, String reason, @TempDir Path tempDir) throws IOException {
        Path directory = GTFS_SCHEDULE_DATASET.getUnzipped(tempDir).toPath();
        Files.writeString(directory.resolve("transfers.txt"), HEADER + row + "\nEMSI,STAGECOACH,,,,,2,420\n");

        Reading reading = readWarning(directory);

        assertThat(transfers(reading.schedule(), "EMSI")).containsExactly("STAGECOACH:MINIMUM_TIME:420");
        assertThat(reading.warnings()).satisfiesExactly(
                warning -> assertThat(warning).startsWith("Skipping invalid transfer [").contains(reason),
                warning -> assertThat(warning).isEqualTo("Skipped 1 of 2 records of transfers.txt"));
    }

    @Test
    void rowsAtAStopTimeWithoutTimes_areSkipped(@TempDir Path tempDir) throws IOException {
        Path directory = GTFS_SCHEDULE_DATASET.getUnzipped(tempDir).toPath();
        // STBA passes NANAA, which has no times and is left out of the trip; CITY2 calls at NANAA at 06:51
        Path stopTimes = directory.resolve("stop_times.txt");
        Files.writeString(stopTimes, Files.readString(stopTimes)
                .replace("STBA,6:20:00,6:20:00,BEATTY_AIRPORT,2,,,,",
                        "STBA,,,NANAA,2,,,,\nSTBA,6:20:00,6:20:00,BEATTY_AIRPORT,3,,,,"));
        Files.writeString(directory.resolve("transfers.txt"), HEADER + "NANAA,NANAA,,,STBA,CITY2,2,120\n");

        Reading reading = readWarning(directory);

        assertThat(reading.schedule().getTrips().get("STBA").getStopTimes()).extracting(
                stopTime -> stopTime.stop().getId()).containsExactly("STAGECOACH", "BEATTY_AIRPORT");
        assertThat(transfers(reading.schedule(), "NANAA")).isEmpty();
        assertThat(reading.warnings()).contains(
                "Skipping invalid transfer [from_stop_id=NANAA, to_stop_id=NANAA, from_trip_id=STBA, "
                        + "to_trip_id=CITY2]: Trip STBA does not call at stop NANAA",
                "Skipped 1 of 1 records of transfers.txt");
    }

    @Test
    void rowsOfTripsStayingOnBoard_nameStopsNotStations(@TempDir Path tempDir) throws IOException {
        Path directory = GTFS_SCHEDULE_DATASET.getUnzipped(tempDir).toPath();
        writeStopsOfBullfrogStation(directory, "");

        // a row of trips at a station applies at its stops, where they call
        Path transfers = directory.resolve("transfers.txt");
        Files.writeString(transfers, HEADER + "BULLFROG_STATION,BULLFROG_STATION,,,AB1,BFC1,2,300\n");
        assertThat(transfers(read(directory), "BULLFROG_STATION")).containsExactly(
                "BULLFROG_STATION:MINIMUM_TIME:300 trips AB1>BFC1");

        // a row of trips staying on board at a station, and one at a boarding area, are skipped
        writeStopsOfBullfrogStation(directory, "BULLFROG_FRONT,Bullfrog front,36.88108,-116.81797,4,BULLFROG\n");
        Files.writeString(transfers, HEADER + "BULLFROG_STATION,BULLFROG_STATION,,,AB1,AB2,4,\n"
                + "BULLFROG_FRONT,BULLFROG_FRONT,,,AB1,BFC1,2,300\n");
        Reading reading = readWarning(directory);
        assertThat(transfers(reading.schedule(), "BULLFROG_STATION")).isEmpty();
        assertThat(transfers(reading.schedule(), "BULLFROG_FRONT")).isEmpty();
        assertThat(reading.warnings()).containsExactly(
                "Skipping invalid transfer [from_stop_id=BULLFROG_STATION, to_stop_id=BULLFROG_STATION, "
                        + "from_trip_id=AB1, to_trip_id=AB2]: Transfer of type IN_SEAT links trips at station "
                        + "BULLFROG_STATION, not at its stops",
                "Skipping invalid transfer [from_stop_id=BULLFROG_FRONT, to_stop_id=BULLFROG_FRONT, "
                        + "from_trip_id=AB1, to_trip_id=BFC1]: Transfer names stop BULLFROG_FRONT of location type "
                        + "BOARDING_AREA, which is neither a stop nor a station",
                "Skipped 2 of 2 records of transfers.txt");
    }

    @Test
    void rowsOfTripsStayingOnBoard_atAPlatformWithABoardingArea_areApplied(@TempDir Path tempDir) throws IOException {
        Path directory = GTFS_SCHEDULE_DATASET.getUnzipped(tempDir).toPath();
        // the boarding area of the platform BULLFROG does not make it a station
        writeStopsOfBullfrogStation(directory, "BULLFROG_FRONT,Bullfrog front,36.88108,-116.81797,4,BULLFROG\n");
        Files.writeString(directory.resolve("transfers.txt"), HEADER + "BULLFROG,BULLFROG,,,AB1,AB2,4,\n");

        GtfsSchedule schedule = read(directory);

        assertThat(transfers(schedule, "BULLFROG")).containsExactly("BULLFROG:IN_SEAT:null trips AB1>AB2");
        assertThat(schedule.getStops().get("BULLFROG").getChildren()).extracting(Stop::getId)
                .containsExactly("BULLFROG_FRONT");
        assertThat(Stream.of("BULLFROG_STATION", "BULLFROG", "BULLFROG_FRONT", "EMSI")
                .map(id -> schedule.getStops().get(id).getLocationType())).containsExactly(LocationType.STATION,
                LocationType.STOP, LocationType.BOARDING_AREA, LocationType.STOP);
    }

    @Test
    void rowsWithoutRouteAndTripFields_areReadForAllTrips(@TempDir Path tempDir) throws IOException {
        // the sample feed's transfers.txt, which has no route and trip fields
        GtfsSchedule schedule = read(GTFS_SCHEDULE_DATASET.getUnzipped(tempDir).toPath());

        assertThat(transfers(schedule, "EMSI")).containsExactly("STAGECOACH:MINIMUM_TIME:120");
        assertThat(transfers(schedule, "STAGECOACH")).containsExactly("EMSI:MINIMUM_TIME:180");
        assertThat(transfers(schedule, "FUR_CREEK_RES")).containsExactly("BULLFROG:NOT_POSSIBLE:null");
    }

    private record Reading(GtfsSchedule schedule, List<String> warnings) {
    }

}
