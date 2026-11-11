package org.naviqore.app.config;

import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.Nullable;
import org.naviqore.service.config.ServiceConfig;
import org.naviqore.service.config.ServiceConfig.CacheEvictionStrategy;
import org.naviqore.service.config.ServiceConfig.WalkCalculatorType;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.Name;

/**
 * The configuration of the public transit service, bound from the properties under {@code app}: the GTFS feed,
 * transfers, walks, the router and its cache, each a group of its own. A property that is not set takes the default of
 * {@link ServiceConfig}; {@code application.properties} sets each one from an environment variable, with the defaults
 * of the app.
 */
@ConfigurationProperties(prefix = "app")
@Getter
public class AppProperties {

    private final Gtfs gtfs = new Gtfs();
    private final Transfer transfer = new Transfer();
    private final Walk walk = new Walk();
    private final Raptor raptor = new Raptor();
    private final Cache cache = new Cache();

    /**
     * The GTFS feed.
     */
    @Getter
    public static class Gtfs {

        /**
         * Bound under {@code static}, which is a Java keyword.
         */
        @Name("static")
        private final Static staticFeed = new Static();

        /**
         * The static GTFS feed and its updates.
         */
        @Getter
        @Setter
        public static class Static {

            /**
             * URL, file path or S3 URI of the static GTFS feed.
             */
            private @Nullable String uri;

            /**
             * Cron expression for updating the static GTFS feed.
             */
            private String updateCron = ServiceConfig.DEFAULT_GTFS_STATIC_UPDATE_CRON;
        }
    }

    /**
     * Transfers between trips, at a stop or between stops.
     */
    @Getter
    public static class Transfer {

        private final Duration duration = new Duration();

        /**
         * The durations of transfers, in seconds.
         */
        @Getter
        @Setter
        public static class Duration {

            /**
             * Same stop transfer time where the timetable gives none.
             */
            private int sameStopDefault = ServiceConfig.DEFAULT_TRANSFER_DURATION_SAME_STOP_DEFAULT;

            /**
             * Shortest generated walk between stops; negative for none.
             */
            private int betweenStopsMinimum = ServiceConfig.DEFAULT_TRANSFER_DURATION_BETWEEN_STOPS_MINIMUM;

            /**
             * Time to get on and off a vehicle, added to walks.
             */
            private int accessEgress = ServiceConfig.DEFAULT_TRANSFER_DURATION_ACCESS_EGRESS;
        }
    }

    /**
     * Walks between stops and to and from locations.
     */
    @Getter
    @Setter
    public static class Walk {

        /**
         * Radius in meters for stops near a location and for generated walks between stops.
         */
        private int searchRadius = ServiceConfig.DEFAULT_WALK_SEARCH_RADIUS;

        /**
         * How walking durations are calculated.
         */
        private WalkCalculatorType calculatorType = ServiceConfig.DEFAULT_WALK_CALCULATOR_TYPE;

        /**
         * Walking speed in meters per second.
         */
        private double speed = ServiceConfig.DEFAULT_WALK_SPEED;

        /**
         * Shortest walk in seconds to or from a location that a connection includes.
         */
        private int durationMinimum = ServiceConfig.DEFAULT_WALK_DURATION_MINIMUM;
    }

    /**
     * The router.
     */
    @Getter
    @Setter
    public static class Raptor {

        /**
         * Service days around the date of a query that are scanned for trips.
         */
        private int daysToScan = ServiceConfig.DEFAULT_RAPTOR_DAYS_TO_SCAN;

        /**
         * Whether a connection query without a time window optimizes the travel time of its connections.
         */
        private boolean optimizeTravelTime = ServiceConfig.DEFAULT_OPTIMIZE_TRAVEL_TIME;
    }

    /**
     * The cache of the service days' stop times.
     */
    @Getter
    @Setter
    public static class Cache {

        /**
         * Service days whose stop times are cached.
         */
        private int serviceDaySize = ServiceConfig.DEFAULT_CACHE_SIZE;

        /**
         * Which service day is evicted from a full cache.
         */
        private CacheEvictionStrategy evictionStrategy = ServiceConfig.DEFAULT_CACHE_EVICTION_STRATEGY;
    }
}
