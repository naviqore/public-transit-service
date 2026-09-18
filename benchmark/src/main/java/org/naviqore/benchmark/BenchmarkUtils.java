package org.naviqore.benchmark;

import lombok.experimental.UtilityClass;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.naviqore.gtfs.schedule.GtfsScheduleDataset;
import org.naviqore.gtfs.schedule.GtfsScheduleReader;
import org.naviqore.gtfs.schedule.model.GtfsSchedule;
import org.naviqore.gtfs.schedule.model.StopTime;
import org.naviqore.gtfs.schedule.model.Trip;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Shared setup, sampling and output utilities for benchmarks. CSV columns use snake_case with units on durations (_s,
 * _ms) and distances (_m). Date-time values use ISO 8601 with an offset; filenames use a local run timestamp.
 */
@UtilityClass
public class BenchmarkUtils {

    private static final DateTimeFormatter FILE_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy_MM_dd_HH_mm_ss",
            Locale.ROOT);

    public static GtfsSchedule initializeSchedule(GtfsScheduleDataset dataset, Path inputDirectory,
                                                  long monitoringIntervalMs) throws IOException, InterruptedException {
        File file = dataset.getZip(inputDirectory);
        GtfsSchedule schedule = new GtfsScheduleReader().read(file.getPath());
        manageResources(monitoringIntervalMs);
        return schedule;
    }

    public static void manageResources(long monitoringIntervalMs) throws InterruptedException {
        System.gc();
        Thread.sleep(monitoringIntervalMs);
    }

    /**
     * Returns unique active stop IDs in stable order for reproducible sampling.
     */
    public static List<String> getActiveStopIds(GtfsSchedule schedule, LocalDate date, int minimumStops) {
        if (minimumStops < 1) {
            throw new IllegalArgumentException("Minimum active stop count must be positive.");
        }
        List<Trip> activeTrips = schedule.getActiveTrips(date);
        if (activeTrips.isEmpty()) {
            throw new IllegalStateException(
                    String.format("No active trips found on schedule date %s; choose a date with active service.",
                            date));
        }

        List<String> stopIds = collectStopIds(activeTrips);
        if (stopIds.size() < minimumStops) {
            throw new IllegalStateException(String.format(
                    "Fewer than %d active stops found on schedule date %s; choose a date with at least %d active stops.",
                    minimumStops, date, minimumStops));
        }
        return stopIds;
    }

    private static List<String> collectStopIds(List<Trip> trips) {
        Set<String> stopIds = new TreeSet<>();
        for (Trip trip : trips) {
            for (StopTime stopTime : trip.getStopTimes()) {
                stopIds.add(stopTime.stop().getId());
            }
        }
        return new ArrayList<>(stopIds);
    }

    public static long elapsedMillis(long startTime, long endTime) {
        return TimeUnit.NANOSECONDS.toMillis(endTime - startTime);
    }

    public static String formatDateTime(OffsetDateTime dateTime) {
        return dateTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    /**
     * Writes UTF-8 CSV to {dataset}/{run timestamp}_{suffix}.csv, including the header. Reuse the timestamp for all
     * outputs of a run. The caller closes the printer.
     */
    public static CSVPrinter openCsv(GtfsScheduleDataset dataset, LocalDateTime timestamp, String suffix,
                                     String... headers) throws IOException {
        return openCsv(Path.of("benchmark/output"), dataset, timestamp, suffix, headers);
    }

    static CSVPrinter openCsv(Path outputDirectory, GtfsScheduleDataset dataset, LocalDateTime timestamp, String suffix,
                              String... headers) throws IOException {
        Path directory = outputDirectory.resolve(dataset.name().toLowerCase(Locale.ROOT));
        Files.createDirectories(directory);
        String fileName = String.format("%s_%s.csv", timestamp.format(FILE_TIMESTAMP_FORMAT), suffix);
        return CSVFormat.DEFAULT.builder()
                .setHeader(headers)
                .get()
                .print(directory.resolve(fileName), StandardCharsets.UTF_8);
    }

}

