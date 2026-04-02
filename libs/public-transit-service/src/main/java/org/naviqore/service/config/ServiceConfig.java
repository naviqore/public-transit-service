package org.naviqore.service.config;

import lombok.Builder;
import lombok.Value;
import org.naviqore.service.repo.GtfsScheduleRepository;

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
    public static final int DEFAULT_RAPTOR_RANGE = -1; // -1 means no range raptor

    public static final int DEFAULT_CACHE_SIZE = 5;
    public static final CacheEvictionStrategy DEFAULT_CACHE_EVICTION_STRATEGY = CacheEvictionStrategy.LRU;

    GtfsScheduleRepository gtfsScheduleRepository;

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

    @Builder.Default
    int raptorRange = DEFAULT_RAPTOR_RANGE;

    @Builder.Default
    int cacheServiceDaySize = DEFAULT_CACHE_SIZE;

    @Builder.Default
    CacheEvictionStrategy cacheEvictionStrategy = DEFAULT_CACHE_EVICTION_STRATEGY;

    public ServiceConfig(GtfsScheduleRepository gtfsScheduleRepository, String gtfsStaticUpdateCron,
                         int transferDurationSameStopDefault, int transferDurationBetweenStopsMinimum,
                         int transferDurationAccessEgress, int walkSearchRadius, WalkCalculatorType walkCalculatorType,
                         double walkSpeed, int walkDurationMinimum, int raptorDaysToScan, int raptorRange,
                         int cacheServiceDaySize, CacheEvictionStrategy cacheEvictionStrategy) {
        this.gtfsScheduleRepository = validateNonNull(gtfsScheduleRepository, "gtfsScheduleRepository");
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
