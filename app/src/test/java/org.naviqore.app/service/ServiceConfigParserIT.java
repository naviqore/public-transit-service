package org.naviqore.app.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.naviqore.gtfs.schedule.GtfsScheduleDataset;
import org.naviqore.service.config.ServiceConfig;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.naviqore.service.config.ServiceConfig.*;

public class ServiceConfigParserIT {

    static Stream<Arguments> provideTestCombinations() {
        return Stream.of(Arguments.of(-1, DEFAULT_TRANSFER_TIME_BETWEEN_STOPS_MINIMUM, DEFAULT_WALKING_SEARCH_RADIUS,
                        DEFAULT_WALKING_SPEED, "BEE_LINE_DISTANCE", "Same Stop Transfer Time cannot be smaller than zero."),
                Arguments.of(DEFAULT_TRANSFER_TIME_SAME_STOP_DEFAULT, DEFAULT_TRANSFER_TIME_BETWEEN_STOPS_MINIMUM, -1,
                        DEFAULT_WALKING_SPEED, "BEE_LINE_DISTANCE",
                        "Maximum Walking Distance cannot be smaller than zero."),
                Arguments.of(DEFAULT_TRANSFER_TIME_SAME_STOP_DEFAULT, DEFAULT_TRANSFER_TIME_BETWEEN_STOPS_MINIMUM,
                        DEFAULT_WALKING_SEARCH_RADIUS, -1.0, "BEE_LINE_DISTANCE",
                        "Walking speed cannot be smaller than zero."),
                Arguments.of(DEFAULT_TRANSFER_TIME_SAME_STOP_DEFAULT, DEFAULT_TRANSFER_TIME_BETWEEN_STOPS_MINIMUM,
                        DEFAULT_WALKING_SEARCH_RADIUS, 0.0, "BEE_LINE_DISTANCE", "Walking speed cannot be zero."),
                Arguments.of(DEFAULT_TRANSFER_TIME_SAME_STOP_DEFAULT, DEFAULT_TRANSFER_TIME_BETWEEN_STOPS_MINIMUM,
                        DEFAULT_WALKING_SEARCH_RADIUS, DEFAULT_WALKING_SPEED, "INVALID",
                        "Can't process invalid Walking Calculator Type."));
    }

    private static ServiceConfig getServiceConfig(Path gtfsPath) throws IOException {
        File gtfs = GtfsScheduleDataset.SAMPLE_FEED_1.getZip(gtfsPath);
        ServiceConfigParser parser = new ServiceConfigParser(gtfs.getAbsolutePath(), DEFAULT_GTFS_STATIC_UPDATE_CRON,
                DEFAULT_TRANSFER_TIME_SAME_STOP_DEFAULT, DEFAULT_TRANSFER_TIME_BETWEEN_STOPS_MINIMUM,
                DEFAULT_TRANSFER_TIME_ACCESS_EGRESS, DEFAULT_WALKING_SEARCH_RADIUS,
                DEFAULT_WALKING_CALCULATOR_TYPE.name(), DEFAULT_WALKING_SPEED, DEFAULT_WALKING_DURATION_MINIMUM,
                DEFAULT_RAPTOR_DAYS_TO_SCAN, DEFAULT_RAPTOR_RANGE, DEFAULT_CACHE_SIZE,
                DEFAULT_CACHE_EVICTION_STRATEGY.name());
        return parser.getServiceConfig();
    }

    @Test
    void testServiceConfigParser_withValidInputs(@TempDir Path tempDir) throws IOException {
        ServiceConfig config = getServiceConfig(tempDir);
        assertNotNull(config.getGtfsScheduleRepository());
        assertEquals(DEFAULT_GTFS_STATIC_UPDATE_CRON, config.getGtfsStaticUpdateCron());
        assertEquals(DEFAULT_TRANSFER_TIME_SAME_STOP_DEFAULT, config.getTransferTimeSameStopDefault());
        assertEquals(DEFAULT_TRANSFER_TIME_BETWEEN_STOPS_MINIMUM, config.getTransferTimeBetweenStopsMinimum());
        assertEquals(WalkCalculatorType.BEE_LINE_DISTANCE, config.getWalkingCalculatorType());
        assertEquals(DEFAULT_WALKING_SPEED, config.getWalkingSpeed());
        assertEquals(DEFAULT_WALKING_SEARCH_RADIUS, config.getWalkingSearchRadius());
        assertEquals(DEFAULT_WALKING_DURATION_MINIMUM, config.getWalkingDurationMinimum());
        assertEquals(DEFAULT_RAPTOR_DAYS_TO_SCAN, config.getRaptorDaysToScan());
        assertEquals(DEFAULT_CACHE_SIZE, config.getCacheServiceDaySize());
        assertEquals(CacheEvictionStrategy.LRU, config.getCacheEvictionStrategy());
    }

    @Test
    void testServiceConfigParser_withInvalidWalkCalculatorType(@TempDir Path tempDir) throws IOException {
        File gtfs = GtfsScheduleDataset.SAMPLE_FEED_1.getZip(tempDir);
        assertThrows(IllegalArgumentException.class,
                () -> new ServiceConfigParser(gtfs.getAbsolutePath(), DEFAULT_GTFS_STATIC_UPDATE_CRON,
                        DEFAULT_TRANSFER_TIME_BETWEEN_STOPS_MINIMUM, DEFAULT_TRANSFER_TIME_SAME_STOP_DEFAULT,
                        DEFAULT_TRANSFER_TIME_ACCESS_EGRESS, DEFAULT_WALKING_SEARCH_RADIUS, "INVALID",
                        DEFAULT_WALKING_SPEED, DEFAULT_WALKING_DURATION_MINIMUM, DEFAULT_RAPTOR_DAYS_TO_SCAN,
                        DEFAULT_RAPTOR_RANGE, DEFAULT_CACHE_SIZE, DEFAULT_CACHE_EVICTION_STRATEGY.name()));
    }

    @ParameterizedTest(name = "{5}")
    @MethodSource("provideTestCombinations")
    void testServiceConfigParser_withInvalidInputs(int transferTimeSameStopDefault, int transferTimeBetweenStopsMinimum,
                                                   int walkingSearchRadius, double walkingSpeed,
                                                   String walkingCalculatorType, String message,
                                                   @TempDir Path tempDir) throws IOException {
        File gtfs = GtfsScheduleDataset.SAMPLE_FEED_1.getZip(tempDir);
        assertThrows(IllegalArgumentException.class,
                () -> new ServiceConfigParser(gtfs.getAbsolutePath(), DEFAULT_GTFS_STATIC_UPDATE_CRON,
                        transferTimeSameStopDefault, transferTimeBetweenStopsMinimum,
                        DEFAULT_TRANSFER_TIME_ACCESS_EGRESS, walkingSearchRadius, walkingCalculatorType.toUpperCase(),
                        walkingSpeed, DEFAULT_WALKING_DURATION_MINIMUM, DEFAULT_RAPTOR_DAYS_TO_SCAN,
                        DEFAULT_RAPTOR_RANGE, DEFAULT_CACHE_SIZE, DEFAULT_CACHE_EVICTION_STRATEGY.name()), message);
    }

}
