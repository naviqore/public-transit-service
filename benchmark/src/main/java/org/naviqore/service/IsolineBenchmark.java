package org.naviqore.service;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.naviqore.gtfs.schedule.GtfsScheduleDataset;
import org.naviqore.gtfs.schedule.GtfsScheduleReader;
import org.naviqore.gtfs.schedule.model.GtfsSchedule;
import org.naviqore.gtfs.schedule.model.StopTime;
import org.naviqore.gtfs.schedule.model.Trip;
import org.naviqore.service.config.ConnectionQueryConfig;
import org.naviqore.service.config.ServiceConfig;
import org.naviqore.service.exception.ConnectionRoutingException;
import org.naviqore.service.exception.StopNotFoundException;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Benchmark for isoline queries on the public transit service layer.
 * <p>
 * Measures the time it takes to compute isolines for a fixed set of scenarios from a sample of source stops on large
 * GTFS datasets. Each scenario is executed several times per source stop, which allows to detect non-deterministic
 * results in addition to measuring the processing time. Intended to be run on different branches with the same
 * configuration to compare performance and correctness.
 *
 * @author munterfi
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
public final class IsolineBenchmark {

    // dataset
    private static final Path INPUT_DATA_DIRECTORY = Path.of("benchmark/input");
    private static final GtfsScheduleDataset DATASET = GtfsScheduleDataset.SWITZERLAND;
    private static final ZoneId ZONE_ID = ZoneId.of("Europe/Zurich");
    private static final LocalDate SCHEDULE_DATE = LocalDate.of(2026, 4, 25);

    // sampling
    /**
     * Time of day for all isoline queries. Chosen at noon so that the time windows of the scenarios lie within the
     * regular service hours.
     */
    private static final LocalTime QUERY_TIME = LocalTime.of(12, 0);
    private static final long RANDOM_SEED = 1234;
    private static final int SAMPLE_SIZE = 10;
    /**
     * Number of times each scenario is executed per source stop. The first execution of each scenario warms up the stop
     * time cache, the subsequent ones reflect the steady state.
     */
    private static final int RUNS_PER_SCENARIO = 3;

    // constants
    private static final long MONITORING_INTERVAL_MS = 30000;
    private static final int NS_TO_MS_CONVERSION_FACTOR = 1_000_000;
    private static final int MAX_DAYS_TO_SCAN = 3;
    private static final int RAPTOR_RANGE = -1; // No range raptor
    /**
     * Minimum walk duration in seconds, mirrors the default of the application properties so that results are
     * comparable to the live service.
     */
    private static final int WALK_DURATION_MINIMUM = 60;

    // scenarios
    private static final List<Scenario> SCENARIOS = List.of(new Scenario("30min_travel_60min_window", 30 * 60, 60 * 60),
            new Scenario("120min_travel_240min_window", 120 * 60, 240 * 60),
            new Scenario("30min_travel_no_window", 30 * 60, 0));

    static void main() throws IOException, InterruptedException, StopNotFoundException {
        GtfsSchedule schedule = initializeSchedule();
        PublicTransitService service = initializeService(schedule);
        IsolineRequest[] requests = sampleIsolineRequests(schedule, service);
        IsolineResult[] results = processRequests(service, requests);
        writeResultsToCsv(results);
        writeSummaryToCsv(results);
    }

    private static GtfsSchedule initializeSchedule() throws IOException, InterruptedException {
        File file = DATASET.getZip(INPUT_DATA_DIRECTORY);
        GtfsSchedule schedule = new GtfsScheduleReader().read(file.getPath());
        manageResources();
        return schedule;
    }

    private static PublicTransitService initializeService(
            GtfsSchedule schedule) throws IOException, InterruptedException {
        ServiceConfig config = ServiceConfig.builder()
                .gtfsScheduleRepository(() -> schedule)
                .raptorDaysToScan(MAX_DAYS_TO_SCAN)
                .raptorRange(RAPTOR_RANGE)
                .walkDurationMinimum(WALK_DURATION_MINIMUM)
                .build();
        PublicTransitService service = new PublicTransitServiceFactory(config).create();
        manageResources();

        // abort early if the schedule date is outside the validity of the dataset, results would be meaningless
        Validity validity = service.getValidity();
        if (!validity.isWithin(SCHEDULE_DATE)) {
            throw new IllegalStateException(
                    String.format("Schedule date %s is outside the validity of the dataset [%s, %s].", SCHEDULE_DATE,
                            validity.getStartDate(), validity.getEndDate()));
        }

        return service;
    }

    private static void manageResources() throws InterruptedException {
        System.gc();
        Thread.sleep(MONITORING_INTERVAL_MS);
    }

    private static IsolineRequest[] sampleIsolineRequests(GtfsSchedule schedule,
                                                          PublicTransitService service) throws StopNotFoundException {
        // extract valid stops for day, sorted to ensure a reproducible sample for the seed
        Set<String> uniqueStopIds = new HashSet<>();
        for (Trip trip : schedule.getActiveTrips(SCHEDULE_DATE)) {
            for (StopTime stopTime : trip.getStopTimes()) {
                uniqueStopIds.add(stopTime.stop().getId());
            }
        }
        List<String> stopIds = new ArrayList<>(uniqueStopIds);
        Collections.sort(stopIds);

        // sample source stops, each is combined with every scenario and executed several times
        Random random = new Random(RANDOM_SEED);
        OffsetDateTime queryTime = SCHEDULE_DATE.atTime(QUERY_TIME).atZone(ZONE_ID).toOffsetDateTime();
        List<IsolineRequest> requests = new ArrayList<>(SAMPLE_SIZE * SCENARIOS.size() * RUNS_PER_SCENARIO);
        for (int i = 0; i < SAMPLE_SIZE; i++) {
            Stop sourceStop = service.getStopById(stopIds.get(random.nextInt(stopIds.size())));
            for (Scenario scenario : SCENARIOS) {
                for (int run = 1; run <= RUNS_PER_SCENARIO; run++) {
                    requests.add(new IsolineRequest(sourceStop, queryTime, scenario, run));
                }
            }
        }

        return requests.toArray(new IsolineRequest[0]);
    }

    private static IsolineResult[] processRequests(PublicTransitService service, IsolineRequest[] requests) {
        IsolineResult[] results = new IsolineResult[requests.length];
        for (int i = 0; i < requests.length; i++) {
            IsolineRequest request = requests[i];
            ConnectionQueryConfig queryConfig = ConnectionQueryConfig.builder()
                    .maximumTravelDuration(request.scenario().maximumTravelDuration())
                    .timeWindowDuration(request.scenario().timeWindowDuration())
                    .build();

            long startTime = System.nanoTime();
            try {
                Map<Stop, Connection> isolines = service.getIsolines(request.sourceStop(), request.queryTime(),
                        TimeType.DEPARTURE, queryConfig);
                long endTime = System.nanoTime();
                results[i] = toResult(i, request, isolines, startTime, endTime);
                log.info("{} from {} (run {}): {} isolines in {} ms", request.scenario().label(),
                        request.sourceStop().getName(), request.run(), results[i].isolines(),
                        results[i].processingTime());
            } catch (ConnectionRoutingException e) {
                log.error("Could not process isoline request: {}", e.getMessage());
            }
        }
        return results;
    }

    private static IsolineResult toResult(int id, IsolineRequest request, Map<Stop, Connection> isolines,
                                          long startTime, long endTime) {
        long processingTime = (endTime - startTime) / NS_TO_MS_CONVERSION_FACTOR;
        return new IsolineResult(id, request.sourceStop().getId(), request.sourceStop().getName(), request.queryTime(),
                request.scenario(), request.run(), isolines.size(), processingTime);
    }

    private static void writeResultsToCsv(IsolineResult[] results) throws IOException {
        String[] headers = {"id", "source_stop_id", "source_stop_name", "requested_departure_time", "scenario",
                "max_travel_duration", "time_window_duration", "run", "isolines", "processing_time_ms"};

        try (PrintWriter writer = openWriter("isoline_results")) {
            writer.println(String.join(",", headers));
            for (IsolineResult result : results) {
                if (result == null) {
                    continue;
                }
                writer.printf("%d,%s,\"%s\",%s,%s,%d,%d,%d,%d,%d%n", result.id(), result.sourceStopId(),
                        result.sourceStopName(),
                        result.requestedDepartureTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                        result.scenario().label(), result.scenario().maximumTravelDuration(),
                        result.scenario().timeWindowDuration(), result.run(), result.isolines(),
                        result.processingTime());
            }
        }
    }

    /**
     * Aggregates the runs per source stop and scenario. The number of isolines must be identical across the runs of the
     * same request, a deviation indicates non-deterministic routing results.
     */
    private static void writeSummaryToCsv(IsolineResult[] results) throws IOException {
        String[] headers = {"source_stop_id", "source_stop_name", "scenario", "runs", "min_isolines", "max_isolines",
                "consistent", "min_processing_time_ms", "max_processing_time_ms", "avg_processing_time_ms"};

        // group by source stop and scenario, insertion order preserved
        Map<String, List<IsolineResult>> groups = new LinkedHashMap<>();
        for (IsolineResult result : results) {
            if (result == null) {
                continue;
            }
            String key = result.sourceStopId() + "|" + result.scenario().label();
            groups.computeIfAbsent(key, _ -> new ArrayList<>()).add(result);
        }

        try (PrintWriter writer = openWriter("isoline_summary")) {
            writer.println(String.join(",", headers));
            for (List<IsolineResult> group : groups.values()) {
                IsolineResult first = group.getFirst();
                IntSummaryStatistics isolines = group.stream().mapToInt(IsolineResult::isolines).summaryStatistics();
                LongSummaryStatistics times = group.stream()
                        .mapToLong(IsolineResult::processingTime)
                        .summaryStatistics();
                boolean consistent = isolines.getMin() == isolines.getMax();

                if (!consistent) {
                    log.warn("{} from {}: number of isolines varies across runs (min={}, max={})",
                            first.scenario().label(), first.sourceStopName(), isolines.getMin(), isolines.getMax());
                }

                writer.printf("%s,\"%s\",%s,%d,%d,%d,%b,%d,%d,%d%n", first.sourceStopId(), first.sourceStopName(),
                        first.scenario().label(), group.size(), isolines.getMin(), isolines.getMax(), consistent,
                        times.getMin(), times.getMax(), Math.round(times.getAverage()));
            }
        }
    }

    private static PrintWriter openWriter(String suffix) throws IOException {
        String folderPath = String.format("benchmark/output/%s", DATASET.name().toLowerCase());
        String fileName = String.format("%s_%s.csv",
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy_MM_dd_HH_mm_ss")), suffix);
        Path directoryPath = Paths.get(folderPath);
        if (!Files.exists(directoryPath)) {
            Files.createDirectories(directoryPath);
        }
        Path filePath = directoryPath.resolve(fileName);

        return new PrintWriter(Files.newBufferedWriter(filePath, StandardOpenOption.CREATE, StandardOpenOption.WRITE));
    }

    /**
     * @param label                 identifier of the scenario used in the output
     * @param maximumTravelDuration maximum travel duration in seconds
     * @param timeWindowDuration    time window duration in seconds, 0 disables the time window
     */
    record Scenario(String label, int maximumTravelDuration, int timeWindowDuration) {
    }

    record IsolineRequest(Stop sourceStop, OffsetDateTime queryTime, Scenario scenario, int run) {
    }

    record IsolineResult(int id, String sourceStopId, String sourceStopName, OffsetDateTime requestedDepartureTime,
                         Scenario scenario, int run, int isolines, long processingTime) {
    }

}
