package org.naviqore.service.config;

import lombok.Builder;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.naviqore.raptor.router.RaptorConfig;
import org.naviqore.service.repo.TimetableRepository;

/**
 * Configuration to create a public transit service. The settings starting with {@code raptor} are those of the router,
 * see {@link RaptorConfig}, whose defaults they take.
 */
@Builder
@Value
public class ServiceConfig {

    public static final int DEFAULT_TRANSFER_DURATION_BETWEEN_STOPS_MINIMUM = 180;
    public static final int DEFAULT_TRANSFER_DURATION_ACCESS_EGRESS = 15;

    public static final int DEFAULT_WALK_SEARCH_RADIUS = 500;
    public static final WalkCalculatorType DEFAULT_WALK_CALCULATOR_TYPE = WalkCalculatorType.BEE_LINE_DISTANCE;
    public static final double DEFAULT_WALK_SPEED = 1.4;
    public static final int DEFAULT_WALK_DURATION_MINIMUM = 60;

    public static final boolean DEFAULT_OPTIMIZE_TRAVEL_TIME = true;

    public static final int DEFAULT_RAPTOR_DAYS_TO_SCAN = RaptorConfig.DEFAULT_DAYS_TO_SCAN;
    public static final int DEFAULT_RAPTOR_SAME_STOP_TRANSFER_DURATION_DEFAULT =
            RaptorConfig.DEFAULT_SAME_STOP_TRANSFER_DURATION_DEFAULT;
    public static final int DEFAULT_RAPTOR_STOP_TIME_CACHE_SIZE = RaptorConfig.DEFAULT_STOP_TIME_CACHE_SIZE;
    public static final CacheEvictionStrategy DEFAULT_RAPTOR_STOP_TIME_CACHE_STRATEGY = CacheEvictionStrategy.valueOf(
            RaptorConfig.DEFAULT_STOP_TIME_CACHE_STRATEGY.name());

    TimetableRepository timetableRepository;

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

    /**
     * Longest walk in seconds at either end of a connection, without the access or egress time, that is no leg of its
     * own. The times of the connection include the walk either way.
     */
    @Builder.Default
    int walkDurationMinimum = DEFAULT_WALK_DURATION_MINIMUM;

    /**
     * Whether a connection query without a time window optimizes the travel time of its connections: of the connections
     * arriving as early with as many transfers, it reports the one departing latest (for an arrival query: of those
     * departing as late, the one arriving earliest), found by a second query in the other direction. A query with a
     * time window reports those anyway.
     */
    @Builder.Default
    boolean optimizeTravelTime = DEFAULT_OPTIMIZE_TRAVEL_TIME;

    @Builder.Default
    int raptorDaysToScan = DEFAULT_RAPTOR_DAYS_TO_SCAN;

    @Builder.Default
    int raptorSameStopTransferDurationDefault = DEFAULT_RAPTOR_SAME_STOP_TRANSFER_DURATION_DEFAULT;

    /**
     * Service days whose stop times the router caches. The service caches which of the timetable's services run on
     * twenty times as many dates, at most 365.
     */
    @Builder.Default
    int raptorStopTimeCacheSize = DEFAULT_RAPTOR_STOP_TIME_CACHE_SIZE;

    @Builder.Default
    CacheEvictionStrategy raptorStopTimeCacheStrategy = DEFAULT_RAPTOR_STOP_TIME_CACHE_STRATEGY;

    public ServiceConfig(TimetableRepository timetableRepository, int transferDurationBetweenStopsMinimum,
                         int transferDurationAccessEgress, int walkSearchRadius, WalkCalculatorType walkCalculatorType,
                         double walkSpeed, int walkDurationMinimum, boolean optimizeTravelTime, int raptorDaysToScan,
                         int raptorSameStopTransferDurationDefault, int raptorStopTimeCacheSize,
                         CacheEvictionStrategy raptorStopTimeCacheStrategy) {
        this.timetableRepository = validateNonNull(timetableRepository, "timetableRepository");
        // negative values imply that transfers should not be generated
        this.transferDurationBetweenStopsMinimum = transferDurationBetweenStopsMinimum;
        this.transferDurationAccessEgress = validateNonNegative(transferDurationAccessEgress,
                "transferDurationAccessEgress");
        this.walkSearchRadius = validateNonNegative(walkSearchRadius, "walkSearchRadius");
        this.walkCalculatorType = validateNonNull(walkCalculatorType, "walkCalculatorType");
        this.walkSpeed = validatePositive(walkSpeed, "walkSpeed");
        this.walkDurationMinimum = validateNonNegative(walkDurationMinimum, "walkDurationMinimum");
        this.optimizeTravelTime = optimizeTravelTime;
        this.raptorDaysToScan = validatePositive(raptorDaysToScan, "raptorDaysToScan");
        this.raptorSameStopTransferDurationDefault = validateNonNegative(raptorSameStopTransferDurationDefault,
                "raptorSameStopTransferDurationDefault");
        this.raptorStopTimeCacheSize = validatePositive(raptorStopTimeCacheSize, "raptorStopTimeCacheSize");
        this.raptorStopTimeCacheStrategy = validateNonNull(raptorStopTimeCacheStrategy, "raptorStopTimeCacheStrategy");
    }

    private static <T> T validateNonNull(@Nullable T value, String name) {
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
