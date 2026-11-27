package org.naviqore.benchmark;

import lombok.experimental.UtilityClass;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Shared output utilities for benchmarks. CSV columns use snake_case with units on durations (_s, _ms) and sizes (_mb).
 * Date-time values use ISO 8601 with an offset; filenames use a local run timestamp.
 */
@UtilityClass
public class BenchmarkUtils {

    public static final DateTimeFormatter FILE_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy_MM_dd_HH_mm_ss",
            Locale.ROOT);

    private static final double NANOS_PER_MILLI = 1e6;
    private static final double NANOS_PER_SECOND = 1e9;
    private static final long BYTES_PER_MEGABYTE = 1024 * 1024;

    /**
     * Writes UTF-8 CSV to {output}/{feed}/{run timestamp}_{suffix}.csv, including the header. Reuse the timestamp for
     * all outputs of a run. The caller closes the printer.
     */
    public static CSVPrinter openCsv(Path outputDirectory, String feed, LocalDateTime timestamp, String suffix,
                                     String... headers) throws IOException {
        return CSVFormat.DEFAULT.builder()
                .setHeader(headers)
                .get()
                .print(outputPath(outputDirectory, feed, timestamp, suffix + ".csv"), StandardCharsets.UTF_8);
    }

    /**
     * Returns the path of an output of a run, {output}/{feed}/{run timestamp}_{name}, creating its directory.
     */
    public static Path outputPath(Path outputDirectory, String feed, LocalDateTime timestamp,
                                  String name) throws IOException {
        Path directory = outputDirectory.resolve(feed);
        Files.createDirectories(directory);
        return directory.resolve(timestamp.format(FILE_TIMESTAMP_FORMAT) + "_" + name);
    }

    public static double millis(long nanos) {
        return Math.round(nanos / NANOS_PER_MILLI * 10) / 10.0;
    }

    public static double seconds(long nanos) {
        return Math.round(nanos / NANOS_PER_SECOND * 10) / 10.0;
    }

    public static double megabytes(long bytes) {
        return Math.round(10.0 * bytes / BYTES_PER_MEGABYTE) / 10.0;
    }

    /**
     * Returns the heap in use after a full garbage collection, in bytes.
     */
    public static long heapAfterGarbageCollection() {
        System.gc();
        return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
    }

}
