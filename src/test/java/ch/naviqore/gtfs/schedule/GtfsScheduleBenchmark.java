package ch.naviqore.gtfs.schedule;

import ch.naviqore.gtfs.schedule.model.GtfsSchedule;
import ch.naviqore.gtfs.schedule.model.GtfsScheduleDay;

import java.io.IOException;
import java.time.LocalDate;

public class GtfsScheduleBenchmark {


    public static void main(String[] args) throws IOException, InterruptedException {

        String path = GtfsScheduleBenchmarkData.get(GtfsScheduleBenchmarkData.Dataset.SWITZERLAND);
        GtfsSchedule schedule = new GtfsScheduleReader().read(path);
        GtfsScheduleDay scheduleDay = schedule.getScheduleForDay(LocalDate.now());
        System.gc();
        Thread.sleep(30000);
    }
}
