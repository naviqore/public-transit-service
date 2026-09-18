package org.naviqore.benchmark.raptor.router;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVPrinter;
import org.naviqore.benchmark.BenchmarkUtils;
import org.naviqore.gtfs.schedule.GtfsScheduleDataset;
import org.naviqore.gtfs.schedule.model.GtfsSchedule;
import org.naviqore.gtfs.schedule.model.Stop;
import org.naviqore.raptor.Connection;
import org.naviqore.raptor.QueryConfig;
import org.naviqore.raptor.RaptorAlgorithm;
import org.naviqore.raptor.router.RaptorConfig;
import org.naviqore.raptor.router.RaptorRouter;
import org.naviqore.service.gtfs.raptor.convert.GtfsToRaptorConverter;
import org.naviqore.service.gtfs.raptor.convert.GtfsTripMaskProvider;
import org.naviqore.utils.cache.EvictionCache;

import java.io.IOException;
import java.nio.file.Path;
import java.time.*;
import java.util.*;

/**
 * Benchmark for the RAPTOR routing algorithm.
 * <p>
 * Measures the time it takes to route a number of requests using Raptor algorithm on large GTFS datasets.
 *
 * @author munterfi
 */
@UtilityClass
@Slf4j
public class RaptorRouterBenchmark {

    // dataset
    private static final Path INPUT_DATA_DIRECTORY = Path.of("benchmark/input");
    private static final GtfsScheduleDataset DATASET = GtfsScheduleDataset.SWITZERLAND;
    private static final ZoneId ZONE_ID = ZoneId.of("Europe/Zurich");
    private static final LocalDate SCHEDULE_DATE = LocalDate.of(2026, Month.APRIL, 25);

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
    private static final int NOT_AVAILABLE = -1;
    private static final int SAME_STOP_TRANSFER_TIME = 120;
    private static final int MAX_DAYS_TO_SCAN = 3;
    private static final int RAPTOR_RANGE = -1; // No range raptor

    static void main() throws IOException, InterruptedException {
        LocalDateTime runTimestamp = LocalDateTime.now();
        GtfsSchedule schedule = BenchmarkUtils.initializeSchedule(DATASET, INPUT_DATA_DIRECTORY,
                MONITORING_INTERVAL_MS);
        List<RouteRequest> requests = sampleRouteRequests(schedule);
        RaptorAlgorithm raptor = initializeRaptor(schedule);
        List<RoutingResult> results = processRequests(raptor, requests);
        writeResultsToCsv(results, runTimestamp);
    }

    private static RaptorAlgorithm initializeRaptor(GtfsSchedule schedule) throws InterruptedException {
        RaptorConfig config = RaptorConfig.builder()
                .daysToScan(MAX_DAYS_TO_SCAN)
                .raptorRangeDefault(RAPTOR_RANGE)
                .sameStopTransferDurationDefault(SAME_STOP_TRANSFER_TIME)
                .stopTimeCacheSize(MAX_DAYS_TO_SCAN)
                .stopTimeCacheStrategy(EvictionCache.Strategy.LRU)
                .maskProvider(new GtfsTripMaskProvider(schedule))
                .build();
        RaptorRouter raptor = new GtfsToRaptorConverter(config, schedule).run();
        BenchmarkUtils.manageResources(MONITORING_INTERVAL_MS);

        for (int dayIndex = 0; dayIndex < MAX_DAYS_TO_SCAN; dayIndex++) {
            raptor.prepareStopTimesForDate(SCHEDULE_DATE.plusDays(dayIndex - 1));
        }
        BenchmarkUtils.manageResources(MONITORING_INTERVAL_MS);

        return raptor;
    }

    private static List<RouteRequest> sampleRouteRequests(GtfsSchedule schedule) {
        // sampling distinct source and destination stops requires at least two active stops
        List<String> stopIds = BenchmarkUtils.getActiveStopIds(schedule, SCHEDULE_DATE, 2);

        // sample
        Random random = new Random(RANDOM_SEED);
        List<RouteRequest> requests = new ArrayList<>(SAMPLE_SIZE);
        for (int i = 0; i < SAMPLE_SIZE; i++) {
            int sourceIndex = random.nextInt(stopIds.size());
            int destinationIndex = getRandomDestinationIndex(stopIds.size(), sourceIndex, random);

            OffsetDateTime departureTime = SCHEDULE_DATE.atStartOfDay()
                    .atZone(ZONE_ID)
                    .plusSeconds(random.nextInt(DEPARTURE_TIME_LIMIT))
                    .toOffsetDateTime();
            requests.add(new RouteRequest(schedule.getStops().get(stopIds.get(sourceIndex)),
                    schedule.getStops().get(stopIds.get(destinationIndex)), departureTime));
        }
        return requests;
    }

    private static int getRandomDestinationIndex(int size, int exclude, Random random) {
        int index = random.nextInt(size - 1);
        if (index >= exclude) index++;
        return index;
    }

    private static List<RoutingResult> processRequests(RaptorAlgorithm raptor, List<RouteRequest> requests) {
        List<RoutingResult> results = new ArrayList<>(requests.size());
        for (int i = 0; i < requests.size(); i++) {
            RouteRequest request = requests.get(i);
            long startTime = System.nanoTime();
            try {
                Map<String, OffsetDateTime> sourceStops = Map.of(request.sourceStop().getId(), request.departureTime());
                Map<String, Integer> targetStops = Map.of(request.targetStop().getId(), 0);

                List<Connection> connections = raptor.routeEarliestArrival(sourceStops, targetStops,
                        QueryConfig.defaults());
                long endTime = System.nanoTime();
                results.add(toResult(i, request, connections, startTime, endTime));
            } catch (IllegalArgumentException e) {
                log.error("Could not process route request: {}", e.getMessage());
            }

        }
        return results;
    }

    private static RoutingResult toResult(int id, RouteRequest request, List<Connection> connections, long startTime,
                                          long endTime) {
        Optional<OffsetDateTime> earliestDepartureTime = connections.stream()
                .map(Connection::getDepartureTime)
                .min(Comparator.naturalOrder());
        Optional<OffsetDateTime> earliestArrivalTime = connections.stream()
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
        long processingTime = BenchmarkUtils.elapsedMillis(startTime, endTime);
        return new RoutingResult(id, request.sourceStop().getId(), request.targetStop().getId(),
                request.sourceStop().getName(), request.targetStop.getName(), request.departureTime, connections.size(),
                earliestDepartureTime, earliestArrivalTime, minDuration, maxDuration, minTransfers, maxTransfers,
                beelineDistance, processingTime);
    }

    private static void writeResultsToCsv(List<RoutingResult> results, LocalDateTime runTimestamp) throws IOException {
        String[] headers = {"id", "source_stop_id", "target_stop_id", "source_stop_name", "target_stop_name",
                "requested_departure_time", "connections", "earliest_departure_time", "earliest_arrival_time",
                "min_duration_s", "max_duration_s", "min_transfers", "max_transfers", "beeline_distance_m",
                "processing_time_ms"};

        try (CSVPrinter writer = BenchmarkUtils.openCsv(DATASET, runTimestamp, "raptor_results", headers)) {
            for (RoutingResult result : results) {
                writer.printRecord(result.id(), result.sourceStopId(), result.targetStopId(), result.sourceStopName(),
                        result.targetStopName(), BenchmarkUtils.formatDateTime(result.requestedDepartureTime()),
                        result.connections(),
                        result.earliestDepartureTime().map(BenchmarkUtils::formatDateTime).orElse("N/A"),
                        result.earliestArrivalTime().map(BenchmarkUtils::formatDateTime).orElse("N/A"),
                        result.minDuration(), result.maxDuration(), result.minTransfers(), result.maxTransfers(),
                        result.beelineDistance(), result.processingTime());
            }
        }
    }

    record RouteRequest(Stop sourceStop, Stop targetStop, OffsetDateTime departureTime) {
    }

    record RoutingResult(int id, String sourceStopId, String targetStopId, String sourceStopName, String targetStopName,
                         OffsetDateTime requestedDepartureTime, int connections,
                         Optional<OffsetDateTime> earliestDepartureTime, Optional<OffsetDateTime> earliestArrivalTime,
                         int minDuration, int maxDuration, int minTransfers, int maxTransfers, long beelineDistance,
                         long processingTime) {
    }

}
