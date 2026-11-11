package org.naviqore.app.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.naviqore.app.config.AppProperties;
import org.naviqore.gtfs.schedule.GtfsScheduleDataset;
import org.naviqore.service.config.ServiceConfig;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.naviqore.service.config.ServiceConfig.*;

public class ServiceConfigParserIT {

    static Stream<Arguments> provideInvalidProperties() {
        return Stream.of(Arguments.of(
                        (Consumer<AppProperties>) properties -> properties.getTransfer().getDuration().setSameStopDefault(-1),
                        "Same Stop Transfer Duration cannot be smaller than zero."),
                Arguments.of((Consumer<AppProperties>) properties -> properties.getWalk().setSearchRadius(-1),
                        "Maximum Walk Distance cannot be smaller than zero."),
                Arguments.of((Consumer<AppProperties>) properties -> properties.getWalk().setSpeed(-1.0),
                        "Walk speed cannot be smaller than zero."),
                Arguments.of((Consumer<AppProperties>) properties -> properties.getWalk().setSpeed(0.0),
                        "Walk speed cannot be zero."));
    }

    private static AppProperties getAppProperties(Path gtfsPath) throws IOException {
        AppProperties properties = new AppProperties();
        properties.getGtfs()
                .getStaticFeed()
                .setUri(GtfsScheduleDataset.SAMPLE_FEED_1.getZip(gtfsPath).getAbsolutePath());

        return properties;
    }

    @Test
    void testServiceConfigParser_withValidInputs(@TempDir Path tempDir) throws IOException {
        ServiceConfig config = new ServiceConfigParser(getAppProperties(tempDir)).getServiceConfig();
        assertNotNull(config.getTimetableRepository());
        assertEquals(DEFAULT_GTFS_STATIC_UPDATE_CRON, config.getGtfsStaticUpdateCron());
        assertEquals(DEFAULT_TRANSFER_DURATION_SAME_STOP_DEFAULT, config.getTransferDurationSameStopDefault());
        assertEquals(DEFAULT_TRANSFER_DURATION_BETWEEN_STOPS_MINIMUM, config.getTransferDurationBetweenStopsMinimum());
        assertEquals(DEFAULT_TRANSFER_DURATION_ACCESS_EGRESS, config.getTransferDurationAccessEgress());
        assertEquals(WalkCalculatorType.BEE_LINE_DISTANCE, config.getWalkCalculatorType());
        assertEquals(DEFAULT_WALK_SPEED, config.getWalkSpeed());
        assertEquals(DEFAULT_WALK_SEARCH_RADIUS, config.getWalkSearchRadius());
        assertEquals(DEFAULT_WALK_DURATION_MINIMUM, config.getWalkDurationMinimum());
        assertEquals(DEFAULT_RAPTOR_DAYS_TO_SCAN, config.getRaptorDaysToScan());
        assertEquals(DEFAULT_OPTIMIZE_TRAVEL_TIME, config.isOptimizeTravelTime());
        assertEquals(DEFAULT_CACHE_SIZE, config.getCacheServiceDaySize());
        assertEquals(CacheEvictionStrategy.LRU, config.getCacheEvictionStrategy());
    }

    @Test
    void testServiceConfigParser_withoutStaticUri() {
        assertThrows(IllegalArgumentException.class, () -> new ServiceConfigParser(new AppProperties()));
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("provideInvalidProperties")
    void testServiceConfigParser_withInvalidInputs(Consumer<AppProperties> change, String message,
                                                   @TempDir Path tempDir) throws IOException {
        AppProperties properties = getAppProperties(tempDir);
        change.accept(properties);

        assertThrows(IllegalArgumentException.class, () -> new ServiceConfigParser(properties), message);
    }

}
