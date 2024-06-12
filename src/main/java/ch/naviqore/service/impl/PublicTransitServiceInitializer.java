package ch.naviqore.service.impl;

import ch.naviqore.gtfs.schedule.GtfsScheduleReader;
import ch.naviqore.gtfs.schedule.model.GtfsSchedule;
import ch.naviqore.gtfs.schedule.model.Stop;
import ch.naviqore.gtfs.schedule.type.TransferType;
import ch.naviqore.service.PublicTransitService;
import ch.naviqore.service.config.ServiceConfig;
import ch.naviqore.service.impl.transfer.SameStationTransferGenerator;
import ch.naviqore.service.impl.transfer.TransferGenerator;
import ch.naviqore.service.impl.transfer.WalkTransferGenerator;
import ch.naviqore.service.walk.BeeLineWalkCalculator;
import ch.naviqore.service.walk.WalkCalculator;
import ch.naviqore.utils.search.SearchIndex;
import ch.naviqore.utils.search.SearchIndexBuilder;
import ch.naviqore.utils.spatial.index.KDTree;
import ch.naviqore.utils.spatial.index.KDTreeBuilder;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;

@RequiredArgsConstructor
@Getter(AccessLevel.PACKAGE)
@Log4j2
public class PublicTransitServiceInitializer {

    private final ServiceConfig config;

    private final WalkCalculator walkCalculator;
    private final GtfsSchedule schedule;
    private final SearchIndex<Stop> stopSearchIndex;
    private final KDTree<Stop> spatialStopIndex;
    private final List<TransferGenerator.Transfer> additionalTransfers;

    public PublicTransitServiceInitializer(ServiceConfig config) {
        this.config = config;
        log.info("Initializing PublicTransitServiceInitializer with config {}", config);
        this.walkCalculator = initializeWalkCalculator(config);
        this.schedule = readGtfsSchedule(config.getGtfsStaticUrl());
        this.stopSearchIndex = generateStopSearchIndex(schedule);
        this.spatialStopIndex = generateSpatialStopIndex(schedule);
        this.additionalTransfers = generateTransfers(schedule,
                createTransferGenerators(config, walkCalculator, spatialStopIndex));
    }

    private static WalkCalculator initializeWalkCalculator(ServiceConfig config) {
        return switch (config.getWalkingCalculatorType()) {
            case ServiceConfig.WalkCalculatorType.BEE_LINE_DISTANCE ->
                    new BeeLineWalkCalculator(config.getWalkingSpeed());
        };

    }

    private static GtfsSchedule readGtfsSchedule(String gtfsFilePath) {
        // TODO: Download file if needed
        try {
            return new GtfsScheduleReader().read(gtfsFilePath);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static SearchIndex<Stop> generateStopSearchIndex(GtfsSchedule schedule) {
        SearchIndexBuilder<Stop> builder = SearchIndex.builder();

        // only add parent stops and stops without a parent
        for (ch.naviqore.gtfs.schedule.model.Stop stop : schedule.getStops().values()) {
            if (stop.getParent().isEmpty()) {
                builder.add(stop.getName().toLowerCase(), stop);
            }
        }

        return builder.build();
    }

    private static KDTree<Stop> generateSpatialStopIndex(GtfsSchedule schedule) {
        return new KDTreeBuilder<Stop>().addLocations(schedule.getStops().values()).build();
    }

    private static List<TransferGenerator> createTransferGenerators(ServiceConfig config, WalkCalculator walkCalculator,
                                                                    KDTree<Stop> spatialStopIndex) {
        ArrayList<TransferGenerator> generators = new ArrayList<>();
        generators.add(new SameStationTransferGenerator(config.getTransferTimeSameStopDefault()));
        generators.add(new WalkTransferGenerator(walkCalculator, config.getTransferTimeBetweenStopsMinimum(),
                config.getWalkingDistanceMaximum(), spatialStopIndex));
        return generators;
    }

    private static List<TransferGenerator.Transfer> generateTransfers(GtfsSchedule schedule,
                                                                      List<TransferGenerator> transferGenerators) {
        // Create Lookup for GTFS Transfers in Schedule to prevent adding duplicates later
        Set<String> gtfsTransfers = new HashSet<>();
        schedule.getStops().values().forEach(stop -> stop.getTransfers().forEach(transfer -> {
            if (transfer.getTransferType() == TransferType.MINIMUM_TIME) {
                String key = transfer.getFromStop().getId() + transfer.getToStop().getId();
                gtfsTransfers.add(key);
            }
        }));

        // Run all Generators in parallel and collect all generated Transfers
        List<TransferGenerator.Transfer> uncheckedGeneratedTransfers = transferGenerators.parallelStream()
                .flatMap(generator -> generator.generateTransfers(schedule).stream())
                .toList();

        // Add all generated Transfers to the Lookup if they are not already in the GTFS Transfers or
        // where already generated by a preceding generator.
        Map<String, TransferGenerator.Transfer> generatedTransfers = new HashMap<>();
        for (TransferGenerator.Transfer transfer : uncheckedGeneratedTransfers) {
            String key = transfer.from().getId() + transfer.to().getId();
            if (!gtfsTransfers.contains(key) && !generatedTransfers.containsKey(key)) {
                generatedTransfers.put(key, transfer);
            }
        }

        return new ArrayList<>(generatedTransfers.values());
    }

    public PublicTransitService get() {
        return new PublicTransitServiceImpl(config, schedule, spatialStopIndex, stopSearchIndex, walkCalculator,
                additionalTransfers);
    }

}
