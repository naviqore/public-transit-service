package org.naviqore.gtfs.schedule;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.apache.commons.csv.*;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.naviqore.gtfs.schedule.model.*;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration test for {@link GtfsScheduleReader}
 * <p>
 * Read GTFS schedule data from a ZIP file and a directory.
 */
class GtfsScheduleReaderIT {

    private static final GtfsScheduleDataset GTFS_SCHEDULE_DATASET = GtfsScheduleDataset.SAMPLE_FEED_1;
    private GtfsScheduleReader gtfsScheduleReader;

    /**
     * The stops, routes, trips with their stop times, calendars with their dates, agencies and transfers of a schedule,
     * each entity on a line, in the order of their ids, and the lists they hold in their own order.
     */
    private static List<String> dump(GtfsSchedule schedule) {
        List<String> lines = new ArrayList<>();
        schedule.getAgencies()
                .values()
                .stream()
                .sorted(Comparator.comparing(Agency::agency))
                .forEach(agency -> lines.add("agency " + agency));
        schedule.getCalendars()
                .values()
                .stream()
                .sorted(Comparator.comparing(Calendar::getId))
                .forEach(calendar -> lines.add(
                        "calendar " + calendar + " dates " + new TreeMap<>(calendar.getCalendarDates()).values()
                                .stream()
                                .map(date -> date.date() + ":" + date.type())
                                .toList() + " trips " + calendar.getTrips().stream().map(Trip::getId).toList()));
        schedule.getStops()
                .values()
                .stream()
                .sorted(Comparator.comparing(Stop::getId))
                .forEach(stop -> lines.add(
                        String.join(" | ", "stop " + stop.getId(), stop.getName(), stop.getCoordinate().toString(),
                                stop.getParent().map(Stop::getId).orElse("-"), stop.getWheelchairBoarding().toString(),
                                stop.getChildren().stream().map(Stop::getId).toList().toString(), stop.getTransfers()
                                        .stream()
                                        .map(transfer -> transfer.getToStop()
                                                .getId() + ":" + transfer.getTransferType() + ":" + transfer.getMinTransferTime()
                                                .orElse(null))
                                        .toList()
                                        .toString(), String.valueOf(stop.getStopTimes().size()))));
        schedule.getRoutes()
                .values()
                .stream()
                .sorted(Comparator.comparing(Route::getId))
                .forEach(route -> lines.add(
                        String.join(" | ", "route " + route.getId(), route.getAgency().agency(), route.getShortName(),
                                route.getLongName(), route.getType().toString(),
                                route.getTrips().stream().map(Trip::getId).toList().toString())));
        schedule.getTrips()
                .values()
                .stream()
                .sorted(Comparator.comparing(Trip::getId))
                .forEach(trip -> lines.add(
                        String.join(" | ", "trip " + trip.getId(), trip.getRoute().getId(), trip.getCalendar().getId(),
                                trip.getHeadSign(), trip.getWheelchairAccessible().toString(),
                                trip.getBikesAllowed().toString(), trip.getStopTimes()
                                        .stream()
                                        .map(stopTime -> stopTime.stop()
                                                .getId() + "@" + stopTime.arrival() + "-" + stopTime.departure())
                                        .toList()
                                        .toString())));

        return lines;
    }

    @BeforeEach
    void setUp() {
        gtfsScheduleReader = new GtfsScheduleReader();
    }

    @Test
    void shouldReadFromZipFile(@TempDir Path tempDir) throws IOException {
        Path zipFile = GTFS_SCHEDULE_DATASET.getZip(tempDir).toPath();
        GtfsSchedule schedule = gtfsScheduleReader.read(zipFile.toAbsolutePath().toString());
        assertScheduleSizes(schedule);
    }

    @Test
    void shouldReadFromDirectory(@TempDir Path tempDir) throws IOException {
        Path unzippedDir = GTFS_SCHEDULE_DATASET.getUnzipped(tempDir).toPath();
        GtfsSchedule schedule = gtfsScheduleReader.read(unzippedDir.toAbsolutePath().toString());
        assertScheduleSizes(schedule);
    }

    @Test
    void shouldRead_withoutCalendarFile(@TempDir Path tempDir) throws IOException {
        Path unzippedDir = GTFS_SCHEDULE_DATASET.getUnzipped(tempDir).toPath();
        Files.deleteIfExists(unzippedDir.resolve("calendar.txt"));

        GtfsSchedule schedule = gtfsScheduleReader.read(unzippedDir.toString());
        assertScheduleSizes(schedule, false);

        // re-run with zipped directory
        Path zipFile = tempDir.resolve("gtfs_schedule_no_calendar.zip");
        zipDirectory(unzippedDir, zipFile);
        schedule = gtfsScheduleReader.read(zipFile.toString());
        assertScheduleSizes(schedule, false);
    }

    @Test
    void shouldRead_withoutCalendarDatesFile(@TempDir Path tempDir) throws IOException {
        Path unzippedDir = GTFS_SCHEDULE_DATASET.getUnzipped(tempDir).toPath();
        Files.deleteIfExists(unzippedDir.resolve("calendar_dates.txt"));

        GtfsSchedule schedule = gtfsScheduleReader.read(unzippedDir.toString());
        assertScheduleSizes(schedule);

        Path zipFile = tempDir.resolve("gtfs_schedule_no_calendar_dates.zip");
        zipDirectory(unzippedDir, zipFile);
        schedule = gtfsScheduleReader.read(zipFile.toString());
        assertScheduleSizes(schedule);
    }

    @Test
    void shouldRead_withoutTransfersFile(@TempDir Path tempDir) throws IOException {
        Path unzippedDir = GTFS_SCHEDULE_DATASET.getUnzipped(tempDir).toPath();
        Files.deleteIfExists(unzippedDir.resolve("transfers.txt"));

        GtfsSchedule schedule = gtfsScheduleReader.read(unzippedDir.toString());
        assertScheduleSizes(schedule);

        Path zipFile = tempDir.resolve("gtfs_schedule_no_transfers.zip");
        zipDirectory(unzippedDir, zipFile);
        schedule = gtfsScheduleReader.read(zipFile.toString());
        assertScheduleSizes(schedule);
    }

    @Test
    void shouldNotReadFromDirectory_withoutCalendarAndCalendarDatesFiles(@TempDir Path tempDir) throws IOException {
        Path unzippedDir = GTFS_SCHEDULE_DATASET.getUnzipped(tempDir).toPath();
        Files.deleteIfExists(unzippedDir.resolve("calendar.txt"));
        Files.deleteIfExists(unzippedDir.resolve("calendar_dates.txt"));

        assertThatThrownBy(() -> gtfsScheduleReader.read(unzippedDir.toString())).isInstanceOf(
                FileNotFoundException.class).hasMessageContaining("Conditional requirement not met:");

        Path zipFile = tempDir.resolve("gtfs_schedule_failing.zip");
        zipDirectory(unzippedDir, zipFile);
        assertThatThrownBy(() -> gtfsScheduleReader.read(zipFile.toString())).isInstanceOf(FileNotFoundException.class)
                .hasMessageContaining("Conditional requirement not met:");
    }

    @ParameterizedTest
    @ValueSource(strings = {"agency.txt", "stops.txt", "routes.txt", "trips.txt", "stop_times.txt"})
    void shouldNotRead_withoutRequiredFile(String missingFile, @TempDir Path tempDir) throws IOException {
        Path unzippedDir = GTFS_SCHEDULE_DATASET.getUnzipped(tempDir).toPath();
        Files.delete(unzippedDir.resolve(missingFile));

        // check directory
        assertThatThrownBy(() -> gtfsScheduleReader.read(unzippedDir.toString())).isInstanceOf(
                FileNotFoundException.class).hasMessageContaining("Required GTFS CSV file");

        // check zip
        Path zipFile = tempDir.resolve("gtfs_missing_" + missingFile + ".zip");
        zipDirectory(unzippedDir, zipFile);
        assertThatThrownBy(() -> gtfsScheduleReader.read(zipFile.toString())).isInstanceOf(FileNotFoundException.class)
                .hasMessageContaining("Required GTFS CSV file");
    }

    private void assertScheduleSizes(GtfsSchedule schedule) {
        assertScheduleSizes(schedule, true);
    }

    private void assertScheduleSizes(GtfsSchedule schedule, boolean withCalendarTextFile) {
        assertThat(schedule.getAgencies()).as("Agencies").hasSize(1);
        assertThat(schedule.getRoutes()).as("Routes").hasSize(5);
        assertThat(schedule.getStops()).as("Stops").hasSize(9);
        assertThat(schedule.getTrips()).as("Trips").hasSize(withCalendarTextFile ? 11 : 7);
    }

    private void zipDirectory(Path sourcePath, Path zipFilePath) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipFilePath))) {
            Files.walkFileTree(sourcePath, new SimpleFileVisitor<>() {
                @Override
                public @NonNull FileVisitResult visitFile(@NonNull Path file,
                                                          @NonNull BasicFileAttributes attrs) throws IOException {
                    String entryName = sourcePath.relativize(file).toString().replace("\\", "/");
                    zos.putNextEntry(new ZipEntry(entryName));
                    Files.copy(file, zos);
                    zos.closeEntry();

                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    /**
     * A file is read in blocks parsed concurrently; the schedule is the one read with each file in one piece.
     */
    @Nested
    class InBlocks {

        private static final int CONCURRENCY = 4;

        private static GtfsSchedule readInOnePiece(String path) throws IOException {
            return new GtfsScheduleReader(Integer.MAX_VALUE, 1).read(path);
        }

        /**
         * Rewrites the stops of a feed with every value quoted, and their names with a comma, quotes and a line break.
         */
        private static void quoteStops(Path directory) throws IOException {
            Path stops = directory.resolve("stops.txt");
            List<CSVRecord> records;
            List<String> header;
            try (Reader reader = Files.newBufferedReader(stops);
                 CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().get().parse(reader)) {
                header = parser.getHeaderNames();
                records = parser.getRecords();
            }

            CSVFormat quoted = CSVFormat.DEFAULT.builder()
                    .setQuoteMode(QuoteMode.ALL)
                    .setHeader(header.toArray(String[]::new))
                    .get();
            try (Writer writer = Files.newBufferedWriter(stops); CSVPrinter printer = new CSVPrinter(writer, quoted)) {
                for (CSVRecord record : records) {
                    List<String> values = new ArrayList<>(record.toList());
                    int name = header.indexOf("stop_name");
                    values.set(name, values.get(name) + ", \"quoted\"\nand broken");
                    printer.printRecord(values);
                }
            }
        }

        /**
         * Reads a feed, and returns the warnings the parser logged while reading it.
         */
        private static List<String> warningsReading(GtfsScheduleReader reader, Path directory) throws IOException {
            Logger logger = (Logger) LoggerFactory.getLogger(GtfsScheduleParser.class);
            ListAppender<ILoggingEvent> appender = new ListAppender<>();
            appender.start();
            logger.addAppender(appender);
            try {
                reader.read(directory.toString());
            } finally {
                logger.detachAppender(appender);
            }

            return appender.list.stream()
                    .filter(event -> event.getLevel() == Level.WARN)
                    .map(ILoggingEvent::getFormattedMessage)
                    .toList();
        }

        /**
         * The line of the file a failure to parse it names: the line commons-csv names, counted from the line of the
         * file the block starts at if the failure names one, and from the first line if not.
         */
        private static long lineOf(Throwable failure) {
            String message = failure.getMessage();
            Matcher block = Pattern.compile("^Cannot parse the block of \\S+ from its line (\\d+): ").matcher(message);
            long blockStart = block.find() ? Long.parseLong(block.group(1)) : 1;
            Matcher line = Pattern.compile("(?:startline |at line: )(\\d+)").matcher(message);
            assertThat(line.find()).as("a line in: %s", message).isTrue();

            return blockStart + Long.parseLong(line.group(1)) - 1;
        }

        /**
         * Reads a feed with a record commons-csv cannot parse in stops.txt, in one piece and in blocks: both fail with
         * the exception of commons-csv, at the same line of the file.
         */
        private static void assertFailsAtTheLineOfReadingInOnePiece(Path directory, int blockSize, String failure) {
            Throwable inOnePiece = catchThrowable(
                    () -> new GtfsScheduleReader(Integer.MAX_VALUE, 1).read(directory.toString()));
            Throwable inBlocks = catchThrowable(
                    () -> new GtfsScheduleReader(blockSize, CONCURRENCY).read(directory.toString()));

            assertThat(inOnePiece).isInstanceOf(UncheckedIOException.class).hasMessageContaining(failure);
            assertThat(inBlocks).isInstanceOf(UncheckedIOException.class)
                    .hasMessageStartingWith("Cannot parse the block of stops.txt from its line ")
                    .hasMessageContaining(failure)
                    .cause()
                    .isExactlyInstanceOf(inOnePiece.getCause().getClass());
            assertThat(lineOf(inBlocks)).isEqualTo(lineOf(inOnePiece));
        }

        @ParameterizedTest(name = "blocks of {0} bytes")
        @ValueSource(ints = {1, 64, 4096})
        void schedule_isTheOneReadInOnePiece(int blockSize, @TempDir Path tempDir) throws IOException {
            String zip = GTFS_SCHEDULE_DATASET.getZip(tempDir).getAbsolutePath();

            GtfsSchedule inBlocks = new GtfsScheduleReader(blockSize, CONCURRENCY).read(zip);

            assertThat(dump(inBlocks)).isEqualTo(dump(readInOnePiece(zip)));
        }

        @ParameterizedTest(name = "blocks of {0} bytes")
        @ValueSource(ints = {1, 7, 64})
        void quotedValuesAcrossBlocks_areReadAsInOnePiece(int blockSize, @TempDir Path tempDir) throws IOException {
            Path directory = GTFS_SCHEDULE_DATASET.getUnzipped(tempDir).toPath();
            quoteStops(directory);

            GtfsSchedule inBlocks = new GtfsScheduleReader(blockSize, CONCURRENCY).read(directory.toString());

            assertThat(inBlocks.getStops().values()).allSatisfy(
                    stop -> assertThat(stop.getName()).contains(", \"quoted\"\nand broken"));
            assertThat(dump(inBlocks)).isEqualTo(dump(readInOnePiece(directory.toString())));
        }

        @ParameterizedTest(name = "blocks of {0} bytes")
        @ValueSource(ints = {1, 64})
        void invalidRecords_areSkippedWithTheWarningsOfReadingInOnePiece(int blockSize,
                                                                         @TempDir Path tempDir) throws IOException {
            Path directory = GTFS_SCHEDULE_DATASET.getUnzipped(tempDir).toPath();
            // a stop time of a trip that does not exist, which the builder rejects, one with an invalid time, which
            // reading its values rejects, and a calendar date with an invalid exception type
            Files.writeString(directory.resolve("stop_times.txt"),
                    "\nUNKNOWN,6:00:00,6:00:00,STAGECOACH,1,,,,\nSTBA,6:x0:00,6:20:00,BEATTY_AIRPORT,3,,,,\n",
                    StandardOpenOption.APPEND);
            Files.writeString(directory.resolve("calendar_dates.txt"), "\nFULLW,20070605,7\n",
                    StandardOpenOption.APPEND);

            List<String> inOnePiece = warningsReading(new GtfsScheduleReader(Integer.MAX_VALUE, 1), directory);
            List<String> inBlocks = warningsReading(new GtfsScheduleReader(blockSize, CONCURRENCY), directory);

            assertThat(inOnePiece).hasSize(3);
            assertThat(inBlocks).isEqualTo(inOnePiece);
        }

        @ParameterizedTest(name = "blocks of {0} bytes")
        @ValueSource(ints = {1, 64, Integer.MAX_VALUE})
        void invalidCoordinate_failsAsInOnePiece(int blockSize, @TempDir Path tempDir) throws IOException {
            Path directory = GTFS_SCHEDULE_DATASET.getUnzipped(tempDir).toPath();
            Files.writeString(directory.resolve("stops.txt"), "\nNOT_A_NUMBER,Not a number,,north,-116.8,,\n",
                    StandardOpenOption.APPEND);

            assertThatThrownBy(
                    () -> new GtfsScheduleReader(blockSize, CONCURRENCY).read(directory.toString())).isInstanceOf(
                    NumberFormatException.class).hasMessageContaining("north");
        }

        @ParameterizedTest(name = "blocks of {0} bytes")
        @ValueSource(ints = {1, 64})
        void quoteNotClosed_failsAtTheLineOfReadingInOnePiece(int blockSize, @TempDir Path tempDir) throws IOException {
            Path directory = GTFS_SCHEDULE_DATASET.getUnzipped(tempDir).toPath();
            Files.writeString(directory.resolve("stops.txt"), "\n\"NOT_CLOSED,Not closed,36.9,-116.8\n",
                    StandardOpenOption.APPEND);

            assertFailsAtTheLineOfReadingInOnePiece(directory, blockSize,
                    "EOF reached before encapsulated token finished");
        }

        @ParameterizedTest(name = "blocks of {0} bytes")
        @ValueSource(ints = {1, 64})
        void characterAfterAQuotedValue_failsAtTheLineOfReadingInOnePiece(int blockSize,
                                                                          @TempDir Path tempDir) throws IOException {
            Path directory = GTFS_SCHEDULE_DATASET.getUnzipped(tempDir).toPath();
            Path stops = directory.resolve("stops.txt");
            Files.writeString(stops, Files.readString(stops).replace("BEATTY_AIRPORT,", "\"BEATTY_AIRPORT\" x,"));

            assertFailsAtTheLineOfReadingInOnePiece(directory, blockSize,
                    "Invalid character between encapsulated token and delimiter");
        }
    }
}
