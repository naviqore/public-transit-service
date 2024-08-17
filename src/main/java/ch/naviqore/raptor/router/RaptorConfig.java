package ch.naviqore.raptor.router;

import ch.naviqore.raptor.QueryConfig;
import ch.naviqore.utils.cache.EvictionCache;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@Getter
@NoArgsConstructor
@ToString
public class RaptorConfig {

    @Setter
    private RaptorTripMaskProvider maskProvider = new NoMaskProvider();

    private int daysToScan = 1;
    private int defaultSameStopTransferTime = 120;

    @Setter
    private int raptorRange = 1800; // 30 minutes

    private int stopTimeCacheSize = 5;
    @Setter
    private EvictionCache.Strategy stopTimeCacheStrategy = EvictionCache.Strategy.LRU;

    public RaptorConfig(int daysToScan, int raptorRange, int defaultSameStopTransferTime, int stopTimeCacheSize,
                        EvictionCache.Strategy stopTimeCacheStrategy, RaptorTripMaskProvider maskProvider) {
        setRaptorRange(raptorRange);
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

    public RaptorConfig copy() {
        return new RaptorConfig(daysToScan, raptorRange, defaultSameStopTransferTime, stopTimeCacheSize,
                stopTimeCacheStrategy, maskProvider);
    }

    /**
     * No mask provider as default mask provider (no masking of trips).
     */
    @Setter
    @NoArgsConstructor

    static class NoMaskProvider implements RaptorTripMaskProvider {

        Map<String, String[]> tripIds = null;

        @Override
        public String getServiceIdForDate(LocalDate date) {
            return "NoMask";
        }

        @Override
        public DayTripMask getDayTripMask(LocalDate date, QueryConfig queryConfig) {
            Map<String, RouteTripMask> tripMasks = new HashMap<>();
            for (Map.Entry<String, String[]> entry : tripIds.entrySet()) {
                String routeId = entry.getKey();
                String[] tripIds = entry.getValue();
                boolean[] tripMask = new boolean[tripIds.length];
                for (int i = 0; i < tripIds.length; i++) {
                    tripMask[i] = true;
                }
                tripMasks.put(routeId, new RouteTripMask(tripMask));
            }

            return new DayTripMask(getServiceIdForDate(date), date, tripMasks);
        }
    }

}
