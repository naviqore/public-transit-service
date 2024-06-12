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
    public static final int DEFAULT_TRANSFER_TIME_BETWEEN_STOPS_MINIMUM = 120;
    public static final int DEFAULT_TRANSFER_TIME_SAME_STOP_DEFAULT = 120;
    public static final WalkCalculatorType DEFAULT_WALKING_CALCULATOR_TYPE = WalkCalculatorType.BEE_LINE_DISTANCE;
    public static final double DEFAULT_WALKING_SPEED = 1.4;
    public static final int DEFAULT_WALKING_DISTANCE_MAXIMUM = 500;
    public static final int DEFAULT_WALKING_DURATION_MINIMUM = 120;
    public static final int DEFAULT_CACHE_SIZE = 5;
    public static final CacheEvictionStrategy DEFAULT_CACHE_EVICTION_STRATEGY = CacheEvictionStrategy.LRU;

    private final String gtfsStaticUrl;
    private final String gtfsStaticUpdateCron;
    private final int transferTimeBetweenStopsMinimum;
    private final int transferTimeSameStopDefault;
    private final WalkCalculatorType walkingCalculatorType;
    private final double walkingSpeed;
    private final int walkingDistanceMaximum;
    private final int walkingDurationMinimum;
    private final int cacheSize;
    private final CacheEvictionStrategy cacheEvictionStrategy;

    public ServiceConfig(String gtfsStaticUrl, String gtfsStaticUpdateCron, int transferTimeBetweenStopsMinimum,
                         int transferTimeSameStopDefault, WalkCalculatorType walkingCalculatorType, double walkingSpeed,
                         int walkingDistanceMaximum, int walkingDurationMinimum, int cacheSize,
                         CacheEvictionStrategy cacheEvictionStrategy) {

        this.gtfsStaticUrl = validateNonNull(gtfsStaticUrl, "gtfsStaticUrl");
        this.gtfsStaticUpdateCron = validateNonNull(gtfsStaticUpdateCron, "gtfsStaticUpdateCron");
        this.transferTimeBetweenStopsMinimum = validateNonNegative(transferTimeBetweenStopsMinimum,
                "transferTimeBetweenStopsMinimum");
        this.transferTimeSameStopDefault = validateNonNegative(transferTimeSameStopDefault,
                "transferTimeSameStopDefault");
        this.walkingCalculatorType = validateNonNull(walkingCalculatorType, "walkingCalculatorType");
        this.walkingSpeed = validatePositive(walkingSpeed, "walkingSpeed");
        this.walkingDistanceMaximum = validateNonNegative(walkingDistanceMaximum, "walkingDistanceMaximum");
        this.walkingDurationMinimum = validateNonNegative(walkingDurationMinimum, "walkingDurationMinimum");
        this.cacheSize = validatePositive(cacheSize, "cacheSize");
        this.cacheEvictionStrategy = validateNonNull(cacheEvictionStrategy, "cacheEvictionStrategy");
    }

    /**
     * Constructor with defaults
     */
    public ServiceConfig(String gtfsStaticUrl) {
        this(gtfsStaticUrl, DEFAULT_GTFS_STATIC_UPDATE_CRON, DEFAULT_TRANSFER_TIME_BETWEEN_STOPS_MINIMUM,
                DEFAULT_TRANSFER_TIME_SAME_STOP_DEFAULT, DEFAULT_WALKING_CALCULATOR_TYPE, DEFAULT_WALKING_SPEED,
                DEFAULT_WALKING_DISTANCE_MAXIMUM, DEFAULT_WALKING_DURATION_MINIMUM, DEFAULT_CACHE_SIZE,
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
