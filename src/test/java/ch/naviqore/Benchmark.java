package ch.naviqore;

import ch.naviqore.BenchmarkData.Dataset;
import ch.naviqore.gtfs.schedule.GtfsScheduleReader;
import ch.naviqore.gtfs.schedule.model.GtfsSchedule;
import ch.naviqore.gtfs.schedule.model.Stop;
import ch.naviqore.gtfs.schedule.model.StopTime;
import ch.naviqore.gtfs.schedule.model.Trip;
import ch.naviqore.gtfs.schedule.type.ServiceDayTime;
import ch.naviqore.raptor.Connection;
import ch.naviqore.raptor.QueryConfig;
import ch.naviqore.raptor.Raptor;
import ch.naviqore.raptor.TimeType;
import ch.naviqore.service.impl.convert.GtfsToRaptorConverter;
import ch.naviqore.service.impl.transfer.SameStopTransferGenerator;
import ch.naviqore.service.impl.transfer.TransferGenerator;
import ch.naviqore.service.impl.transfer.WalkTransferGenerator;
import ch.naviqore.service.walk.BeeLineWalkCalculator;
import ch.naviqore.utils.spatial.index.KDTree;
import ch.naviqore.utils.spatial.index.KDTreeBuilder;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;

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
 * Benchmark for Raptor routing algorithm.
 * <p>
 * Measures the time it takes to route a number of requests using Raptor algorithm on large GTFS datasets.
 * <p>
 * Note: To run this benchmark, ensure that the log level is set to INFO in the
 * {@code src/test/resources/log4j2-test.properties} file.
 *
 * @author munterfi
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Log4j2
final class Benchmark {

    // dataset
    private static final Dataset DATASET = Dataset.SWITZERLAND;
    private static final LocalDate SCHEDULE_DATE = LocalDate.of(2024, 4, 26);

    // sampling
    /**
     * Limit in seconds after midnight for the departure time. Only allow early departure times, otherwise many
     * connections crossing the complete schedule (region) are not feasible.
     */
    private static final int DEPARTURE_TIME_LIMIT = 8 * 60 * 60;
    private static final long RANDOM_SEED = 1234;
    private static final int SAMPLE_SIZE = 10000;

    // constants
    private static final long MONITORING_INTERVAL_MS = 30000;
    private static final int NS_TO_MS_CONVERSION_FACTOR = 1_000_000;
    private static final int NOT_AVAILABLE = -1;
    private static final int WALKING_SPEED = 3000;
    private static final int MINIMUM_TRANSFER_TIME = 120;
    private static final int SAME_STOP_TRANSFER_TIME = 120;
    private static final int ACCESS_EGRESS_TIME = 15;
    private static final int SEARCH_RADIUS = 500;

    public static void main(String[] args) throws IOException, InterruptedException {
        GtfsSchedule schedule = initializeSchedule();
        Raptor raptor = initializeRaptor(schedule);
        RouteRequest[] requests = sampleRouteRequests(schedule);
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
        // TODO: This should be implemented in the new integration service and should not need to run everytime a raptor
        //  instance is created. Ideally this will be handled as an attribute with a list of transfer generators. With
        //  this approach, transfers can be generated according to different rules with the first applicable one taking
        //  precedence.
        KDTree<Stop> spatialStopIndex = new KDTreeBuilder<Stop>().addLocations(schedule.getStops().values()).build();
        BeeLineWalkCalculator walkCalculator = new BeeLineWalkCalculator(WALKING_SPEED);
        WalkTransferGenerator transferGenerator = new WalkTransferGenerator(walkCalculator, MINIMUM_TRANSFER_TIME,
                ACCESS_EGRESS_TIME, SEARCH_RADIUS, spatialStopIndex);
        List<TransferGenerator.Transfer> additionalGeneratedTransfers = transferGenerator.generateTransfers(schedule);
        SameStopTransferGenerator sameStopTransferGenerator = new SameStopTransferGenerator(SAME_STOP_TRANSFER_TIME);
        additionalGeneratedTransfers.addAll(sameStopTransferGenerator.generateTransfers(schedule));

        Raptor raptor = new GtfsToRaptorConverter(schedule, additionalGeneratedTransfers,
                SAME_STOP_TRANSFER_TIME).convert(SCHEDULE_DATE);
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
            requests[i] = new RouteRequest(schedule.getStops().get(stopIds.get(sourceIndex)),
                    schedule.getStops().get(stopIds.get(destinationIndex)), random.nextInt(DEPARTURE_TIME_LIMIT));
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
            try {
                Map<String, Integer> sourceStops = Map.of(requests[i].sourceStop().getId(),
                        requests[i].departureTime());
                Map<String, Integer> targetStops = Map.of(requests[i].targetStop().getId(), 0);

                List<Connection> connections = raptor.getConnections(sourceStops, targetStops, TimeType.DEPARTURE,
                        new QueryConfig());
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
        Optional<LocalDateTime> earliestDepartureTime = toLocalDatetime(
                connections.stream().mapToInt(Connection::getDepartureTime).min().orElse(NOT_AVAILABLE));
        Optional<LocalDateTime> earliestArrivalTime = toLocalDatetime(
                connections.stream().mapToInt(Connection::getArrivalTime).min().orElse(NOT_AVAILABLE));
        int minDuration = connections.stream().mapToInt(Connection::getDuration).min().orElse(NOT_AVAILABLE);
        int maxDuration = connections.stream().mapToInt(Connection::getDuration).max().orElse(NOT_AVAILABLE);
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
                request.sourceStop().getName(), request.targetStop.getName(),
                toLocalDatetime(request.departureTime).orElseThrow(), connections.size(), earliestDepartureTime,
                earliestArrivalTime, minDuration, maxDuration, minTransfers, maxTransfers, beelineDistance,
                processingTime);
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

    private static Optional<LocalDateTime> toLocalDatetime(int seconds) {
        if (seconds == NOT_AVAILABLE) {
            return Optional.empty();
        }
        return Optional.of(new ServiceDayTime(seconds).toLocalDateTime(SCHEDULE_DATE));
    }

    record RouteRequest(Stop sourceStop, Stop targetStop, int departureTime) {
    }

    record RoutingResult(int id, String sourceStopId, String targetStopId, String sourceStopName, String targetStopName,
                         LocalDateTime requestedDepartureTime, int connections,
                         Optional<LocalDateTime> earliestDepartureTime, Optional<LocalDateTime> earliestArrivalTime,
                         int minDuration, int maxDuration, int minTransfers, int maxTransfers, long beelineDistance,
                         long processingTime) {
    }

}
