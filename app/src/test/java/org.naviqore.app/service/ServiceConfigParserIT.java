package org.naviqore.app.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.naviqore.app.config.NaviqoreProperties;
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
        return Stream.of(
                Arguments.of((Consumer<NaviqoreProperties>) p -> p.getRaptor().setSameStopTransferDurationDefault(-1),
                        "Same Stop Transfer Duration cannot be smaller than zero."),
                Arguments.of((Consumer<NaviqoreProperties>) p -> p.getService().getWalk().setSearchRadius(-1),
                        "Maximum Walk Distance cannot be smaller than zero."),
                Arguments.of((Consumer<NaviqoreProperties>) p -> p.getService().getWalk().setSpeed(-1.0),
                        "Walk speed cannot be smaller than zero."),
                Arguments.of((Consumer<NaviqoreProperties>) p -> p.getService().getWalk().setSpeed(0.0),
                        "Walk speed cannot be zero."));
    }

    private static NaviqoreProperties getNaviqoreProperties(Path gtfsPath) throws IOException {
        NaviqoreProperties properties = new NaviqoreProperties();
        properties.getGtfs()
                .getStaticFeed()
                .setUri(GtfsScheduleDataset.SAMPLE_FEED_1.getZip(gtfsPath).getAbsolutePath());

        return properties;
    }

    @Test
    void testServiceConfigParser_withValidInputs(@TempDir Path tempDir) throws IOException {
        ServiceConfig config = new ServiceConfigParser(getNaviqoreProperties(tempDir)).getServiceConfig();
        assertNotNull(config.getTimetableRepository());
        assertEquals(DEFAULT_TRANSFER_DURATION_BETWEEN_STOPS_MINIMUM, config.getTransferDurationBetweenStopsMinimum());
        assertEquals(DEFAULT_TRANSFER_DURATION_ACCESS_EGRESS, config.getTransferDurationAccessEgress());
        assertEquals(WalkCalculatorType.BEE_LINE_DISTANCE, config.getWalkCalculatorType());
        assertEquals(DEFAULT_WALK_SPEED, config.getWalkSpeed());
        assertEquals(DEFAULT_WALK_SEARCH_RADIUS, config.getWalkSearchRadius());
        assertEquals(DEFAULT_WALK_DURATION_MINIMUM, config.getWalkDurationMinimum());
        assertEquals(DEFAULT_OPTIMIZE_TRAVEL_TIME, config.isOptimizeTravelTime());
        assertEquals(DEFAULT_RAPTOR_DAYS_TO_SCAN, config.getRaptorDaysToScan());
        assertEquals(DEFAULT_RAPTOR_SAME_STOP_TRANSFER_DURATION_DEFAULT,
                config.getRaptorSameStopTransferDurationDefault());
        assertEquals(DEFAULT_RAPTOR_STOP_TIME_CACHE_SIZE, config.getRaptorStopTimeCacheSize());
        assertEquals(CacheEvictionStrategy.LRU, config.getRaptorStopTimeCacheStrategy());
    }

    @Test
    void testServiceConfigParser_withoutStaticUri() {
        assertThrows(IllegalArgumentException.class, () -> new ServiceConfigParser(new NaviqoreProperties()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"no/such/feed.zip", "ftp://example.org/feed.zip", "https://example.org/a feed.zip"})
    void testServiceConfigParser_withNeitherAFileNorAUriOfAKnownScheme(String uri) {
        NaviqoreProperties properties = new NaviqoreProperties();
        properties.getGtfs().getStaticFeed().setUri(uri);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new ServiceConfigParser(properties));
        assertEquals(
                "naviqore.gtfs.static.uri is neither an existing file nor a URI with scheme http, https or s3: " + uri,
                exception.getMessage());
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("provideInvalidProperties")
    void testServiceConfigParser_withInvalidInputs(Consumer<NaviqoreProperties> change, String message,
                                                   @TempDir Path tempDir) throws IOException {
        NaviqoreProperties properties = getNaviqoreProperties(tempDir);
        change.accept(properties);

        assertThrows(IllegalArgumentException.class, () -> new ServiceConfigParser(properties), message);
    }

}
