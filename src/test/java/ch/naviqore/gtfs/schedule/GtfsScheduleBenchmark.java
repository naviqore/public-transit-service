package ch.naviqore.gtfs.schedule;

import ch.naviqore.gtfs.schedule.GtfsScheduleBenchmarkData.Dataset;
import ch.naviqore.gtfs.schedule.model.GtfsSchedule;
import ch.naviqore.gtfs.schedule.model.GtfsScheduleDay;

import java.io.IOException;
import java.time.LocalDate;

public class GtfsScheduleBenchmark {

    private static final Dataset DATASET = Dataset.SWITZERLAND;

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
