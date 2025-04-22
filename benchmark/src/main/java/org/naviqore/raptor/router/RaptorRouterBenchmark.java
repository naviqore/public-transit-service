package org.naviqore.raptor.router;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.naviqore.gtfs.schedule.GtfsScheduleDataset;
import org.naviqore.gtfs.schedule.GtfsScheduleReader;
import org.naviqore.gtfs.schedule.model.GtfsSchedule;
import org.naviqore.gtfs.schedule.model.Stop;
import org.naviqore.gtfs.schedule.model.StopTime;
import org.naviqore.gtfs.schedule.model.Trip;
import org.naviqore.raptor.Connection;
import org.naviqore.raptor.QueryConfig;
import org.naviqore.raptor.RaptorAlgorithm;
import org.naviqore.service.gtfs.raptor.convert.GtfsToRaptorConverter;
import org.naviqore.service.gtfs.raptor.convert.GtfsTripMaskProvider;
import org.naviqore.utils.cache.EvictionCache;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * org.naviqore.raptor.Benchmark for Raptor routing algorithm.
 * <p>
 * Measures the time it takes to route a number of requests using Raptor algorithm on large GTFS datasets.
 * <p>
 * Note: To run this benchmark, ensure that the log level is set to INFO in the
 * {@code src/test/resources/logback-test.xml} file.
 *
 * @author munterfi
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
public final class RaptorRouterBenchmark {

    // dataset
    private static final Path INPUT_DATA_DIRECTORY = Path.of("benchmark/input");
    private static final GtfsScheduleDataset DATASET = GtfsScheduleDataset.SWITZERLAND;
    private static final LocalDate SCHEDULE_DATE = LocalDate.of(2025, 4, 26);

    // sampling
    /**
     * Limit in seconds after midnight for the departure time. Only allow early departure times, otherwise many
     * connections crossing the complete schedule (region) are not feasible.
     */
    private static final int DEPARTURE_TIME_LIMIT = 24 * 60 * 60;
    private static final long RANDOM_SEED = 1234;
    private static final int SAMPLE_SIZE = 10_000;

    // constants
    private static final long MONITORING_INTERVAL_MS = 30000;
    private static final int NS_TO_MS_CONVERSION_FACTOR = 1_000_000;
    private static final int NOT_AVAILABLE = -1;
    private static final int SAME_STOP_TRANSFER_TIME = 120;
    private static final int MAX_DAYS_TO_SCAN = 3;
    private static final int RAPTOR_RANGE = -1; // No range raptor

    public static void main(String[] args) throws IOException, InterruptedException {
        GtfsSchedule schedule = initializeSchedule();
        RaptorAlgorithm raptor = initializeRaptor(schedule);
        RouteRequest[] requests = sampleRouteRequests(schedule);
        RoutingResult[] results = processRequests(raptor, requests);
        writeResultsToCsv(results);
    }

    private static GtfsSchedule initializeSchedule() throws IOException, InterruptedException {
        File file = DATASET.getZip(INPUT_DATA_DIRECTORY);
        GtfsSchedule schedule = new GtfsScheduleReader().read(file.getPath());
        manageResources();
        return schedule;
    }

    private static RaptorAlgorithm initializeRaptor(GtfsSchedule schedule) throws InterruptedException {
        RaptorConfig config = new RaptorConfig(MAX_DAYS_TO_SCAN, RAPTOR_RANGE, SAME_STOP_TRANSFER_TIME,
                MAX_DAYS_TO_SCAN, EvictionCache.Strategy.LRU, new GtfsTripMaskProvider(schedule));
        RaptorRouter raptor = new GtfsToRaptorConverter(config, schedule).run();
        manageResources();

        for (int dayIndex = 0; dayIndex < MAX_DAYS_TO_SCAN; dayIndex++) {
            raptor.prepareStopTimesForDate(SCHEDULE_DATE.plusDays(dayIndex - 1));
        }
        manageResources();

        return raptor;
    }

    private static void manageResources() throws InterruptedException {
        System.gc();
        Thread.sleep(MONITORING_INTERVAL_MS);
    }

    private static RouteRequest[] sampleRouteRequests(GtfsSchedule schedule) {
        // extract valid stops for day
        Set<String> uniqueStopIds = new HashSet<>();
        for (Trip trip : schedule.getActiveTrips(SCHEDULE_DATE)) {
            for (StopTime stopTime : trip.getStopTimes()) {
                uniqueStopIds.add(stopTime.stop().getId());
            }
        }
        List<String> stopIds = new ArrayList<>(uniqueStopIds);

        // sample
        Random random = new Random(RANDOM_SEED);
        RouteRequest[] requests = new RouteRequest[SAMPLE_SIZE];
        for (int i = 0; i < SAMPLE_SIZE; i++) {
            int sourceIndex = random.nextInt(stopIds.size());
            int destinationIndex = getRandomDestinationIndex(stopIds.size(), sourceIndex, random);

            LocalDateTime departureTime = SCHEDULE_DATE.atStartOfDay()
                    .plusSeconds(random.nextInt(DEPARTURE_TIME_LIMIT));
            requests[i] = new RouteRequest(schedule.getStops().get(stopIds.get(sourceIndex)),
                    schedule.getStops().get(stopIds.get(destinationIndex)), departureTime);
        }
        return requests;
    }

    private static int getRandomDestinationIndex(int size, int exclude, Random random) {
        int index = random.nextInt(size - 1);
        if (index >= exclude) index++;
        return index;
    }

    private static RoutingResult[] processRequests(RaptorAlgorithm raptor, RouteRequest[] requests) {
        RoutingResult[] responses = new RoutingResult[requests.length];
        for (int i = 0; i < requests.length; i++) {
            long startTime = System.nanoTime();
            try {
                Map<String, LocalDateTime> sourceStops = Map.of(requests[i].sourceStop().getId(),
                        requests[i].departureTime());
                Map<String, Integer> targetStops = Map.of(requests[i].targetStop().getId(), 0);

                List<Connection> connections = raptor.routeEarliestArrival(sourceStops, targetStops, new QueryConfig());
                long endTime = System.nanoTime();
                responses[i] = toResult(i, requests[i], connections, startTime, endTime);
            } catch (IllegalArgumentException e) {
                log.error("Could not process route request: {}", e.getMessage());
            }

        }
        return responses;
    }

    private static RoutingResult toResult(int id, RouteRequest request, List<Connection> connections, long startTime,
                                          long endTime) {
        Optional<LocalDateTime> earliestDepartureTime = connections.stream()
                .map(Connection::getDepartureTime)
                .min(Comparator.naturalOrder());
        Optional<LocalDateTime> earliestArrivalTime = connections.stream()
                .map(Connection::getArrivalTime)
                .min(Comparator.naturalOrder());
        int minDuration = connections.stream().mapToInt(Connection::getDurationInSeconds).min().orElse(NOT_AVAILABLE);
        int maxDuration = connections.stream().mapToInt(Connection::getDurationInSeconds).max().orElse(NOT_AVAILABLE);
        int minTransfers = connections.stream()
                .mapToInt(Connection::getNumberOfTotalTransfers)
                .min()
                .orElse(NOT_AVAILABLE);
        int maxTransfers = connections.stream()
                .mapToInt(Connection::getNumberOfTotalTransfers)
                .max()
                .orElse(NOT_AVAILABLE);
        long beelineDistance = Math.round(
                request.sourceStop.getCoordinate().distanceTo(request.targetStop.getCoordinate()));
        long processingTime = (endTime - startTime) / NS_TO_MS_CONVERSION_FACTOR;
        return new RoutingResult(id, request.sourceStop().getId(), request.targetStop().getId(),
                request.sourceStop().getName(), request.targetStop.getName(), request.departureTime, connections.size(),
                earliestDepartureTime, earliestArrivalTime, minDuration, maxDuration, minTransfers, maxTransfers,
                beelineDistance, processingTime);
    }

    private static void writeResultsToCsv(RoutingResult[] results) throws IOException {
        String[] headers = {"id", "source_stop_id", "target_stop_id", "source_stop_name", "target_stop_name",
                "requested_departure_time", "connections", "earliest_departure_time", "earliest_arrival_time",
                "min_duration", "max_duration", "min_transfers", "max_transfers", "beeline_distance",
                "processing_time_ms"};
        String header = String.join(",", headers);
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
                writer.printf("%d,%s,%s,\"%s\",\"%s\",%s,%d,%s,%s,%d,%d,%d,%d,%d,%d%n", result.id, result.sourceStopId,
                        result.targetStopId, result.sourceStopName, result.targetStopName,
                        result.requestedDepartureTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), result.connections,
                        result.earliestDepartureTime.map(dt -> dt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                                .orElse("N/A"),
                        result.earliestArrivalTime.map(dt -> dt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                                .orElse("N/A"), result.minDuration, result.maxDuration, result.minTransfers,
                        result.maxTransfers, result.beelineDistance, result.processingTime);
            }
        }
    }

    record RouteRequest(Stop sourceStop, Stop targetStop, LocalDateTime departureTime) {
    }

    record RoutingResult(int id, String sourceStopId, String targetStopId, String sourceStopName, String targetStopName,
                         LocalDateTime requestedDepartureTime, int connections,
                         Optional<LocalDateTime> earliestDepartureTime, Optional<LocalDateTime> earliestArrivalTime,
                         int minDuration, int maxDuration, int minTransfers, int maxTransfers, long beelineDistance,
                         long processingTime) {
    }

}
