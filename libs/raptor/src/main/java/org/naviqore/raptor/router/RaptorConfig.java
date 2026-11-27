package org.naviqore.raptor.router;

import lombok.Builder;
import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.naviqore.raptor.QueryConfig;
import org.naviqore.utils.cache.EvictionCache;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@Getter
@Builder(toBuilder = true)
public class RaptorConfig {

    public static final int DEFAULT_DAYS_TO_SCAN = 3;
    public static final int DEFAULT_SAME_STOP_TRANSFER_DURATION_DEFAULT = 120;
    public static final int DEFAULT_STOP_TIME_CACHE_SIZE = 30;
    public static final EvictionCache.Strategy DEFAULT_STOP_TIME_CACHE_STRATEGY = EvictionCache.Strategy.LRU;

    @Builder.Default
    private final RaptorTripMaskProvider maskProvider = new NoMaskProvider();
    @Builder.Default
    private final int daysToScan = DEFAULT_DAYS_TO_SCAN;
    @Builder.Default
    private final int sameStopTransferDurationDefault = DEFAULT_SAME_STOP_TRANSFER_DURATION_DEFAULT;
    @Builder.Default
    private final int stopTimeCacheSize = DEFAULT_STOP_TIME_CACHE_SIZE;
    @Builder.Default
    private final EvictionCache.Strategy stopTimeCacheStrategy = DEFAULT_STOP_TIME_CACHE_STRATEGY;

    private RaptorConfig(RaptorTripMaskProvider maskProvider, int daysToScan, int sameStopTransferDurationDefault,
                         int stopTimeCacheSize, EvictionCache.Strategy stopTimeCacheStrategy) {

        if (daysToScan <= 0) {
            throw new IllegalArgumentException("Days to scan must be greater than 0.");
        }
        if (sameStopTransferDurationDefault < 0) {
            throw new IllegalArgumentException(
                    "Default same stop transfer duration must be greater than or equal to 0.");
        }
        if (stopTimeCacheSize <= 0) {
            throw new IllegalArgumentException("Stop time cache size must be greater than 0.");
        }

        this.maskProvider = maskProvider;
        this.daysToScan = daysToScan;
        this.sameStopTransferDurationDefault = sameStopTransferDurationDefault;
        this.stopTimeCacheSize = stopTimeCacheSize;
        this.stopTimeCacheStrategy = stopTimeCacheStrategy;
    }

    public static RaptorConfig defaults() {
        return builder().build();
    }

    public static class NoMaskProvider implements RaptorTripMaskProvider {
        private @Nullable Map<String, String[]> tripIds;

        @Override
        public void setTripIds(Map<String, String[]> tripIds) {
            this.tripIds = tripIds;
        }

        @Override
        public String getServiceIdForDate(LocalDate date) {
            return "NoMask";
        }

        @Override
        public DayTripMask getDayTripMask(LocalDate date, QueryConfig queryConfig) {
            Map<String, RouteTripMask> tripMasks = new HashMap<>();
            if (tripIds != null) {
                for (Map.Entry<String, String[]> entry : tripIds.entrySet()) {
                    boolean[] mask = new boolean[entry.getValue().length];
                    java.util.Arrays.fill(mask, true);
                    tripMasks.put(entry.getKey(), new RouteTripMask(mask));
                }
            }
            return new DayTripMask(getServiceIdForDate(date), date, tripMasks);
        }
    }
}