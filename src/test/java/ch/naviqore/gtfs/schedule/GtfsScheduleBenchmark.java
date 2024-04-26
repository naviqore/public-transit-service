package ch.naviqore.gtfs.schedule;

import ch.naviqore.gtfs.schedule.GtfsScheduleBenchmarkData.Dataset;
import ch.naviqore.gtfs.schedule.model.GtfsSchedule;
import ch.naviqore.gtfs.schedule.model.Trip;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class GtfsScheduleBenchmark {

    private static final Dataset DATASET = Dataset.SWITZERLAND;

    public static void main(String[] args) throws IOException, InterruptedException {
        String path = GtfsScheduleBenchmarkData.get(DATASET);
        GtfsSchedule schedule = new GtfsScheduleReader().read(path);
        List<Trip> activeTrips = schedule.getActiveTrips(LocalDate.now());
        // clean heap from reading artifacts
        System.gc();
        // monitor effect
        Thread.sleep(30000);
    }
}
