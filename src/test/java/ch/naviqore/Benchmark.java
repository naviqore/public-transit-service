package ch.naviqore;

import ch.naviqore.BenchmarkData.Dataset;
import ch.naviqore.gtfs.schedule.GtfsScheduleReader;
import ch.naviqore.gtfs.schedule.model.GtfsSchedule;
import ch.naviqore.raptor.GtfsToRaptorConverter;
import ch.naviqore.raptor.model.Raptor;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Benchmark for Raptor routing algorithm.
 * <p>
 * Measures the time it takes to route a number of requests using Raptor algorithm on large GTFS datasets.
 * <p>
 * Note: To run this benchmark, ensure that the log level is set to INFO in the
 * {@code src/test/resources/log4j2.properties} file.
 *
 * @author munterfi
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class Benchmark {

    private static final int N = 10000;
    private static final Dataset DATASET = Dataset.SWITZERLAND;
    private static final LocalDate SCHEDULE_DATE = LocalDate.of(2024, 4, 26);
    private static final int SECONDS_IN_DAY = 86400;
    private static final long MONITORING_INTERVAL_MS = 30000;
    private static final int NS_TO_MS_CONVERSION_FACTOR = 1_000_000;

    public static void main(String[] args) throws IOException, InterruptedException {
        GtfsSchedule schedule = initializeSchedule();
        Raptor raptor = initializeRaptor(schedule);
        List<String> stopIds = new ArrayList<>(schedule.getStops().keySet());
        RouteRequest[] requests = sampleRouteRequests(stopIds);
        RoutingResult[] results = processRequests(raptor, requests);
        writeResultsToCsv(results);
    }

    private static GtfsSchedule initializeSchedule() throws IOException, InterruptedException {
        String path = BenchmarkData.get(DATASET);
        GtfsSchedule schedule = new GtfsScheduleReader().read(path);
        manageResources();
        return schedule;
    }

    private static Raptor initializeRaptor(GtfsSchedule schedule) throws InterruptedException {
        Raptor raptor = new GtfsToRaptorConverter(schedule).convert(SCHEDULE_DATE);
        manageResources();
        return raptor;
    }

    private static void manageResources() throws InterruptedException {
        System.gc();
        Thread.sleep(MONITORING_INTERVAL_MS);
    }

    private static RouteRequest[] sampleRouteRequests(List<String> stopIds) {
        Random random = new Random();
        RouteRequest[] requests = new RouteRequest[Benchmark.N];
        for (int i = 0; i < Benchmark.N; i++) {
            int sourceIndex = random.nextInt(stopIds.size());
            int destinationIndex = getRandomDestinationIndex(stopIds.size(), sourceIndex, random);
            requests[i] = new RouteRequest(stopIds.get(sourceIndex), stopIds.get(destinationIndex),
                    random.nextInt(SECONDS_IN_DAY));
        }
        return requests;
    }

    private static int getRandomDestinationIndex(int size, int exclude, Random random) {
        int index = random.nextInt(size - 1);
        if (index >= exclude) index++;
        return index;
    }

    private static RoutingResult[] processRequests(Raptor raptor, RouteRequest[] requests) {
        RoutingResult[] responses = new RoutingResult[requests.length];
        for (int i = 0; i < requests.length; i++) {
            long startTime = System.nanoTime();
            // TODO: RaptorResponse result =
            raptor.routeEarliestArrival(requests[i].sourceStop(), requests[i].targetStop(),
                    requests[i].departureTime());
            long endTime = System.nanoTime();
            responses[i] = new RoutingResult(requests[i].sourceStop(), requests[i].targetStop(),
                    requests[i].departureTime(), 0, 0, 0, (endTime - startTime) / NS_TO_MS_CONVERSION_FACTOR);
        }
        return responses;
    }

    private static void writeResultsToCsv(RoutingResult[] results) throws IOException {
        String header = "source_stop,target_stop,requested_departure_time,departure_time,arrival_time,transfers,processing_time_ms";
        String folderPath = String.format("benchmark/output/%s", DATASET.name().toLowerCase());
        String fileName = String.format("%s_raptor_results.csv",
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy_MM_dd_HH_mm_ss")));
        Path directoryPath = Paths.get(folderPath);
        if (!Files.exists(directoryPath)) {
            Files.createDirectories(directoryPath);
        }
        Path filePath = directoryPath.resolve(fileName);

        try (PrintWriter writer = new PrintWriter(
                Files.newBufferedWriter(filePath, StandardOpenOption.CREATE, StandardOpenOption.WRITE))) {
            writer.println(header);

            for (RoutingResult result : results) {
                writer.printf("%s,%s,%d,%d,%d,%d,%d%n", result.sourceStop(), result.targetStop(),
                        result.requestedDepartureTime(), result.departureTime(), result.arrivalTime(),
                        result.transfers(), result.time());
            }
        }
    }

    record RouteRequest(String sourceStop, String targetStop, int departureTime) {
    }

    record RoutingResult(String sourceStop, String targetStop, int requestedDepartureTime, int departureTime,
                         int arrivalTime, int transfers, long time) {
    }
}
