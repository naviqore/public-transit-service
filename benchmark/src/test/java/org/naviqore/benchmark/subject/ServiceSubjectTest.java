package org.naviqore.benchmark.subject;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.naviqore.benchmark.workload.Query;
import org.naviqore.benchmark.workload.Query.Location;
import org.naviqore.benchmark.workload.Query.TimeType;
import org.naviqore.gtfs.schedule.GtfsScheduleDataset;

import java.io.IOException;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ServiceSubjectTest {

    // a Wednesday within the validity of the sample feed, in its time zone
    private static final OffsetDateTime MORNING = OffsetDateTime.parse("2008-05-14T08:00-07:00");

    private static ServiceSubject subject;

    @BeforeAll
    static void setUp(@TempDir Path directory) throws IOException {
        subject = new ServiceSubject();
        subject.read(GtfsScheduleDataset.SAMPLE_FEED_1.getZip(directory).getPath());
        subject.build();
    }

    @Test
    void describesTheTimetable() {
        assertEquals(List.of("2007-01-01", "2010-12-31"), subject.validity());
        assertEquals("America/Los_Angeles", subject.zone());
    }

    @Test
    void logsReadingAndBuilding() {
        List<String> log = subject.log();

        assertFalse(log.isEmpty());
        assertTrue(log.stream().anyMatch(line -> line.contains("GtfsScheduleReader")));
        assertTrue(log.stream().allMatch(line -> line.split("\t").length == 3));
    }

    @Test
    void findsStopsByNameOrDrawsOne() {
        assertEquals("FUR_CREEK_RES", subject.stopByName("Furnace Creek Resort (Demo)"));
        // a name no stop has draws the same stop every time
        assertEquals(subject.stopByName("Zürich HB"), subject.stopByName("Zürich HB"));
    }

    @Test
    void drawsTheSameStopsWithTheSameSeed() {
        assertEquals(subject.randomStops(5, 42), subject.randomStops(5, 42));
    }

    @Test
    void answersAndTimesTheSameQuery() {
        Query query = Query.connections(Location.stop("FUR_CREEK_RES"), Location.stop("BULLFROG"), MORNING,
                TimeType.DEPARTURE, "default");

        List<String> answer = subject.answer(query.encode());
        long[] times = subject.time(query.encode());

        assertFalse(answer.isEmpty());
        assertTrue(answer.getFirst().contains(" | PT "), answer.getFirst());
        assertEquals(4, times.length);
        assertTrue(times[0] > 0);
        assertEquals(answer.size(), times[3]);
    }

    @Test
    void answersIsolinesByStop() {
        Query query = Query.isolines(Location.near("FUR_CREEK_RES"), MORNING, TimeType.DEPARTURE, "default");

        List<String> answer = subject.answer(query.encode());

        assertFalse(answer.isEmpty());
        assertEquals(answer.stream().sorted().toList(), answer);
    }

    @Test
    void answersAFailureAsOneLine() {
        Query query = Query.isolines(Location.stop("NO_SUCH_STOP"), MORNING, TimeType.DEPARTURE, "default");

        List<String> answer = subject.answer(query.encode());

        assertEquals(1, answer.size());
        assertTrue(answer.getFirst().startsWith("EXCEPTION StopNotFoundException"), answer.getFirst());
        assertEquals(-1, subject.time(query.encode())[3]);
    }

    @Test
    void rejectsAnUnknownConfiguration() {
        Query query = Query.isolines(Location.stop("FUR_CREEK_RES"), MORNING, TimeType.DEPARTURE, "teleport");

        assertTrue(subject.answer(query.encode()).getFirst().startsWith("EXCEPTION IllegalArgumentException"));
    }

}
