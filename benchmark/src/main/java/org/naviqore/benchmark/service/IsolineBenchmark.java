package org.naviqore.benchmark.service;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVPrinter;
import org.naviqore.benchmark.BenchmarkUtils;
import org.naviqore.gtfs.schedule.GtfsScheduleDataset;
import org.naviqore.gtfs.schedule.model.GtfsSchedule;
import org.naviqore.service.*;
import org.naviqore.service.config.ConnectionQueryConfig;
import org.naviqore.service.config.ServiceConfig;
import org.naviqore.service.exception.ConnectionRoutingException;
import org.naviqore.service.exception.StopNotFoundException;

import java.io.IOException;
import java.nio.file.Path;
import java.time.*;
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
@UtilityClass
@Slf4j
public class IsolineBenchmark {

    // dataset
    private static final Path INPUT_DATA_DIRECTORY = Path.of("benchmark/input");
    private static final GtfsScheduleDataset DATASET = GtfsScheduleDataset.SWITZERLAND;
    private static final ZoneId ZONE_ID = ZoneId.of("Europe/Zurich");
    private static final LocalDate SCHEDULE_DATE = LocalDate.of(2026, Month.APRIL, 25);

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
        LocalDateTime runTimestamp = LocalDateTime.now();
        GtfsSchedule schedule = BenchmarkUtils.initializeSchedule(DATASET, INPUT_DATA_DIRECTORY,
                MONITORING_INTERVAL_MS);
        PublicTransitService service = initializeService(schedule);
        List<IsolineRequest> requests = sampleIsolineRequests(schedule, service);
        List<IsolineResult> results = processRequests(service, requests);
        writeResultsToCsv(results, runTimestamp);
        writeSummaryToCsv(results, runTimestamp);
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
        BenchmarkUtils.manageResources(MONITORING_INTERVAL_MS);

        // abort early if the schedule date is outside the validity of the dataset, results would be meaningless
        Validity validity = service.getValidity();
        if (!validity.isWithin(SCHEDULE_DATE)) {
            throw new IllegalStateException(
                    String.format("Schedule date %s is outside the validity of the dataset [%s, %s].", SCHEDULE_DATE,
                            validity.getStartDate(), validity.getEndDate()));
        }

        return service;
    }

    private static List<IsolineRequest> sampleIsolineRequests(GtfsSchedule schedule,
                                                              PublicTransitService service) throws StopNotFoundException {
        // sampling a source stop requires at least one active stop
        List<String> stopIds = BenchmarkUtils.getActiveStopIds(schedule, SCHEDULE_DATE, 1);

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

        return requests;
    }

    private static List<IsolineResult> processRequests(PublicTransitService service, List<IsolineRequest> requests) {
        List<IsolineResult> results = new ArrayList<>(requests.size());
        for (int i = 0; i < requests.size(); i++) {
            IsolineRequest request = requests.get(i);
            ConnectionQueryConfig queryConfig = ConnectionQueryConfig.builder()
                    .maximumTravelDuration(request.scenario().maximumTravelDuration())
                    .timeWindowDuration(request.scenario().timeWindowDuration())
                    .build();

            long startTime = System.nanoTime();
            try {
                Map<Stop, Connection> isolines = service.getIsolines(request.sourceStop(), request.queryTime(),
                        TimeType.DEPARTURE, queryConfig);
                long endTime = System.nanoTime();
                IsolineResult result = toResult(i, request, isolines, startTime, endTime);
                results.add(result);
                log.info("{} from {} (run {}): {} isolines in {} ms", request.scenario().label(),
                        request.sourceStop().getName(), request.run(), result.isolines(), result.processingTime());
            } catch (ConnectionRoutingException e) {
                log.error("Could not process isoline request: {}", e.getMessage());
            }
        }
        return results;
    }

    private static IsolineResult toResult(int id, IsolineRequest request, Map<Stop, Connection> isolines,
                                          long startTime, long endTime) {
        long processingTime = BenchmarkUtils.elapsedMillis(startTime, endTime);
        return new IsolineResult(id, request.sourceStop().getId(), request.sourceStop().getName(), request.queryTime(),
                request.scenario(), request.run(), isolines.size(), processingTime);
    }

    private static void writeResultsToCsv(List<IsolineResult> results, LocalDateTime runTimestamp) throws IOException {
        String[] headers = {"id", "source_stop_id", "source_stop_name", "requested_departure_time", "scenario",
                "max_travel_duration_s", "time_window_duration_s", "run", "isolines", "processing_time_ms"};

        try (CSVPrinter writer = BenchmarkUtils.openCsv(DATASET, runTimestamp, "isoline_results", headers)) {
            for (IsolineResult result : results) {
                writer.printRecord(result.id(), result.sourceStopId(), result.sourceStopName(),
                        BenchmarkUtils.formatDateTime(result.requestedDepartureTime()), result.scenario().label(),
                        result.scenario().maximumTravelDuration(), result.scenario().timeWindowDuration(), result.run(),
                        result.isolines(), result.processingTime());
            }
        }
    }

    /**
     * Aggregates the runs per source stop and scenario. The number of isolines must be identical across the runs of the
     * same request, a deviation indicates non-deterministic routing results.
     */
    private static void writeSummaryToCsv(List<IsolineResult> results, LocalDateTime runTimestamp) throws IOException {
        String[] headers = {"source_stop_id", "source_stop_name", "scenario", "runs", "min_isolines", "max_isolines",
                "consistent", "min_processing_time_ms", "max_processing_time_ms", "avg_processing_time_ms"};

        // group by source stop and scenario, insertion order preserved
        Map<String, List<IsolineResult>> groups = new LinkedHashMap<>();
        for (IsolineResult result : results) {
            String key = result.sourceStopId() + "|" + result.scenario().label();
            groups.computeIfAbsent(key, _ -> new ArrayList<>()).add(result);
        }

        try (CSVPrinter writer = BenchmarkUtils.openCsv(DATASET, runTimestamp, "isoline_summary", headers)) {
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

                writer.printRecord(first.sourceStopId(), first.sourceStopName(), first.scenario().label(), group.size(),
                        isolines.getMin(), isolines.getMax(), consistent, times.getMin(), times.getMax(),
                        Math.round(times.getAverage()));
            }
        }
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
