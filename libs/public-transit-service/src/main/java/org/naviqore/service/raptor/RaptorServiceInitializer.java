package org.naviqore.service.raptor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.naviqore.raptor.router.RaptorConfig;
import org.naviqore.raptor.router.RaptorRouter;
import org.naviqore.service.config.ServiceConfig;
import org.naviqore.service.raptor.convert.TimetableToRaptorConverter;
import org.naviqore.service.raptor.convert.TimetableTripMaskProvider;
import org.naviqore.service.raptor.convert.TransferGenerator;
import org.naviqore.service.raptor.convert.WalkTransferGenerator;
import org.naviqore.service.walk.BeeLineWalkCalculator;
import org.naviqore.service.walk.WalkCalculator;
import org.naviqore.timetable.model.Stop;
import org.naviqore.timetable.model.Timetable;
import org.naviqore.utils.cache.EvictionCache;
import org.naviqore.utils.search.SearchIndex;
import org.naviqore.utils.search.SearchIndexBuilder;
import org.naviqore.utils.spatial.index.KDTree;
import org.naviqore.utils.spatial.index.KDTreeBuilder;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

@RequiredArgsConstructor
@Slf4j
public class RaptorServiceInitializer {

    private final ServiceConfig config;
    private final Timetable schedule;
    private final WalkCalculator walkCalculator;
    private final SearchIndex<Stop> stopSearchIndex;
    private final KDTree<Stop> spatialStopIndex;
    private final RaptorRouter raptorRouter;

    public RaptorServiceInitializer(ServiceConfig config) throws IOException, InterruptedException {
        log.debug("Initializing with config: {}", config);
        this.config = config;
        this.schedule = config.getTimetableRepository().get();
        this.walkCalculator = initializeWalkCalculator(config);
        this.stopSearchIndex = createStopSearchIndex(schedule);
        this.spatialStopIndex = createSpatialStopIndex(schedule);

        // generate walks between stops unless their minimum duration is negative
        List<TransferGenerator> transferGenerators = config.getTransferDurationBetweenStopsMinimum() >= 0 ? createTransferGenerators(
                config, walkCalculator, spatialStopIndex) : Collections.emptyList();
        this.raptorRouter = createRaptorRouter(config, schedule, transferGenerators);
    }

    private static WalkCalculator initializeWalkCalculator(ServiceConfig config) {
        return switch (config.getWalkCalculatorType()) {
            case ServiceConfig.WalkCalculatorType.BEE_LINE_DISTANCE -> new BeeLineWalkCalculator(config.getWalkSpeed());
        };
    }

    private static SearchIndex<Stop> createStopSearchIndex(Timetable schedule) {
        SearchIndexBuilder<Stop> builder = SearchIndex.builder();

        // only add parent stops and stops without a parent
        for (org.naviqore.timetable.model.Stop stop : schedule.getStops().values()) {
            if (stop.getParent().isEmpty()) {
                builder.add(stop.getName().toLowerCase(Locale.ROOT), stop);
            }
        }

        return builder.build();
    }

    private static KDTree<Stop> createSpatialStopIndex(Timetable schedule) {
        return new KDTreeBuilder<Stop>().addLocations(schedule.getStops().values()).build();
    }

    private static List<TransferGenerator> createTransferGenerators(ServiceConfig config, WalkCalculator walkCalculator,
                                                                    KDTree<Stop> spatialStopIndex) {
        return List.of(new WalkTransferGenerator(walkCalculator, config.getTransferDurationBetweenStopsMinimum(),
                config.getTransferDurationAccessEgress(), config.getWalkSearchRadius(), spatialStopIndex));
    }

    private static RaptorRouter createRaptorRouter(ServiceConfig config, Timetable schedule,
                                                   List<TransferGenerator> transferGenerators) {
        // setup cache and trip mask provider
        EvictionCache.Strategy cacheStrategy = EvictionCache.Strategy.valueOf(
                config.getRaptorStopTimeCacheStrategy().name());
        TimetableTripMaskProvider tripMaskProvider = new TimetableTripMaskProvider(schedule,
                config.getRaptorStopTimeCacheSize(), cacheStrategy);

        // configure raptor
        RaptorConfig raptorConfig = RaptorConfig.builder()
                .daysToScan(config.getRaptorDaysToScan())
                .sameStopTransferDurationDefault(config.getRaptorSameStopTransferDurationDefault())
                .stopTimeCacheSize(config.getRaptorStopTimeCacheSize())
                .stopTimeCacheStrategy(cacheStrategy)
                .maskProvider(tripMaskProvider)
                .build();

        return new TimetableToRaptorConverter(raptorConfig, schedule, transferGenerators).run();
    }

    public RaptorService get() {
        return new RaptorService(config, schedule, spatialStopIndex, stopSearchIndex, walkCalculator, raptorRouter);
    }

}
