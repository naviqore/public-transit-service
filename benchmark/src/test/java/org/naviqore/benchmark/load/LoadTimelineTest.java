package org.naviqore.benchmark.load;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LoadTimelineTest {

    @Test
    void stepsLastUntilTheNextLineOfTheirPhase() {
        LoadTimeline.Phase read = new LoadTimeline.Phase(1_000, 5_000);
        LoadTimeline.Phase build = new LoadTimeline.Phase(6_000, 9_000);
        List<String> log = List.of("1000\tGtfsScheduleReader\tReading stops", "2500\tGtfsScheduleReader\tReading trips",
                "6000\tKDTreeBuilder\tBuilding spatial index", "8000\tWalkTransferGenerator\tGenerating transfers");

        List<LoadTimeline.Step> steps = LoadTimeline.steps(log, read, build);

        assertEquals(List.of(new LoadTimeline.Step(0, 1_500, "GtfsScheduleReader", "Reading stops"),
                // the last step of reading ends with reading, not with the next line, after a garbage collection
                new LoadTimeline.Step(1_500, 2_500, "GtfsScheduleReader", "Reading trips"),
                new LoadTimeline.Step(5_000, 2_000, "KDTreeBuilder", "Building spatial index"),
                new LoadTimeline.Step(7_000, 1_000, "WalkTransferGenerator", "Generating transfers")), steps);
    }

}
