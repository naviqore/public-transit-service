package org.naviqore.benchmark.profile;

import jdk.jfr.Recording;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTrue;

class HotMethodsTest {

    private static volatile double sink;

    private static void spin(Duration duration) {
        long end = System.nanoTime() + duration.toNanos();
        double value = 0;
        while (System.nanoTime() < end) {
            value += Math.sqrt(value + 1);
        }
        sink = value;
    }

    @Test
    void findsTheMethodTheTimeIsSpentIn(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("spin.jfr");
        try (Recording recording = new Recording()) {
            recording.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(1));
            recording.start();
            spin(Duration.ofMillis(500));
            recording.stop();
            recording.dump(file);
        }

        HotMethods.Summary summary = HotMethods.summarize(file);
        HotMethods.Inside inside = HotMethods.inside(file, "HotMethodsTest.spin");

        assertTrue(summary.samples() > 0);
        assertTrue(summary.total().getOrDefault("HotMethodsTest.spin", 0L) > summary.samples() / 2);
        assertTrue(inside.samples() > 0);
        assertTrue(inside.callees().keySet().stream().anyMatch(callee -> callee.contains("HotMethodsTest.spin")));
    }

}
