package ch.naviqore.raptor.router;

import ch.naviqore.utils.cache.EvictionCache;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@Getter
@NoArgsConstructor
@ToString
public class RaptorConfig {

    private RaptorTripMaskProvider maskProvider = new NoMaskProvider();

    private int daysToScan = 1;
    private int defaultSameStopTransferTime = 120;

    private int stopTimeCacheSize = 5;
    private EvictionCache.Strategy stopTimeCacheStrategy = EvictionCache.Strategy.LRU;

    public RaptorConfig(int daysToScan, int defaultSameStopTransferTime, int stopTimeCacheSize,
                        @NotNull EvictionCache.Strategy stopTimeCacheStrategy,
                        @NotNull RaptorTripMaskProvider maskProvider) {
        setDaysToScan(daysToScan);
        setDefaultSameStopTransferTime(defaultSameStopTransferTime);
        setStopTimeCacheSize(stopTimeCacheSize);
        setStopTimeCacheStrategy(stopTimeCacheStrategy);
        setMaskProvider(maskProvider);
    }

    public void setDaysToScan(int daysToScan) {
        if (daysToScan <= 0) {
            throw new IllegalArgumentException("Days to scan must be greater than 0.");
        }
        this.daysToScan = daysToScan;
    }

    public void setDefaultSameStopTransferTime(int defaultSameStopTransferTime) {
        if (defaultSameStopTransferTime < 0) {
            throw new IllegalArgumentException("Default same stop transfer time must be greater than or equal to 0.");
        }
        this.defaultSameStopTransferTime = defaultSameStopTransferTime;
    }

    public void setStopTimeCacheSize(int stopTimeCacheSize) {
        if (stopTimeCacheSize <= 0) {
            throw new IllegalArgumentException("Stop time cache size must be greater than 0.");
        }
        this.stopTimeCacheSize = stopTimeCacheSize;
    }

    public void setMaskProvider(@NotNull RaptorTripMaskProvider maskProvider) {
        this.maskProvider = maskProvider;
    }

    public void setStopTimeCacheStrategy(@NotNull EvictionCache.Strategy stopTimeCacheStrategy) {
        this.stopTimeCacheStrategy = stopTimeCacheStrategy;
    }

    /**
     * No mask provider as default mask provider (no masking of trips).
     */
    @Setter
    @NoArgsConstructor
    static class NoMaskProvider implements RaptorTripMaskProvider {

        private final static int EARLIEST_TRIP_TIME = 0;
        private final static int LATEST_TRIP_TIME = 48 * 60 * 60; // 48 hours in seconds
        Map<String, String[]> tripIds = null;

        @Override
        public String getServiceIdForDate(LocalDate date) {
            return "NoMask";
        }

        @Override
        public RaptorDayMask getTripMask(LocalDate date) {
            int earliestTripTime = EARLIEST_TRIP_TIME;
            int latestTripTime = LATEST_TRIP_TIME;

            Map<String, TripMask> tripMasks = new HashMap<>();
            for (Map.Entry<String, String[]> entry : tripIds.entrySet()) {
                String routeId = entry.getKey();
                String[] tripIds = entry.getValue();
                boolean[] tripMask = new boolean[tripIds.length];
                for (int i = 0; i < tripIds.length; i++) {
                    tripMask[i] = true;
                }
                tripMasks.put(routeId, new TripMask(earliestTripTime, latestTripTime, tripMask));
            }

            return new RaptorDayMask(getServiceIdForDate(date), date, earliestTripTime, latestTripTime, tripMasks);
        }
    }

}
