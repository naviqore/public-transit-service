package org.naviqore.service.config;

import lombok.Builder;
import lombok.Value;
import org.naviqore.service.repo.TimetableRepository;

/**
 * Configuration to create a public transit service.
 */
@Builder
@Value
public class ServiceConfig {

    // note: The defaults should match the default values in the application.properties file.
    public static final String DEFAULT_GTFS_STATIC_UPDATE_CRON = "0 0 4 * * *";

    public static final int DEFAULT_TRANSFER_DURATION_SAME_STOP_DEFAULT = 120;
    public static final int DEFAULT_TRANSFER_DURATION_BETWEEN_STOPS_MINIMUM = 180;
    public static final int DEFAULT_TRANSFER_DURATION_ACCESS_EGRESS = 15;

    public static final int DEFAULT_WALK_SEARCH_RADIUS = 500;
    public static final WalkCalculatorType DEFAULT_WALK_CALCULATOR_TYPE = WalkCalculatorType.BEE_LINE_DISTANCE;
    public static final double DEFAULT_WALK_SPEED = 1.4;
    public static final int DEFAULT_WALK_DURATION_MINIMUM = 120;

    public static final int DEFAULT_RAPTOR_DAYS_TO_SCAN = 3;
    public static final boolean DEFAULT_OPTIMIZE_TRAVEL_TIME = true;

    public static final int DEFAULT_CACHE_SIZE = 30;
    public static final CacheEvictionStrategy DEFAULT_CACHE_EVICTION_STRATEGY = CacheEvictionStrategy.LRU;

    TimetableRepository timetableRepository;

    @Builder.Default
    String gtfsStaticUpdateCron = DEFAULT_GTFS_STATIC_UPDATE_CRON;

    @Builder.Default
    int transferDurationSameStopDefault = DEFAULT_TRANSFER_DURATION_SAME_STOP_DEFAULT;

    @Builder.Default
    int transferDurationBetweenStopsMinimum = DEFAULT_TRANSFER_DURATION_BETWEEN_STOPS_MINIMUM;

    @Builder.Default
    int transferDurationAccessEgress = DEFAULT_TRANSFER_DURATION_ACCESS_EGRESS;

    @Builder.Default
    int walkSearchRadius = DEFAULT_WALK_SEARCH_RADIUS;

    @Builder.Default
    WalkCalculatorType walkCalculatorType = DEFAULT_WALK_CALCULATOR_TYPE;

    @Builder.Default
    double walkSpeed = DEFAULT_WALK_SPEED;

    @Builder.Default
    int walkDurationMinimum = DEFAULT_WALK_DURATION_MINIMUM;

    @Builder.Default
    int raptorDaysToScan = DEFAULT_RAPTOR_DAYS_TO_SCAN;

    /**
     * Whether a connection query without a time window optimizes the travel time of its connections: of the connections
     * arriving as early with as many transfers, it reports the one departing latest (for an arrival query: of those
     * departing as late, the one arriving earliest), found by a second query in the other direction. A query with a
     * time window reports those anyway.
     */
    @Builder.Default
    boolean optimizeTravelTime = DEFAULT_OPTIMIZE_TRAVEL_TIME;

    @Builder.Default
    int cacheServiceDaySize = DEFAULT_CACHE_SIZE;

    @Builder.Default
    CacheEvictionStrategy cacheEvictionStrategy = DEFAULT_CACHE_EVICTION_STRATEGY;

    public ServiceConfig(TimetableRepository timetableRepository, String gtfsStaticUpdateCron,
                         int transferDurationSameStopDefault, int transferDurationBetweenStopsMinimum,
                         int transferDurationAccessEgress, int walkSearchRadius, WalkCalculatorType walkCalculatorType,
                         double walkSpeed, int walkDurationMinimum, int raptorDaysToScan, boolean optimizeTravelTime,
                         int cacheServiceDaySize, CacheEvictionStrategy cacheEvictionStrategy) {
        this.timetableRepository = validateNonNull(timetableRepository, "timetableRepository");
        this.gtfsStaticUpdateCron = validateNonNull(gtfsStaticUpdateCron, "gtfsStaticUpdateCron");
        this.transferDurationSameStopDefault = validateNonNegative(transferDurationSameStopDefault,
                "transferDurationSameStopDefault");
        // negative values imply that transfers should not be generated
        this.transferDurationBetweenStopsMinimum = transferDurationBetweenStopsMinimum;
        this.transferDurationAccessEgress = validateNonNegative(transferDurationAccessEgress,
                "transferDurationAccessEgress");
        this.walkSearchRadius = validateNonNegative(walkSearchRadius, "walkSearchRadius");
        this.walkCalculatorType = validateNonNull(walkCalculatorType, "walkCalculatorType");
        this.walkSpeed = validatePositive(walkSpeed, "walkSpeed");
        this.walkDurationMinimum = validateNonNegative(walkDurationMinimum, "walkDurationMinimum");
        this.raptorDaysToScan = validatePositive(raptorDaysToScan, "raptorDaysToScan");
        this.optimizeTravelTime = optimizeTravelTime;
        this.cacheServiceDaySize = validatePositive(cacheServiceDaySize, "cacheServiceDaySize");
        this.cacheEvictionStrategy = validateNonNull(cacheEvictionStrategy, "cacheEvictionStrategy");
    }

    private static <T> T validateNonNull(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null.");
        }
        return value;
    }

    private static <T extends Number> T validateNonNegative(T value, String name) {
        if (value.doubleValue() < 0) {
            throw new IllegalArgumentException(name + " must be greater than or equal to 0.");
        }
        return value;
    }

    private static <T extends Number> T validatePositive(T value, String name) {
        if (value.doubleValue() <= 0) {
            throw new IllegalArgumentException(name + " must be greater than 0.");
        }
        return value;
    }

    public enum WalkCalculatorType {
        BEE_LINE_DISTANCE
    }

    public enum CacheEvictionStrategy {
        LRU,
        MRU
    }
}
