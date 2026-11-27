package org.naviqore.benchmark.load;

import jdk.jfr.Configuration;
import jdk.jfr.Recording;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVPrinter;
import org.naviqore.benchmark.Arguments;
import org.naviqore.benchmark.BenchmarkUtils;
import org.naviqore.benchmark.Feed;
import org.naviqore.benchmark.profile.HotMethods;
import org.naviqore.benchmark.version.Version;

import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Measures how long a version takes from a GTFS feed to a ready service, and where the time goes: reading the feed and
 * building the service, each with its time and the heap it leaves behind, and their steps as the libraries log them,
 * each lasting until the next log line. A JFR recording with an execution sample every millisecond shows what a step
 * spends its time on.
 * <p>
 * Outputs: the steps as a CSV file, the recording and its hot methods as a CSV file, and the steps, the times, the heap
 * and the hottest methods in the log.
 */
@UtilityClass
@Slf4j
public class LoadTimeline {

    private static final Duration SAMPLE_PERIOD = Duration.ofMillis(1);
    private static final double MILLIS_PER_SECOND = 1000.0;
    private static final int TOP = 10;

    public static void run(Arguments arguments) throws Exception {
        Feed feed = arguments.feed();
        Version version = arguments.version(0);
        LocalDateTime timestamp = LocalDateTime.now();
        Path file = BenchmarkUtils.outputPath(arguments.output(), feed.name(), timestamp, "load.jfr");

        long heapBefore = BenchmarkUtils.heapAfterGarbageCollection();
        Phase read;
        Phase build;
        long heapAfterRead;
        try (Recording recording = new Recording(Configuration.getConfiguration("profile"))) {
            recording.enable("jdk.ExecutionSample").withPeriod(SAMPLE_PERIOD);
            recording.start();
            read = Phase.of(() -> version.subject().read(feed.path().toString()));
            // the garbage collection between the phases belongs to neither
            heapAfterRead = BenchmarkUtils.heapAfterGarbageCollection();
            build = Phase.of(() -> version.subject().build());
            recording.stop();
            recording.dump(file);
        }
        long heapAfterBuild = BenchmarkUtils.heapAfterGarbageCollection();

        List<Step> steps = steps(version.subject().log(), read, build);
        try (CSVPrinter csv = BenchmarkUtils.openCsv(arguments.output(), feed.name(), timestamp, "load", "step",
                "logger", "start_s", "duration_s")) {
            for (Step step : steps) {
                csv.printRecord(step.message(), step.logger(), step.start() / MILLIS_PER_SECOND,
                        step.duration() / MILLIS_PER_SECOND);
            }
        }

        log.info("Steps of version {}, each until the next log line (start and duration in seconds):", version.name());
        for (Step step : steps) {
            log.info("  {} {}  {}: {}", String.format(Locale.ROOT, "%7.1f", step.start() / MILLIS_PER_SECOND),
                    String.format(Locale.ROOT, "%6.1f", step.duration() / MILLIS_PER_SECOND), step.logger(),
                    step.message());
        }
        log.info("Read the feed in {} s, {} MB of heap after; built the service in {} s, {} MB of heap after",
                seconds(read), megabytes(heapAfterRead - heapBefore), seconds(build),
                megabytes(heapAfterBuild - heapBefore));

        HotMethods.Summary summary = HotMethods.summarize(file);
        HotMethods.write(summary, arguments.output(), feed, timestamp, "load_hot_methods");
        HotMethods.logHotMethods(summary, TOP);
    }

    /**
     * The steps of reading and building as the libraries logged them: each from its log line to the next one of the
     * same phase, the last of a phase until the phase ended.
     *
     * @param log   the log lines of the subject, see {@link org.naviqore.benchmark.subject.Subject#log()}
     * @param read  when reading the feed started and ended
     * @param build when building the service started and ended
     */
    static List<Step> steps(List<String> log, Phase read, Phase build) {
        List<Step> steps = new ArrayList<>(log.size());
        for (int i = 0; i < log.size(); i++) {
            String[] line = log.get(i).split("\t", 3);
            long time = Long.parseLong(line[0]);
            long phaseEnd = time <= read.end() ? read.end() : build.end();
            long end = phaseEnd;
            if (i + 1 < log.size()) {
                end = Math.min(phaseEnd, Long.parseLong(log.get(i + 1).split("\t", 2)[0]));
            }
            steps.add(new Step(time - read.start(), Math.max(0, end - time), line[1], line[2]));
        }

        return steps;
    }

    private static String seconds(Phase phase) {
        return String.format(Locale.ROOT, "%.1f", (phase.end() - phase.start()) / MILLIS_PER_SECOND);
    }

    private static String megabytes(long bytes) {
        return String.format(Locale.ROOT, "%,.0f", BenchmarkUtils.megabytes(bytes));
    }

    /**
     * When a phase started and ended, in milliseconds since the epoch, as log events are stamped.
     */
    record Phase(long start, long end) {

        static Phase of(Runnable phase) {
            long start = System.currentTimeMillis();
            phase.run();
            return new Phase(start, System.currentTimeMillis());
        }
    }

    /**
     * A step of reading or building.
     *
     * @param start    the milliseconds since reading started
     * @param duration the milliseconds until the next step, or the end of the phase
     * @param logger   the class that logged the step, without package
     * @param message  what it logged
     */
    record Step(long start, long duration, String logger, String message) {
    }

}
