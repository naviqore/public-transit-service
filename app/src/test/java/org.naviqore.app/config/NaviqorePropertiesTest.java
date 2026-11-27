package org.naviqore.app.config;

import org.junit.jupiter.api.Test;
import org.naviqore.service.config.ServiceConfig;
import org.naviqore.service.config.ServiceConfig.CacheEvictionStrategy;
import org.naviqore.service.config.ServiceConfig.WalkCalculatorType;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.BindHandler;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.handler.NoUnboundElementsBindHandler;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The properties under {@code naviqore} bind to {@link NaviqoreProperties}: those of {@code application.properties}
 * each to a property, with the defaults of the app, and a property not set keeps the default of the service.
 */
class NaviqorePropertiesTest {

    private static NaviqoreProperties bind(Map<?, ?> properties, BindHandler handler) {
        NaviqoreProperties naviqoreProperties = new NaviqoreProperties();
        new Binder(new MapConfigurationPropertySource(properties)).bind("naviqore",
                Bindable.ofInstance(naviqoreProperties), handler);

        return naviqoreProperties;
    }

    private static NaviqoreProperties bind(Map<String, String> properties) {
        return bind(properties, BindHandler.DEFAULT);
    }

    @Test
    void applicationProperties_bindEachPropertyUnderNaviqore() throws IOException {
        // fails on a property under naviqore that sets nothing, a misspelled one for example
        NaviqoreProperties properties = bind(
                PropertiesLoaderUtils.loadProperties(new ClassPathResource("application.properties")),
                new NoUnboundElementsBindHandler(BindHandler.DEFAULT));

        // without environment variables, the defaults of the app, here those that differ from the service's
        assertEquals("libs/gtfs-sample/src/main/resources/org/naviqore/gtfs/schedule/sample-feed-1.zip",
                properties.getGtfs().getStaticFeed().getUri());
        assertEquals(-1, properties.getService().getTransfer().getDuration().getBetweenStopsMinimum());
        assertEquals(60, properties.getService().getWalk().getDurationMinimum());
        assertEquals(24 * 60 * 60, properties.getRouting().getTimeWindowDurationMaximum());
    }

    @Test
    void propertiesNotSet_keepTheDefaultsOfTheService() {
        NaviqoreProperties properties = bind(Map.of("naviqore.raptor.days-to-scan", "1"));

        assertEquals(1, properties.getRaptor().getDaysToScan());
        assertNull(properties.getGtfs().getStaticFeed().getUri());
        assertEquals(ServiceConfig.DEFAULT_TRANSFER_DURATION_BETWEEN_STOPS_MINIMUM,
                properties.getService().getTransfer().getDuration().getBetweenStopsMinimum());
        assertEquals(ServiceConfig.DEFAULT_WALK_DURATION_MINIMUM,
                properties.getService().getWalk().getDurationMinimum());
        assertEquals(ServiceConfig.DEFAULT_OPTIMIZE_TRAVEL_TIME, properties.getService().isOptimizeTravelTime());
        assertEquals(ServiceConfig.DEFAULT_RAPTOR_STOP_TIME_CACHE_SIZE, properties.getRaptor().getStopTimeCacheSize());
    }

    @Test
    void enums_bindIgnoringCase() {
        NaviqoreProperties properties = bind(Map.of("naviqore.service.walk.calculator-type", "bee_line_distance",
                "naviqore.raptor.stop-time-cache-strategy", "mru"));

        assertEquals(WalkCalculatorType.BEE_LINE_DISTANCE, properties.getService().getWalk().getCalculatorType());
        assertEquals(CacheEvictionStrategy.MRU, properties.getRaptor().getStopTimeCacheStrategy());
    }

    @Test
    void invalidWalkCalculatorType_failsToBind() {
        assertThrows(BindException.class, () -> bind(Map.of("naviqore.service.walk.calculator-type", "INVALID")));
    }

    @Test
    void timeWindowDurationMaximum_mayBeZeroButNotNegative() {
        NaviqoreProperties properties = bind(Map.of("naviqore.routing.time-window-duration-maximum", "0"));

        assertEquals(0, properties.getRouting().getTimeWindowDurationMaximum());
        assertThrows(BindException.class, () -> bind(Map.of("naviqore.routing.time-window-duration-maximum", "-1")));
    }
}
