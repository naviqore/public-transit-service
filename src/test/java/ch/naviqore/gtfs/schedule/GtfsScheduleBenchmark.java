package ch.naviqore.gtfs.schedule;

import ch.naviqore.gtfs.schedule.GtfsScheduleBenchmarkData.Dataset;
import ch.naviqore.gtfs.schedule.model.GtfsSchedule;
import ch.naviqore.gtfs.schedule.model.GtfsScheduleDay;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.io.IOException;
import java.time.LocalDate;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class GtfsScheduleBenchmark {

    private static final Dataset DATASET = Dataset.GERMANY;

    public static void main(String[] args) throws IOException, InterruptedException {
        String path = GtfsScheduleBenchmarkData.get(DATASET);
        GtfsSchedule schedule = new GtfsScheduleReader().read(path);
        GtfsScheduleDay scheduleDay = schedule.getScheduleForDay(LocalDate.now());
        // clean heap from reading artifacts
        System.gc();
        // monitor effect
        Thread.sleep(30000);
    }
}
