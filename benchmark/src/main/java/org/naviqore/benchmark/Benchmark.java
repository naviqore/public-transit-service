package org.naviqore.benchmark;

import lombok.experimental.UtilityClass;
import org.naviqore.benchmark.compare.AnswerComparison;
import org.naviqore.benchmark.load.LoadTimeline;
import org.naviqore.benchmark.profile.HotMethods;
import org.naviqore.benchmark.profile.Profiling;
import org.naviqore.benchmark.run.WorkloadRun;
import org.naviqore.benchmark.timing.TimingComparison;

/**
 * Benchmarks of the libraries on a GTFS feed:
 * <ul>
 *     <li>{@code compare BASELINE CANDIDATE}: the answers of two versions, query by query ({@link AnswerComparison});</li>
 *     <li>{@code timing BASELINE CANDIDATE}: the times of two versions, interleaved, in both load orders
 *     ({@link TimingComparison});</li>
 *     <li>{@code profile VERSION}: where a version spends its time ({@link Profiling});</li>
 *     <li>{@code jfr RECORDING [METHOD...]}: the hot methods of a recording, or the samples inside methods
 *     ({@link HotMethods});</li>
 *     <li>{@code load VERSION}: how long a version takes from the feed to a ready service ({@link LoadTimeline});</li>
 *     <li>{@code run VERSION}: the times of a version, query by query ({@link WorkloadRun}).</li>
 * </ul>
 * A version is the jar of this module with all it depends on, built from the version. {@code benchmark/bench.sh} builds
 * the versions from git references and runs these commands, see the README of the module.
 */
@UtilityClass
public class Benchmark {

    static void main(String[] args) throws Exception {
        Arguments arguments = Arguments.parse(args);
        switch (arguments.command()) {
            case "compare" -> AnswerComparison.run(arguments);
            case "timing" -> TimingComparison.run(arguments);
            case "profile" -> Profiling.run(arguments);
            case "jfr" -> HotMethods.run(arguments);
            case "load" -> LoadTimeline.run(arguments);
            case "run" -> WorkloadRun.run(arguments);
            default -> throw new IllegalArgumentException(
                    "Unknown command " + arguments.command() + ": compare, timing, profile, jfr, load or run");
        }
    }

}
