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

    private static long routeEarliestArrival(String fromStopId, String toStopId) {

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

        return endTime - startTime;
    }

    private static void benchmarkRoute(String fromStopId, String toStopId, int iterations) {
        long totalTime = 0;
        for (int i = 0; i < iterations; i++) {
            totalTime += routeEarliestArrival(fromStopId, toStopId);
        }
        long averageTime = totalTime / iterations;
        System.out.println(
                "Average time for routing from " + fromStopId + " to " + toStopId + " over " + iterations + " iterations: " + averageTime / 1_000_000 + " ms");
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        setUp();
        // "8589640" "St. Gallen, Vonwil" to "8579885" "Mels, Bahnhof"
        benchmarkRoute("8589640", "8579885", 100);
        // "8574563","Maienfeld, Bahnhof" to "8587276" "Biel/Bienne, Taubenloch"
        benchmarkRoute("8574563", "8587276", 100);
        // "8588524","Sion, Hôpital Sud" to "8508896","Stans, Bahnhof"
        benchmarkRoute("8588524", "8508896", 100);
        // "8510709","Lugano, Via Domenico Fontana" to "8579255","Lausanne, Pont-de-Chailly"
        benchmarkRoute("8510709", "8579255", 100);
        // "8574848","Davos Dorf, Bahnhof" to "8576079","Rapperswil SG, Sonnenhof"
        benchmarkRoute("8574848", "8576079", 100);
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