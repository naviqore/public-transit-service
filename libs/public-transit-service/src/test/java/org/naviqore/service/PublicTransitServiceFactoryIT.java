package org.naviqore.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.naviqore.gtfs.schedule.GtfsScheduleDataset;
import org.naviqore.gtfs.schedule.GtfsScheduleReader;
import org.naviqore.gtfs.schedule.model.GtfsSchedule;
import org.naviqore.raptor.RaptorAlgorithm;
import org.naviqore.raptor.router.RaptorConfig;
import org.naviqore.service.config.ConnectionQueryConfig;
import org.naviqore.service.config.ServiceConfig;
import org.naviqore.service.exception.ConnectionRoutingException;
import org.naviqore.service.exception.StopNotFoundException;
import org.naviqore.service.gtfs.raptor.GtfsTransitSchedule;
import org.naviqore.service.gtfs.raptor.convert.GtfsToRaptorConverter;
import org.naviqore.service.gtfs.raptor.convert.GtfsTripMaskProvider;
import org.naviqore.utils.cache.EvictionCache;
import org.naviqore.utils.spatial.index.KDTree;
import org.naviqore.utils.spatial.index.KDTreeBuilder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that a service created on top of a {@link org.naviqore.service.spi.TransitSchedule} answers exactly like the
 * service created from the GTFS schedule repository of the configuration.
 */
class PublicTransitServiceFactoryIT {

    private static final OffsetDateTime DATE_TIME = LocalDateTime.of(2007, 1, 1, 8, 0)
            .atZone(ZoneId.of("America/Los_Angeles"))
            .toOffsetDateTime();

    private PublicTransitService gtfsService;
    private PublicTransitService transitScheduleService;
    private List<Stop> stops;

    private static RaptorAlgorithm createRaptor(GtfsSchedule schedule, ServiceConfig config) {
        EvictionCache.Strategy cacheStrategy = EvictionCache.Strategy.valueOf(config.getCacheEvictionStrategy().name());
        RaptorConfig raptorConfig = RaptorConfig.builder()
                .daysToScan(config.getRaptorDaysToScan())
                .raptorRangeDefault(config.getRaptorRange())
                .sameStopTransferDurationDefault(config.getTransferDurationSameStopDefault())
                .stopTimeCacheSize(config.getCacheServiceDaySize())
                .stopTimeCacheStrategy(cacheStrategy)
                .maskProvider(new GtfsTripMaskProvider(schedule, config.getCacheServiceDaySize(), cacheStrategy))
                .build();

        return new GtfsToRaptorConverter(raptorConfig, schedule).run();
    }

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws IOException, InterruptedException, StopNotFoundException {
        File zipFile = GtfsScheduleDataset.SAMPLE_FEED_1.getZip(tempDir);
        GtfsSchedule schedule = new GtfsScheduleReader().read(zipFile.toString());

        // walk transfers are not generated, so both services route on an identically built RAPTOR router
        ServiceConfig config = ServiceConfig.builder()
                .gtfsScheduleRepository(() -> schedule)
                .transferDurationBetweenStopsMinimum(-1)
                .build();
        PublicTransitServiceFactory factory = new PublicTransitServiceFactory(config);

        KDTree<org.naviqore.gtfs.schedule.model.Stop> spatialStopIndex = new KDTreeBuilder<org.naviqore.gtfs.schedule.model.Stop>().addLocations(
                schedule.getStops().values()).build();

        gtfsService = factory.create();
        transitScheduleService = factory.create(new GtfsTransitSchedule(schedule, spatialStopIndex),
                createRaptor(schedule, config));

        stops = new ArrayList<>();
        for (String stopId : schedule.getStops().keySet()) {
            stops.add(gtfsService.getStopById(stopId));
        }
    }

    @Test
    void shouldAnswerScheduleInformationQueriesIdentically() throws StopNotFoundException {
        assertEquals(gtfsService.getValidity().getStartDate(), transitScheduleService.getValidity().getStartDate());
        assertEquals(gtfsService.getValidity().getEndDate(), transitScheduleService.getValidity().getEndDate());
        assertEquals(gtfsService.getRoutingFeatures(), transitScheduleService.getRoutingFeatures());

        for (StopSortStrategy sortStrategy : StopSortStrategy.values()) {
            List<Stop> found = gtfsService.getStops("a", SearchType.CONTAINS, sortStrategy);
            assertFalse(found.isEmpty());
            assertEquals(found, transitScheduleService.getStops("a", SearchType.CONTAINS, sortStrategy));
        }

        assertFalse(stops.isEmpty());
        for (Stop stop : stops) {
            assertEquals(stop, transitScheduleService.getStopById(stop.getId()));
            assertEquals(gtfsService.getNearestStop(stop.getCoordinate()),
                    transitScheduleService.getNearestStop(stop.getCoordinate()));
            assertEquals(gtfsService.getNearestStops(stop.getCoordinate(), 1000),
                    transitScheduleService.getNearestStops(stop.getCoordinate(), 1000));

            for (StopScope scope : StopScope.values()) {
                for (TimeType timeType : TimeType.values()) {
                    assertEquals(gtfsService.getStopTimes(stop, DATE_TIME, DATE_TIME.plusDays(1), timeType, scope),
                            transitScheduleService.getStopTimes(stop, DATE_TIME, DATE_TIME.plusDays(1), timeType,
                                    scope));
                }
            }
        }
    }

    @Test
    void shouldRouteIdentically() throws ConnectionRoutingException {
        ConnectionQueryConfig queryConfig = ConnectionQueryConfig.defaults();
        int routedConnections = 0;

        for (Stop source : stops) {
            for (TimeType timeType : TimeType.values()) {
                assertEquals(gtfsService.getIsolines(source, DATE_TIME, timeType, queryConfig),
                        transitScheduleService.getIsolines(source, DATE_TIME, timeType, queryConfig));
                assertEquals(
                        gtfsService.getIsolines(source.getCoordinate(), DATE_TIME, timeType, queryConfig),
                        transitScheduleService.getIsolines(source.getCoordinate(), DATE_TIME, timeType,
                                queryConfig));
            }

            for (Stop target : stops) {
                if (source.equals(target)) {
                    continue;
                }

                List<Connection> connections = gtfsService.getConnections(source, target, DATE_TIME,
                        TimeType.DEPARTURE, queryConfig);
                routedConnections += connections.size();
                assertEquals(connections,
                        transitScheduleService.getConnections(source, target, DATE_TIME, TimeType.DEPARTURE,
                                queryConfig));
                assertEquals(gtfsService.getConnections(source.getCoordinate(), target.getCoordinate(), DATE_TIME,
                                TimeType.DEPARTURE, queryConfig),
                        transitScheduleService.getConnections(source.getCoordinate(), target.getCoordinate(),
                                DATE_TIME, TimeType.DEPARTURE, queryConfig));
            }
        }

        assertTrue(routedConnections > 0);
    }

}
