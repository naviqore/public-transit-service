package ch.naviqore.service.gtfs.raptor;

import ch.naviqore.gtfs.schedule.model.GtfsSchedule;
import ch.naviqore.gtfs.schedule.model.Stop;
import ch.naviqore.raptor.router.RaptorConfig;
import ch.naviqore.raptor.router.RaptorRouter;
import ch.naviqore.service.config.ServiceConfig;
import ch.naviqore.service.gtfs.raptor.convert.GtfsToRaptorConverter;
import ch.naviqore.service.gtfs.raptor.convert.GtfsTripMaskProvider;
import ch.naviqore.service.gtfs.raptor.convert.TransferGenerator;
import ch.naviqore.service.gtfs.raptor.convert.WalkTransferGenerator;
import ch.naviqore.service.walk.BeeLineWalkCalculator;
import ch.naviqore.service.walk.WalkCalculator;
import ch.naviqore.utils.cache.EvictionCache;
import ch.naviqore.utils.search.SearchIndex;
import ch.naviqore.utils.search.SearchIndexBuilder;
import ch.naviqore.utils.spatial.index.KDTree;
import ch.naviqore.utils.spatial.index.KDTreeBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;

@RequiredArgsConstructor
@Slf4j
public class GtfsRaptorServiceInitializer {

    private final ServiceConfig config;
    private final GtfsSchedule schedule;
    private final WalkCalculator walkCalculator;
    private final SearchIndex<Stop> stopSearchIndex;
    private final KDTree<Stop> spatialStopIndex;
    private final RaptorRouter raptorRouter;

    public GtfsRaptorServiceInitializer(ServiceConfig config, GtfsSchedule schedule) {
        log.debug("Initializing with config: {}", config);
        this.config = config;
        this.schedule = schedule;
        this.walkCalculator = initializeWalkCalculator(config);
        this.stopSearchIndex = createStopSearchIndex(schedule);
        this.spatialStopIndex = createSpatialStopIndex(schedule);

        // generate transfers if minimum transfer time is not negative; usually -1 to deactivate generators
        List<TransferGenerator> transferGenerators = config.getTransferTimeBetweenStopsMinimum() >= 0 ? createTransferGenerators(
                config, walkCalculator, spatialStopIndex) : Collections.emptyList();
        this.raptorRouter = createRaptorRouter(config, schedule, transferGenerators);
    }

    private static WalkCalculator initializeWalkCalculator(ServiceConfig config) {
        return switch (config.getWalkingCalculatorType()) {
            case ServiceConfig.WalkCalculatorType.BEE_LINE_DISTANCE ->
                    new BeeLineWalkCalculator(config.getWalkingSpeed());
        };
    }

    private static SearchIndex<Stop> createStopSearchIndex(GtfsSchedule schedule) {
        SearchIndexBuilder<Stop> builder = SearchIndex.builder();

        // only add parent stops and stops without a parent
        for (ch.naviqore.gtfs.schedule.model.Stop stop : schedule.getStops().values()) {
            if (stop.getParent().isEmpty()) {
                builder.add(stop.getName().toLowerCase(), stop);
            }
        }

        return builder.build();
    }

    private static KDTree<Stop> createSpatialStopIndex(GtfsSchedule schedule) {
        return new KDTreeBuilder<Stop>().addLocations(schedule.getStops().values()).build();
    }

    private static List<TransferGenerator> createTransferGenerators(ServiceConfig config, WalkCalculator walkCalculator,
                                                                    KDTree<Stop> spatialStopIndex) {
        return List.of(new WalkTransferGenerator(walkCalculator, config.getTransferTimeBetweenStopsMinimum(),
                config.getTransferTimeAccessEgress(), config.getWalkingSearchRadius(), spatialStopIndex));
    }

    private static RaptorRouter createRaptorRouter(ServiceConfig config, GtfsSchedule schedule,
                                                   List<TransferGenerator> transferGenerators) {
        // setup cache and trip mask provider
        EvictionCache.Strategy cacheStrategy = EvictionCache.Strategy.valueOf(config.getCacheEvictionStrategy().name());
        GtfsTripMaskProvider tripMaskProvider = new GtfsTripMaskProvider(schedule, config.getCacheServiceDaySize(),
                cacheStrategy);

        // configure raptor
        RaptorConfig raptorConfig = new RaptorConfig(config.getRaptorDaysToScan(), config.getRaptorRange(),
                config.getTransferTimeSameStopDefault(), config.getCacheServiceDaySize(), cacheStrategy,
                tripMaskProvider);

        return new GtfsToRaptorConverter(raptorConfig, schedule, transferGenerators).run();
    }

    public GtfsRaptorService get() {
        return new GtfsRaptorService(config, schedule, spatialStopIndex, stopSearchIndex, walkCalculator, raptorRouter);
    }

}
