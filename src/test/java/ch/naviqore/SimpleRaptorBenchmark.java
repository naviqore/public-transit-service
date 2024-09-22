package ch.naviqore;

import ch.naviqore.gtfs.schedule.GtfsScheduleReader;
import ch.naviqore.gtfs.schedule.model.GtfsSchedule;
import ch.naviqore.gtfs.schedule.model.Stop;
import ch.naviqore.raptor.Connection;
import ch.naviqore.raptor.QueryConfig;
import ch.naviqore.raptor.RaptorAlgorithm;
import ch.naviqore.raptor.simple.gtfs.Converter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
public class SimpleRaptorBenchmark {

    private static final LocalDate SCHEDULE_DATE = LocalDate.of(2024, 4, 26);
    private static final int SAME_STOP_TRANSFER_TIME = 120;
    private static final BenchmarkData.Dataset DATASET = BenchmarkData.Dataset.SWITZERLAND;
    private static GtfsSchedule schedule;
    private static boolean initialized = false;
    private static RaptorAlgorithm simpleRaptor;

    private static void setUp() throws IOException, InterruptedException {
        if (initialized) {
            return;
        }
        schedule = initializeSchedule();
        simpleRaptor = initializeSimpleRaptorRouter(schedule);
        initialized = true;
    }

    private static void routeEarliestArrival(String fromStopId, String toStopId) {

        Stop fromStop = schedule.getStops().get(fromStopId);
        Stop toStop = schedule.getStops().get(toStopId);

        if (fromStop == null || toStop == null) {
            System.err.println("Invalid stop IDs provided.");
            System.exit(1);
        }

        RouteRequest request = new RouteRequest(fromStop, toStop, LocalDateTime.now());
        Map<String, LocalDateTime> stops = Map.of(request.sourceStop().getId(), request.departureTime());
        Map<String, Integer> targetStops = Map.of(request.targetStop().getId(), 0);
        long startTime = System.nanoTime();
        List<Connection> connections = simpleRaptor.routeEarliestArrival(stops, targetStops, new QueryConfig());
        long endTime = System.nanoTime();

        System.out.println(
                "Route from " + fromStop.getId() + " to " + toStop.getId() + " time in ms: " + (endTime - startTime) / 1_000_000);
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        setUp();
        routeEarliestArrival("8589640", "8579885");
        routeEarliestArrival("8588889", "8589644");
    }

    private static GtfsSchedule initializeSchedule() throws IOException, InterruptedException {
        String path = BenchmarkData.get(BenchmarkData.Dataset.SWITZERLAND);
        return new GtfsScheduleReader().read(path);
    }

    private static RaptorAlgorithm initializeSimpleRaptorRouter(GtfsSchedule schedule) throws InterruptedException {
        return new Converter(schedule, SAME_STOP_TRANSFER_TIME).convert(SCHEDULE_DATE);
    }

    record RouteRequest(Stop sourceStop, Stop targetStop, LocalDateTime departureTime) {
    }

}