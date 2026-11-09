package org.naviqore.benchmark.run;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVPrinter;
import org.naviqore.benchmark.Arguments;
import org.naviqore.benchmark.BenchmarkUtils;
import org.naviqore.benchmark.Feed;
import org.naviqore.benchmark.version.Version;
import org.naviqore.benchmark.workload.Query;
import org.naviqore.benchmark.workload.Workload;
import org.naviqore.benchmark.workload.Workloads;

import java.time.LocalDateTime;
import java.util.List;
import java.util.LongSummaryStatistics;

/**
 * Runs the queries of workloads on one version, each several times, and measures every run: its wall time, the CPU time
 * and the memory it took on its thread, and the number of its answers. The first run of a query warms the caches of its
 * days up, the runs after it show the steady state.
 * <p>
 * Outputs: a row per run of a query and a row per query as CSV files, and per workload its times in the log. A query
 * whose number of answers varies between its runs is not deterministic, and is logged as a warning.
 */
@UtilityClass
@Slf4j
public class WorkloadRun {

    private static final List<String> DEFAULT_WORKLOADS = List.of(Workloads.CONNECTIONS, Workloads.ISOLINES,
            Workloads.WINDOW_ISOLINES);
    private static final int DEFAULT_RUNS = 3;

    private static final String[] RESULT_HEADERS = {"workload", "query", "run", "answers", "wall_time_ms",
            "cpu_time_ms", "allocated_mb"};
    private static final String[] SUMMARY_HEADERS = {"workload", "query", "runs", "min_answers", "max_answers",
            "consistent", "min_wall_time_ms", "avg_wall_time_ms", "max_wall_time_ms"};

    public static void run(Arguments arguments) throws Exception {
        Feed feed = arguments.feed();
        Version version = arguments.version(0);
        version.build(feed.path());
        int runs = arguments.number("runs", DEFAULT_RUNS);

        LocalDateTime timestamp = LocalDateTime.now();
        try (CSVPrinter results = BenchmarkUtils.openCsv(arguments.output(), feed.name(), timestamp, "run_results",
                RESULT_HEADERS);
             CSVPrinter summary = BenchmarkUtils.openCsv(arguments.output(), feed.name(), timestamp, "run_summary",
                     SUMMARY_HEADERS)) {
            for (String name : arguments.workloads(DEFAULT_WORKLOADS)) {
                Workload workload = Workloads.of(name, version.subject());
                log.info("Running the {} queries of workload {}, {} times each", workload.queries().size(), name, runs);
                LongSummaryStatistics workloadTimes = new LongSummaryStatistics();
                for (Query query : workload.queries()) {
                    LongSummaryStatistics answers = new LongSummaryStatistics();
                    LongSummaryStatistics wallTimes = new LongSummaryStatistics();
                    for (int run = 1; run <= runs; run++) {
                        long[] times = version.time(query);
                        results.printRecord(name, query, run, times[3], BenchmarkUtils.millis(times[0]),
                                BenchmarkUtils.millis(times[1]), BenchmarkUtils.megabytes(times[2]));
                        answers.accept(times[3]);
                        wallTimes.accept(times[0]);
                    }
                    boolean consistent = answers.getMin() == answers.getMax();
                    if (!consistent) {
                        log.warn("The number of answers to {} varies between runs: {} to {}", query, answers.getMin(),
                                answers.getMax());
                    }
                    summary.printRecord(name, query, runs, answers.getMin(), answers.getMax(), consistent,
                            BenchmarkUtils.millis(wallTimes.getMin()),
                            BenchmarkUtils.millis(Math.round(wallTimes.getAverage())),
                            BenchmarkUtils.millis(wallTimes.getMax()));
                    workloadTimes.combine(wallTimes);
                }
                log.info("Workload {}: {} ms per query on average, {} ms at most", name,
                        BenchmarkUtils.millis(Math.round(workloadTimes.getAverage())),
                        BenchmarkUtils.millis(workloadTimes.getMax()));
            }
        }
    }

}
