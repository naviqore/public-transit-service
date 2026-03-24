package org.naviqore.raptor.router;

import lombok.Builder;
import lombok.Getter;
import org.naviqore.raptor.QueryConfig;
import org.naviqore.utils.cache.EvictionCache;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@Getter
@Builder(toBuilder = true)
public class RaptorConfig {

    @Builder.Default
    private final RaptorTripMaskProvider maskProvider = new NoMaskProvider();
    @Builder.Default
    private final int daysToScan = 1;
    @Builder.Default
    private final int sameStopTransferDurationDefault = 120;
    @Builder.Default
    private final int stopTimeCacheSize = 5;
    @Builder.Default
    private final int raptorRangeDefault = 1800;
    @Builder.Default
    private final EvictionCache.Strategy stopTimeCacheStrategy = EvictionCache.Strategy.LRU;

    private RaptorConfig(RaptorTripMaskProvider maskProvider, int daysToScan, int sameStopTransferDurationDefault,
                         int stopTimeCacheSize, int raptorRangeDefault, EvictionCache.Strategy stopTimeCacheStrategy) {

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
        this.raptorRangeDefault = raptorRangeDefault;
        this.stopTimeCacheStrategy = stopTimeCacheStrategy;
    }

    public static class NoMaskProvider implements RaptorTripMaskProvider {
        private Map<String, String[]> tripIds;

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