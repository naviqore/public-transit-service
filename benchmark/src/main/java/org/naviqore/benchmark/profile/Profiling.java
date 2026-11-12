package org.naviqore.benchmark.profile;

import jdk.jfr.Configuration;
import jdk.jfr.Recording;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.naviqore.benchmark.Arguments;
import org.naviqore.benchmark.BenchmarkUtils;
import org.naviqore.benchmark.Feed;
import org.naviqore.benchmark.version.Version;
import org.naviqore.benchmark.workload.Query;
import org.naviqore.benchmark.workload.Workload;
import org.naviqore.benchmark.workload.Workloads;

import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Records where a version spends the time of workloads: for each workload a JFR recording of its own, so that the hot
 * methods of one do not hide among those of another, with the profile settings and an execution sample every
 * millisecond, of the queries only, after a round to warm the JIT and the caches up. Reading the feed and building the
 * service are not recorded, see {@link org.naviqore.benchmark.load.LoadTimeline} for those.
 * <p>
 * Outputs: for each workload, the recording and its hot methods as a CSV file, and the hottest methods in the log. The
 * {@code jfr} command breaks a method of a recording down, see {@link HotMethods}.
 */
@UtilityClass
@Slf4j
public class Profiling {

    private static final List<String> DEFAULT_WORKLOADS = List.of(Workloads.CONNECTIONS, Workloads.ISOLINES);
    private static final int DEFAULT_ROUNDS = 3;
    private static final Duration SAMPLE_PERIOD = Duration.ofMillis(1);
    private static final int TOP = 20;

    public static void run(Arguments arguments) throws Exception {
        Feed feed = arguments.feed();
        Version version = arguments.version(0);
        version.build(feed.path());
        List<Workload> workloads = arguments.workloads(DEFAULT_WORKLOADS)
                .stream()
                .map(name -> Workloads.of(name, version.subject()))
                .toList();
        int rounds = arguments.number("rounds", DEFAULT_ROUNDS);

        LocalDateTime timestamp = LocalDateTime.now();
        for (Workload workload : workloads) {
            log.info("Profiling workload {}: {} queries, {} rounds after one to warm up", workload.name(),
                    workload.queries().size(), rounds);
            runQueries(version, workload);

            String name = "profile_" + workload.name();
            Path file = BenchmarkUtils.outputPath(arguments.output(), feed.name(), timestamp, name + ".jfr");
            try (Recording recording = new Recording(Configuration.getConfiguration("profile"))) {
                recording.enable("jdk.ExecutionSample").withPeriod(SAMPLE_PERIOD);
                recording.start();
                for (int round = 1; round <= rounds; round++) {
                    log.info("Recording round {} of {}", round, rounds);
                    runQueries(version, workload);
                }
                recording.stop();
                recording.dump(file);
            }
            log.info("Recorded {}", file);

            HotMethods.Summary summary = HotMethods.summarize(file);
            HotMethods.write(summary, arguments.output(), feed, timestamp, name + "_hot_methods");
            HotMethods.logHotMethods(summary, TOP);
        }
    }

    private static void runQueries(Version version, Workload workload) {
        for (Query query : workload.queries()) {
            version.time(query);
        }
    }

}
