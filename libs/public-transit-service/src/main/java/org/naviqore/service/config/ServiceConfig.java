package org.naviqore.service.config;

import lombok.Builder;
import lombok.Value;

/**
 * Configuration to create a public transit service.
 */
@Builder
@Value
public class ServiceConfig {

    // note: The defaults should match the default values in the application.properties file.
    public static final String DEFAULT_GTFS_STATIC_UPDATE_CRON = "0 0 4 * * *";

    public static final int DEFAULT_TRANSFER_TIME_SAME_STOP_DEFAULT = 120;
    public static final int DEFAULT_TRANSFER_TIME_BETWEEN_STOPS_MINIMUM = 180;
    public static final int DEFAULT_TRANSFER_TIME_ACCESS_EGRESS = 15;

    public static final int DEFAULT_WALKING_SEARCH_RADIUS = 500;
    public static final WalkCalculatorType DEFAULT_WALKING_CALCULATOR_TYPE = WalkCalculatorType.BEE_LINE_DISTANCE;
    public static final double DEFAULT_WALKING_SPEED = 1.4;
    public static final int DEFAULT_WALKING_DURATION_MINIMUM = 120;

    public static final int DEFAULT_RAPTOR_DAYS_TO_SCAN = 3;
    public static final int DEFAULT_RAPTOR_RANGE = -1; // -1 means no range raptor

    public static final int DEFAULT_CACHE_SIZE = 5;
    public static final CacheEvictionStrategy DEFAULT_CACHE_EVICTION_STRATEGY = CacheEvictionStrategy.LRU;

    String gtfsStaticUri;

    @Builder.Default
    String gtfsStaticUpdateCron = DEFAULT_GTFS_STATIC_UPDATE_CRON;

    @Builder.Default
    int transferTimeSameStopDefault = DEFAULT_TRANSFER_TIME_SAME_STOP_DEFAULT;

    @Builder.Default
    int transferTimeBetweenStopsMinimum = DEFAULT_TRANSFER_TIME_BETWEEN_STOPS_MINIMUM;

    @Builder.Default
    int transferTimeAccessEgress = DEFAULT_TRANSFER_TIME_ACCESS_EGRESS;

    @Builder.Default
    int walkingSearchRadius = DEFAULT_WALKING_SEARCH_RADIUS;

    @Builder.Default
    WalkCalculatorType walkingCalculatorType = DEFAULT_WALKING_CALCULATOR_TYPE;

    @Builder.Default
    double walkingSpeed = DEFAULT_WALKING_SPEED;

    @Builder.Default
    int walkingDurationMinimum = DEFAULT_WALKING_DURATION_MINIMUM;

    @Builder.Default
    int raptorDaysToScan = DEFAULT_RAPTOR_DAYS_TO_SCAN;

    @Builder.Default
    int raptorRange = DEFAULT_RAPTOR_RANGE;

    @Builder.Default
    int cacheServiceDaySize = DEFAULT_CACHE_SIZE;

    @Builder.Default
    CacheEvictionStrategy cacheEvictionStrategy = DEFAULT_CACHE_EVICTION_STRATEGY;

    public ServiceConfig(String gtfsStaticUri, String gtfsStaticUpdateCron, int transferTimeSameStopDefault,
                         int transferTimeBetweenStopsMinimum, int transferTimeAccessEgress, int walkingSearchRadius,
                         WalkCalculatorType walkingCalculatorType, double walkingSpeed, int walkingDurationMinimum,
                         int raptorDaysToScan, int raptorRange, int cacheServiceDaySize,
                         CacheEvictionStrategy cacheEvictionStrategy) {
        this.gtfsStaticUri = validateNonNull(gtfsStaticUri, "gtfsStaticUrl");
        this.gtfsStaticUpdateCron = validateNonNull(gtfsStaticUpdateCron, "gtfsStaticUpdateCron");
        this.transferTimeSameStopDefault = validateNonNegative(transferTimeSameStopDefault,
                "transferTimeSameStopDefault");
        // negative values imply that transfers should not be generated
        this.transferTimeBetweenStopsMinimum = transferTimeBetweenStopsMinimum;
        this.transferTimeAccessEgress = validateNonNegative(transferTimeAccessEgress, "transferTimeAccessEgress");
        this.walkingSearchRadius = validateNonNegative(walkingSearchRadius, "walkingSearchRadius");
        this.walkingCalculatorType = validateNonNull(walkingCalculatorType, "walkingCalculatorType");
        this.walkingSpeed = validatePositive(walkingSpeed, "walkingSpeed");
        this.walkingDurationMinimum = validateNonNegative(walkingDurationMinimum, "walkingDurationMinimum");
        this.raptorDaysToScan = validatePositive(raptorDaysToScan, "raptorDaysToScan");
        this.raptorRange = raptorRange;
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
