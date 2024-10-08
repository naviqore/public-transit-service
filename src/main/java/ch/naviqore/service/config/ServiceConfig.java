package ch.naviqore.service.config;

import lombok.Getter;
import lombok.ToString;

/**
 * Configuration to create a public transit service.
 */
@Getter
@ToString
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

    public static final int DEFAULT_MAX_DAYS_TO_SCAN = 3;
    public static final int DEFAULT_RAPTOR_RANGE = -1; // -1 means no range raptor

    public static final int DEFAULT_CACHE_SIZE = 5;
    public static final CacheEvictionStrategy DEFAULT_CACHE_EVICTION_STRATEGY = CacheEvictionStrategy.LRU;

    private final String gtfsStaticUri;
    private final String gtfsStaticUpdateCron;
    private final int transferTimeSameStopDefault;
    private final int transferTimeBetweenStopsMinimum;
    private final int transferTimeAccessEgress;
    private final int walkingSearchRadius;
    private final WalkCalculatorType walkingCalculatorType;
    private final double walkingSpeed;
    private final int walkingDurationMinimum;
    private final int raptorRange;
    private final int raptorDaysToScan;
    private final int cacheServiceDaySize;

    private final CacheEvictionStrategy cacheEvictionStrategy;

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

    /**
     * Constructor with defaults
     */
    public ServiceConfig(String gtfsStaticUri) {
        this(gtfsStaticUri, DEFAULT_GTFS_STATIC_UPDATE_CRON, DEFAULT_TRANSFER_TIME_SAME_STOP_DEFAULT,
                DEFAULT_TRANSFER_TIME_BETWEEN_STOPS_MINIMUM, DEFAULT_TRANSFER_TIME_ACCESS_EGRESS,
                DEFAULT_WALKING_SEARCH_RADIUS, DEFAULT_WALKING_CALCULATOR_TYPE, DEFAULT_WALKING_SPEED,
                DEFAULT_WALKING_DURATION_MINIMUM, DEFAULT_MAX_DAYS_TO_SCAN, DEFAULT_RAPTOR_RANGE, DEFAULT_CACHE_SIZE,
                DEFAULT_CACHE_EVICTION_STRATEGY);
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
