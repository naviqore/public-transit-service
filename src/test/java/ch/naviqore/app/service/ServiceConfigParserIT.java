package ch.naviqore.app.service;

import ch.naviqore.service.config.ServiceConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static ch.naviqore.service.config.ServiceConfig.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ServiceConfigParserIT {

    private static final String GTFS_STATIC_URL = "src/test/resources/ch/naviqore/gtfs/schedule/sample-feed-1.zip";

    static Stream<Arguments> provideTestCombinations() {
        return Stream.of(Arguments.of(-1, DEFAULT_TRANSFER_TIME_BETWEEN_STOPS_MINIMUM, DEFAULT_WALKING_SEARCH_RADIUS,
                        DEFAULT_WALKING_SPEED, "BEE_LINE_DISTANCE", "Minimum Transfer Time cannot be smaller than zero."),
                Arguments.of(DEFAULT_TRANSFER_TIME_SAME_STOP_DEFAULT, -1, DEFAULT_WALKING_SEARCH_RADIUS,
                        DEFAULT_WALKING_SPEED, "BEE_LINE_DISTANCE",
                        "Same Station Transfer Time cannot be smaller than zero."),
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

    private static ServiceConfig getServiceConfig() {
        ServiceConfigParser parser = new ServiceConfigParser(GTFS_STATIC_URL, DEFAULT_GTFS_STATIC_UPDATE_CRON,
                DEFAULT_TRANSFER_TIME_SAME_STOP_DEFAULT, DEFAULT_TRANSFER_TIME_BETWEEN_STOPS_MINIMUM,
                DEFAULT_WALKING_SEARCH_RADIUS, DEFAULT_WALKING_CALCULATOR_TYPE.name(), DEFAULT_WALKING_SPEED,
                DEFAULT_WALKING_DURATION_MINIMUM, DEFAULT_CACHE_SIZE, DEFAULT_CACHE_EVICTION_STRATEGY.name());
        return parser.getServiceConfig();
    }

    @Test
    void testServiceConfigParser_withValidInputs() {
        ServiceConfig config = getServiceConfig();
        assertEquals(GTFS_STATIC_URL, config.getGtfsStaticUrl());
        assertEquals(DEFAULT_GTFS_STATIC_UPDATE_CRON, config.getGtfsStaticUpdateCron());
        assertEquals(DEFAULT_TRANSFER_TIME_SAME_STOP_DEFAULT, config.getTransferTimeSameStopDefault());
        assertEquals(DEFAULT_TRANSFER_TIME_BETWEEN_STOPS_MINIMUM, config.getTransferTimeBetweenStopsMinimum());
        assertEquals(WalkCalculatorType.BEE_LINE_DISTANCE, config.getWalkingCalculatorType());
        assertEquals(DEFAULT_WALKING_SPEED, config.getWalkingSpeed());
        assertEquals(DEFAULT_WALKING_SEARCH_RADIUS, config.getWalkingSearchRadius());
        assertEquals(DEFAULT_WALKING_DURATION_MINIMUM, config.getWalkingDurationMinimum());
        assertEquals(DEFAULT_CACHE_SIZE, config.getCacheSize());
        assertEquals(CacheEvictionStrategy.LRU, config.getCacheEvictionStrategy());
    }

    @Test
    void testServiceConfigParser_withInvalidWalkCalculatorType() {
        assertThrows(IllegalArgumentException.class,
                () -> new ServiceConfigParser(GTFS_STATIC_URL, DEFAULT_GTFS_STATIC_UPDATE_CRON,
                        DEFAULT_TRANSFER_TIME_BETWEEN_STOPS_MINIMUM, DEFAULT_TRANSFER_TIME_SAME_STOP_DEFAULT,
                        DEFAULT_WALKING_SEARCH_RADIUS, "INVALID", DEFAULT_WALKING_SPEED,
                        DEFAULT_WALKING_DURATION_MINIMUM, DEFAULT_CACHE_SIZE, DEFAULT_CACHE_EVICTION_STRATEGY.name()));
    }

    @ParameterizedTest(name = "{5}")
    @MethodSource("provideTestCombinations")
    void testServiceConfigParser_withInvalidInputs(int transferTimeSameStopDefault, int transferTimeBetweenStopsMinimum,
                                                   int walkingSearchRadius, double walkingSpeed,
                                                   String walkingCalculatorType, String message) {
        assertThrows(IllegalArgumentException.class,
                () -> new ServiceConfigParser(GTFS_STATIC_URL, DEFAULT_GTFS_STATIC_UPDATE_CRON,
                        transferTimeSameStopDefault, transferTimeBetweenStopsMinimum, walkingSearchRadius,
                        walkingCalculatorType.toUpperCase(), walkingSpeed, DEFAULT_WALKING_DURATION_MINIMUM,
                        DEFAULT_CACHE_SIZE, DEFAULT_CACHE_EVICTION_STRATEGY.name()), message);
    }

}
