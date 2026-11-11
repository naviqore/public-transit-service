package org.naviqore.app.config;

import org.junit.jupiter.api.Test;
import org.naviqore.service.config.ServiceConfig;
import org.naviqore.service.config.ServiceConfig.CacheEvictionStrategy;
import org.naviqore.service.config.ServiceConfig.WalkCalculatorType;
import org.springframework.boot.context.properties.bind.*;
import org.springframework.boot.context.properties.bind.handler.NoUnboundElementsBindHandler;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The properties under {@code app} bind to {@link AppProperties}: those of {@code application.properties} each to a
 * property, with the defaults of the app, and a property not set keeps the default of the service.
 */
class AppPropertiesTest {

    private static AppProperties bind(Map<String, String> properties) {
        AppProperties appProperties = new AppProperties();
        new Binder(new MapConfigurationPropertySource(properties)).bind("app", Bindable.ofInstance(appProperties));

        return appProperties;
    }

    @Test
    void applicationProperties_bindEachPropertyUnderApp() throws IOException {
        // the home page is no property of the service: a condition of its controller reads it
        Properties properties = PropertiesLoaderUtils.loadProperties(new ClassPathResource("application.properties"));
        properties.stringPropertyNames()
                .stream()
                .filter(name -> name.startsWith("app.home-page."))
                .forEach(properties::remove);
        MutablePropertySources sources = new MutablePropertySources();
        sources.addLast(new PropertiesPropertySource("application.properties", properties));
        Binder binder = new Binder(ConfigurationPropertySources.from(sources),
                new PropertySourcesPlaceholdersResolver(sources));

        // fails on a property under app that sets nothing, a misspelled one for example
        AppProperties appProperties = new AppProperties();
        binder.bind("app", Bindable.ofInstance(appProperties), new NoUnboundElementsBindHandler(BindHandler.DEFAULT));

        // without environment variables, the defaults of the app, here those that differ from the service's
        assertEquals("libs/gtfs-sample/src/main/resources/org/naviqore/gtfs/schedule/sample-feed-1.zip",
                appProperties.getGtfs().getStaticFeed().getUri());
        assertEquals(-1, appProperties.getTransfer().getDuration().getBetweenStopsMinimum());
        assertEquals(60, appProperties.getWalk().getDurationMinimum());
    }

    @Test
    void propertiesNotSet_keepTheDefaultsOfTheService() {
        AppProperties properties = bind(Map.of("app.raptor.days-to-scan", "1"));

        assertEquals(1, properties.getRaptor().getDaysToScan());
        assertNull(properties.getGtfs().getStaticFeed().getUri());
        assertEquals(ServiceConfig.DEFAULT_GTFS_STATIC_UPDATE_CRON,
                properties.getGtfs().getStaticFeed().getUpdateCron());
        assertEquals(ServiceConfig.DEFAULT_TRANSFER_DURATION_BETWEEN_STOPS_MINIMUM,
                properties.getTransfer().getDuration().getBetweenStopsMinimum());
        assertEquals(ServiceConfig.DEFAULT_WALK_DURATION_MINIMUM, properties.getWalk().getDurationMinimum());
        assertEquals(ServiceConfig.DEFAULT_OPTIMIZE_TRAVEL_TIME, properties.getRaptor().isOptimizeTravelTime());
        assertEquals(ServiceConfig.DEFAULT_CACHE_SIZE, properties.getCache().getServiceDaySize());
    }

    @Test
    void enums_bindIgnoringCase() {
        AppProperties properties = bind(
                Map.of("app.walk.calculator-type", "bee_line_distance", "app.cache.eviction-strategy", "mru"));

        assertEquals(WalkCalculatorType.BEE_LINE_DISTANCE, properties.getWalk().getCalculatorType());
        assertEquals(CacheEvictionStrategy.MRU, properties.getCache().getEvictionStrategy());
    }

    @Test
    void invalidWalkCalculatorType_failsToBind() {
        assertThrows(BindException.class, () -> bind(Map.of("app.walk.calculator-type", "INVALID")));
    }
}
