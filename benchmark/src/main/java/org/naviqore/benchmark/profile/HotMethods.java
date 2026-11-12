package org.naviqore.benchmark.profile;

import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordingFile;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVPrinter;
import org.naviqore.benchmark.Arguments;
import org.naviqore.benchmark.BenchmarkUtils;
import org.naviqore.benchmark.Feed;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Consumer;

/**
 * Reads the execution samples of a JFR recording by method, each named by its class without package and its name, e.g.
 * {@code RouteScanner.scan}:
 * <ul>
 *     <li>the hot methods: how many samples have a method on top of the stack, where the time is spent (self), and
 *     anywhere on it, where the time is spent below (total);</li>
 *     <li>inside a method: which callees its samples went to, by the line that calls them, and which leaves they ended
 *     in.</li>
 * </ul>
 * The {@code jfr} command prints the samples inside the methods given, or the hot methods of a recording if none is.
 */
@UtilityClass
@Slf4j
public class HotMethods {

    private static final String EXECUTION_SAMPLE = "jdk.ExecutionSample";
    private static final int TOP = 20;
    private static final int TOP_INSIDE = 8;

    public static void run(Arguments arguments) throws IOException {
        Path recording = Path.of(arguments.positional(0, "a JFR recording"));
        List<String> methods = arguments.positional().subList(1, arguments.positional().size());
        if (methods.isEmpty()) {
            logHotMethods(summarize(recording), TOP);
        } else {
            for (String method : methods) {
                logInside(inside(recording, method));
            }
        }
    }

    /**
     * Counts the samples of a recording by method.
     */
    public static Summary summarize(Path recording) throws IOException {
        Map<String, Long> self = new HashMap<>();
        Map<String, Long> total = new HashMap<>();
        long[] samples = {0};
        forEachStack(recording, stack -> {
            samples[0]++;
            self.merge(stack.getFirst().method(), 1L, Long::sum);
            // a method counts once per sample, even if it calls itself
            new HashSet<>(stack.stream().map(Frame::method).toList()).forEach(
                    method -> total.merge(method, 1L, Long::sum));
        });

        return new Summary(samples[0], self, total);
    }

    /**
     * Breaks the samples inside a method down: into its callees, by the line calling them, and into leaves.
     *
     * @param method the method, its class without package and its name, e.g. {@code RouteScanner.scan}
     */
    public static Inside inside(Path recording, String method) throws IOException {
        Map<String, Long> callees = new HashMap<>();
        Map<String, Long> leaves = new HashMap<>();
        long[] samples = {0};
        forEachStack(recording, stack -> {
            for (int i = 0; i < stack.size(); i++) {
                Frame frame = stack.get(i);
                if (frame.method().equals(method)) {
                    samples[0]++;
                    leaves.merge(stack.getFirst().method(), 1L, Long::sum);
                    String callee = i == 0 ? "(self) " + frame : stack.get(i - 1) + " <- line " + frame.line();
                    callees.merge(callee, 1L, Long::sum);
                    break;
                }
            }
        });

        return new Inside(method, samples[0], callees, leaves);
    }

    /**
     * Writes the hot methods of a recording to a CSV file of the outputs of a run.
     */
    public static void write(Summary summary, Path output, Feed feed, LocalDateTime timestamp,
                             String suffix) throws IOException {
        try (CSVPrinter csv = BenchmarkUtils.openCsv(output, feed.name(), timestamp, suffix, "method", "self_samples",
                "self_share", "total_samples", "total_share")) {
            for (Map.Entry<String, Long> entry : sorted(summary.total())) {
                long self = summary.self().getOrDefault(entry.getKey(), 0L);
                csv.printRecord(entry.getKey(), self, share(self, summary.samples()), entry.getValue(),
                        share(entry.getValue(), summary.samples()));
            }
        }
    }

    /**
     * Logs the methods with the most samples, on top of the stack and anywhere on it.
     */
    public static void logHotMethods(Summary summary, int top) {
        log.info("{} samples; methods by samples on top of the stack (self):", summary.samples());
        sorted(summary.self()).stream().limit(top).forEach(entry -> log.info("  {}", line(entry, summary.samples())));
        log.info("Methods by samples anywhere on the stack (total):");
        sorted(summary.total()).stream().limit(top).forEach(entry -> log.info("  {}", line(entry, summary.samples())));
    }

    private static void logInside(Inside inside) {
        log.info("{}: {} samples; callees:", inside.method(), inside.samples());
        sorted(inside.callees()).stream()
                .limit(TOP_INSIDE)
                .forEach(entry -> log.info("  {}", line(entry, inside.samples())));
        log.info("Leaves:");
        sorted(inside.leaves()).stream()
                .limit(TOP_INSIDE)
                .forEach(entry -> log.info("  {}", line(entry, inside.samples())));
    }

    private static void forEachStack(Path recording, Consumer<List<Frame>> consumer) throws IOException {
        try (RecordingFile file = new RecordingFile(recording)) {
            while (file.hasMoreEvents()) {
                RecordedEvent event = file.readEvent();
                if (!event.getEventType().getName().equals(EXECUTION_SAMPLE) || event.getStackTrace() == null) {
                    continue;
                }
                List<Frame> stack = new ArrayList<>(event.getStackTrace().getFrames().size());
                for (RecordedFrame frame : event.getStackTrace().getFrames()) {
                    stack.add(new Frame(
                            simpleName(frame.getMethod().getType().getName()) + "." + frame.getMethod().getName(),
                            frame.getLineNumber()));
                }
                if (!stack.isEmpty()) {
                    consumer.accept(stack);
                }
            }
        }
    }

    // the name of a class without its package, and without the suffix of a hidden class, e.g. LambdaForm$MH/0x0f...
    private static String simpleName(String type) {
        int hidden = type.indexOf('/');
        String name = hidden < 0 ? type : type.substring(0, hidden);
        return name.substring(name.lastIndexOf('.') + 1);
    }

    private static List<Map.Entry<String, Long>> sorted(Map<String, Long> counts) {
        return counts.entrySet()
                .stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .toList();
    }

    private static double share(long count, long samples) {
        return samples == 0 ? 0 : Math.round(1000.0 * count / samples) / 10.0;
    }

    private static String line(Map.Entry<String, Long> entry, long samples) {
        return String.format(Locale.ROOT, "%-90s %,9d %5.1f %%", entry.getKey(), entry.getValue(),
                share(entry.getValue(), samples));
    }

    /**
     * The samples of a recording by method.
     *
     * @param samples the number of samples
     * @param self    the samples of each method on top of the stack
     * @param total   the samples of each method anywhere on the stack
     */
    public record Summary(long samples, Map<String, Long> self, Map<String, Long> total) {
    }

    /**
     * The samples inside a method.
     *
     * @param method  the method
     * @param samples the samples with the method on the stack
     * @param callees the samples by callee, the method itself as {@code (self)}
     * @param leaves  the samples by the method on top of the stack
     */
    public record Inside(String method, long samples, Map<String, Long> callees, Map<String, Long> leaves) {
    }

    private record Frame(String method, int line) {

        @Override
        public String toString() {
            return method + ":" + line;
        }
    }

}
