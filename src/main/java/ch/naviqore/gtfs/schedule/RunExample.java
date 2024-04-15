package ch.naviqore.gtfs.schedule;

import ch.naviqore.gtfs.schedule.model.GtfsSchedule;
import ch.naviqore.gtfs.schedule.model.GtfsScheduleDay;

import java.io.IOException;
import java.time.LocalDate;

public class RunExample {
    private static final String GTFS_FILE = "/Users/munterfi/Downloads/gtfs_fp2024_2024-04-11_09-11.zip";

    public static void main(String[] args) throws IOException, InterruptedException {
        GtfsSchedule schedule = new GtfsScheduleReader().read(GTFS_FILE);
        GtfsScheduleDay scheduleDay = schedule.getScheduleForDay(LocalDate.now());
        System.gc();
        Thread.sleep(30000);
    }
}
