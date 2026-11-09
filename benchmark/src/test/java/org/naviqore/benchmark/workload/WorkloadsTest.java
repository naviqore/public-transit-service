package org.naviqore.benchmark.workload;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.naviqore.benchmark.subject.ServiceSubject;
import org.naviqore.gtfs.schedule.GtfsScheduleDataset;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkloadsTest {

    private static ServiceSubject subject;

    @BeforeAll
    static void setUp(@TempDir Path directory) throws IOException {
        subject = new ServiceSubject();
        subject.read(GtfsScheduleDataset.SAMPLE_FEED_1.getZip(directory).getPath());
        subject.build();
    }

    @Test
    void drawsTheSameQueriesEveryTime() {
        for (String name : new String[]{Workloads.ANSWERS, Workloads.CONNECTIONS, Workloads.ISOLINES,
                Workloads.WINDOW_ISOLINES}) {
            assertEquals(Workloads.of(name, subject), Workloads.of(name, subject), name);
        }
    }

    @Test
    void answersCoverTheDaysOfTheTimetable() {
        // 24 pairs of stops; the sample feed has 8 changes of daylight saving time in its four years: 15 days with 3
        // connections each and 2 more at night on the 8 days of a change, 8 configurations on 9 days, 3 from and to
        // coordinates, and 112 isolines
        assertEquals(15 * 24 * 3 + 8 * 24 * 2 + 8 * 9 * 24 + 24 * 3 + 112,
                Workloads.of(Workloads.ANSWERS, subject).queries().size());
    }

    @Test
    void timingWorkloadsHaveTheirSizes() {
        assertEquals(450, Workloads.of(Workloads.CONNECTIONS, subject).queries().size());
        assertEquals(20, Workloads.of(Workloads.ISOLINES, subject).queries().size());
        assertEquals(5, Workloads.of(Workloads.WINDOW_ISOLINES, subject).queries().size());
    }

    @Test
    void rejectsAnUnknownWorkload() {
        assertThrows(IllegalArgumentException.class, () -> Workloads.of("everything", subject));
    }

}
