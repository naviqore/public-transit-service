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
 * The configuration of the app, bound from the properties under {@code naviqore} and grouped by the component that
 * applies them: the app reads the GTFS feed ({@code gtfs}), the public transit service ({@code service}) and its RAPTOR
 * router ({@code raptor}) answer the queries, and the app limits the routing requests ({@code routing}) and serves the
 * home page ({@code home-page}). A setting of the service or the router that is not set takes the default of
 * {@link ServiceConfig}.
 */
@ConfigurationProperties(prefix = "naviqore")
@Getter
public class NaviqoreProperties {

    private final Gtfs gtfs = new Gtfs();
    private final Service service = new Service();
    private final Raptor raptor = new Raptor();
    private final Routing routing = new Routing();
    private final HomePage homePage = new HomePage();

    /**
     * The GTFS feed, which the app reads and updates.
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
             * URL, file path or S3 URI of the static GTFS feed; there is no default.
             */
            private @Nullable String uri;

            /**
             * Cron expression for updating the static GTFS feed, daily at 4 AM by default.
             */
            private String updateCron = "0 0 4 * * *";
        }
    }

    /**
     * The public transit service: the walks it generates and adds to connections, and its connection queries.
     */
    @Getter
    public static class Service {

        private final Transfer transfer = new Transfer();
        private final Walk walk = new Walk();

        /**
         * Whether a connection query without a time window optimizes the travel time of its connections.
         */
        @Setter
        private boolean optimizeTravelTime = ServiceConfig.DEFAULT_OPTIMIZE_TRAVEL_TIME;

        /**
         * Transfers between stops.
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
             * Longest walk in seconds, without the access or egress time, that is no leg of its own.
             */
            private int durationMinimum = ServiceConfig.DEFAULT_WALK_DURATION_MINIMUM;
        }
    }

    /**
     * The RAPTOR router, configured by the service.
     */
    @Getter
    @Setter
    public static class Raptor {

        /**
         * Service days around the date of a query that are scanned for trips.
         */
        private int daysToScan = ServiceConfig.DEFAULT_RAPTOR_DAYS_TO_SCAN;

        /**
         * Same stop transfer time in seconds where the timetable gives none.
         */
        private int sameStopTransferDurationDefault = ServiceConfig.DEFAULT_RAPTOR_SAME_STOP_TRANSFER_DURATION_DEFAULT;

        /**
         * Service days whose stop times are cached.
         */
        private int stopTimeCacheSize = ServiceConfig.DEFAULT_RAPTOR_STOP_TIME_CACHE_SIZE;

        /**
         * Which service day is evicted from a full cache.
         */
        private CacheEvictionStrategy stopTimeCacheStrategy = ServiceConfig.DEFAULT_RAPTOR_STOP_TIME_CACHE_STRATEGY;
    }

    /**
     * The routing requests of the REST API.
     */
    @Getter
    public static class Routing {

        /**
         * One day, in seconds.
         */
        public static final int DEFAULT_TIME_WINDOW_DURATION_MAXIMUM = 24 * 60 * 60;

        /**
         * Longest time window of a request, in seconds. A query over a time window runs a search for each departure in
         * it and reads the stop times of every service day it reaches, so a request with a longer one is refused.
         */
        private int timeWindowDurationMaximum = DEFAULT_TIME_WINDOW_DURATION_MAXIMUM;

        public void setTimeWindowDurationMaximum(int timeWindowDurationMaximum) {
            if (timeWindowDurationMaximum < 0) {
                throw new IllegalArgumentException("The time window duration maximum must not be negative.");
            }
            this.timeWindowDurationMaximum = timeWindowDurationMaximum;
        }
    }

    /**
     * The home page at the root path. A condition of its controller reads the property, which binds here as well.
     */
    @Getter
    @Setter
    public static class HomePage {

        /**
         * Whether the app serves the home page.
         */
        private boolean enabled = true;
    }
}
