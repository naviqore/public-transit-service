package org.naviqore.benchmark.timing;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.naviqore.benchmark.Arguments;
import org.naviqore.benchmark.Benchmark;
import org.naviqore.benchmark.BenchmarkUtils;
import org.naviqore.benchmark.Feed;
import org.naviqore.benchmark.version.Version;
import org.naviqore.benchmark.workload.Query;
import org.naviqore.benchmark.workload.Workload;
import org.naviqore.benchmark.workload.Workloads;

import java.io.IOException;
import java.io.Reader;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Compares the times of two versions on the queries of workloads in randomized multiple interleaved trials (Abedi and
 * Brecht, ICPE 2017): every round runs all queries of a workload on both versions, in an order drawn anew for each
 * round, so that whatever else happens on the machine meanwhile hits both versions alike. The first rounds warm the JIT
 * and the caches up and do not count.
 * <p>
 * The version loaded first into a JVM does not run like the one loaded second, by more than many changes do. So the
 * rounds run twice, each time in a new JVM, with the versions loaded in either order, and the ratio of their times,
 * candidate / baseline, combines both orders, with a 95 % confidence interval (see {@link Ratio}). It is given for the
 * wall time and for the CPU time of the thread that runs the queries; isolines with a time window route on several
 * threads, which only the wall time accounts for.
 * <p>
 * Outputs: the rounds of each load order and the ratios as CSV files, and the ratios in the log, with a warning when
 * the machine was busy. A load average above the number of processors makes rounds vary far more than most changes.
 */
@UtilityClass
@Slf4j
public class TimingComparison {

    private static final List<String> DEFAULT_WORKLOADS = List.of(Workloads.CONNECTIONS, Workloads.ISOLINES);
    private static final int DEFAULT_ROUNDS = 10;
    private static final int DEFAULT_WARMUPS = 2;
    private static final long SEED = 1234;

    // options the JVMs of the load orders get from the one running them
    private static final String ORDER = "order";
    private static final String TIMESTAMP = "timestamp";

    private static final String[] ROUND_HEADERS = {"order", "workload", "round", "warmup", "first", "baseline_wall_ms",
            "candidate_wall_ms", "baseline_cpu_ms", "candidate_cpu_ms", "load_average", "processors"};

    public static void run(Arguments arguments) throws Exception {
        Optional<String> order = arguments.option(ORDER);
        if (order.isPresent()) {
            measure(arguments, LoadOrder.valueOf(order.get()),
                    LocalDateTime.parse(arguments.option(TIMESTAMP).orElseThrow()));
            return;
        }

        LocalDateTime timestamp = LocalDateTime.now().withNano(0);
        for (LoadOrder loadOrder : LoadOrder.values()) {
            measureInNewJvm(arguments, loadOrder, timestamp);
        }
        summarize(arguments.output(), arguments.feed(), timestamp);
    }

    /**
     * Runs the rounds of one load order in a JVM of their own, started like this one.
     */
    private static void measureInNewJvm(Arguments arguments, LoadOrder loadOrder,
                                        LocalDateTime timestamp) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(ProcessHandle.current().info().command().orElse("java"));
        command.addAll(ManagementFactory.getRuntimeMXBean().getInputArguments());
        command.addAll(List.of("-cp", System.getProperty("java.class.path"), Benchmark.class.getName()));
        command.add(arguments.command());
        command.addAll(arguments.positional());
        arguments.options().forEach((name, value) -> command.addAll(List.of("--" + name, value)));
        command.addAll(List.of("--" + ORDER, loadOrder.name(), "--" + TIMESTAMP, timestamp.toString()));

        log.info("Measuring with the {} loaded first, in a new JVM", loadOrder.first());
        Process process = new ProcessBuilder(command).inheritIO().start();
        if (process.waitFor() != 0) {
            throw new IllegalStateException("The JVM measuring with the " + loadOrder.first() + " loaded first failed");
        }
    }

    private static void measure(Arguments arguments, LoadOrder loadOrder, LocalDateTime timestamp) throws Exception {
        Feed feed = arguments.feed();
        Version baseline = arguments.version(0);
        Version candidate = arguments.version(1);
        for (Version version : loadOrder == LoadOrder.BASELINE_FIRST ? List.of(baseline, candidate) : List.of(candidate,
                baseline)) {
            version.build(feed.path());
        }

        int rounds = arguments.number("rounds", DEFAULT_ROUNDS);
        int warmups = arguments.number("warmups", DEFAULT_WARMUPS);
        Random random = new Random(SEED + loadOrder.ordinal());
        OperatingSystemMXBean system = ManagementFactory.getOperatingSystemMXBean();

        try (CSVPrinter csv = BenchmarkUtils.openCsv(arguments.output(), feed.name(), timestamp,
                "timing_" + loadOrder.suffix(), ROUND_HEADERS)) {
            for (String name : arguments.workloads(DEFAULT_WORKLOADS)) {
                // the queries are drawn from the baseline in both load orders, so that both run the same
                Workload workload = Workloads.of(name, baseline.subject());
                log.info("Timing workload {}: {} queries, {} rounds after {} to warm up", name,
                        workload.queries().size(), rounds, warmups);
                for (int round = 1; round <= warmups + rounds; round++) {
                    boolean baselineFirst = random.nextBoolean();
                    long[] baselineTimes;
                    long[] candidateTimes;
                    if (baselineFirst) {
                        baselineTimes = time(baseline, workload);
                        candidateTimes = time(candidate, workload);
                    } else {
                        candidateTimes = time(candidate, workload);
                        baselineTimes = time(baseline, workload);
                    }
                    double loadAverage = system.getSystemLoadAverage();
                    csv.printRecord(loadOrder.suffix(), name, round, round <= warmups,
                            baselineFirst ? "baseline" : "candidate", BenchmarkUtils.millis(baselineTimes[0]),
                            BenchmarkUtils.millis(candidateTimes[0]), BenchmarkUtils.millis(baselineTimes[1]),
                            BenchmarkUtils.millis(candidateTimes[1]), loadAverage, system.getAvailableProcessors());
                    csv.flush();
                    log.info("Round {} of {}: candidate / baseline {} wall, {} CPU, load average {}", round,
                            warmups + rounds,
                            String.format(Locale.ROOT, "%.3f", (double) candidateTimes[0] / baselineTimes[0]),
                            String.format(Locale.ROOT, "%.3f", (double) candidateTimes[1] / baselineTimes[1]),
                            String.format(Locale.ROOT, "%.1f", loadAverage));
                }
            }
        }
    }

    /**
     * Runs the queries of a workload on a version and returns the sums of their wall times and CPU times.
     */
    private static long[] time(Version version, Workload workload) {
        long wallTime = 0;
        long cpuTime = 0;
        for (Query query : workload.queries()) {
            long[] times = version.time(query);
            wallTime += times[0];
            cpuTime += times[1];
        }

        return new long[]{wallTime, cpuTime};
    }

    /**
     * Combines the rounds of both load orders into the ratio of every workload, in wall time and in CPU time.
     */
    private static void summarize(Path output, Feed feed, LocalDateTime timestamp) throws IOException {
        Map<String, Rounds> roundsByWorkload = new LinkedHashMap<>();
        double maxLoadAverage = 0;
        int processors = 0;
        for (LoadOrder loadOrder : LoadOrder.values()) {
            Path file = BenchmarkUtils.outputPath(output, feed.name(), timestamp,
                    "timing_" + loadOrder.suffix() + ".csv");
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                for (CSVRecord row : CSVFormat.DEFAULT.builder()
                        .setHeader()
                        .setSkipHeaderRecord(true)
                        .get()
                        .parse(reader)) {
                    if (Boolean.parseBoolean(row.get("warmup"))) {
                        continue;
                    }
                    Rounds rounds = roundsByWorkload.computeIfAbsent(row.get("workload"), _ -> new Rounds());
                    double wall = Double.parseDouble(row.get("candidate_wall_ms")) / Double.parseDouble(
                            row.get("baseline_wall_ms"));
                    double cpu = Double.parseDouble(row.get("candidate_cpu_ms")) / Double.parseDouble(
                            row.get("baseline_cpu_ms"));
                    rounds.add(loadOrder, wall, cpu);
                    maxLoadAverage = Math.max(maxLoadAverage, Double.parseDouble(row.get("load_average")));
                    processors = Integer.parseInt(row.get("processors"));
                }
            }
        }

        log.info("Time candidate / baseline [95 % confidence interval]: below 1 is faster, ~ is no difference");
        try (CSVPrinter csv = BenchmarkUtils.openCsv(output, feed.name(), timestamp, "timing", "workload", "time",
                "ratio", "lower", "upper", "verdict", "rounds")) {
            for (Map.Entry<String, Rounds> entry : roundsByWorkload.entrySet()) {
                Rounds rounds = entry.getValue();
                Ratio wall = Ratio.of(rounds.wall(LoadOrder.BASELINE_FIRST), rounds.wall(LoadOrder.CANDIDATE_FIRST));
                Ratio cpu = Ratio.of(rounds.cpu(LoadOrder.BASELINE_FIRST), rounds.cpu(LoadOrder.CANDIDATE_FIRST));
                for (Map.Entry<String, Ratio> ratio : List.of(Map.entry("wall", wall), Map.entry("cpu", cpu))) {
                    csv.printRecord(entry.getKey(), ratio.getKey(), ratio.getValue().estimate(),
                            ratio.getValue().lower(), ratio.getValue().upper(), ratio.getValue().verdict(),
                            rounds.size());
                }
                log.info("  {} wall {} | CPU {} ({} rounds)", String.format("%-16s", entry.getKey()), wall, cpu,
                        rounds.size());
            }
        }

        if (maxLoadAverage > processors) {
            log.warn("The load average reached {} on {} processors: the machine was busy, the intervals are wide",
                    String.format(Locale.ROOT, "%.1f", maxLoadAverage), processors);
        }
    }

    /**
     * The order in which a JVM loads the two versions.
     */
    enum LoadOrder {
        BASELINE_FIRST,
        CANDIDATE_FIRST;

        String first() {
            return this == BASELINE_FIRST ? "baseline" : "candidate";
        }

        String suffix() {
            return first() + "_first";
        }
    }

    /**
     * The ratios candidate / baseline of the rounds of a workload, by load order.
     */
    private static class Rounds {

        private final Map<LoadOrder, List<Double>> wall = new EnumMap<>(LoadOrder.class);
        private final Map<LoadOrder, List<Double>> cpu = new EnumMap<>(LoadOrder.class);

        void add(LoadOrder loadOrder, double wallRatio, double cpuRatio) {
            wall.computeIfAbsent(loadOrder, _ -> new ArrayList<>()).add(wallRatio);
            cpu.computeIfAbsent(loadOrder, _ -> new ArrayList<>()).add(cpuRatio);
        }

        List<Double> wall(LoadOrder loadOrder) {
            return wall.getOrDefault(loadOrder, List.of());
        }

        List<Double> cpu(LoadOrder loadOrder) {
            return cpu.getOrDefault(loadOrder, List.of());
        }

        int size() {
            return wall.values().stream().mapToInt(List::size).sum();
        }
    }

}
