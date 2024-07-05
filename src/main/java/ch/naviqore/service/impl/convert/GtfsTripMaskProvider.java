package ch.naviqore.service.impl.convert;

import ch.naviqore.gtfs.schedule.model.Calendar;
import ch.naviqore.gtfs.schedule.model.GtfsSchedule;
import ch.naviqore.raptor.router.RaptorTripMask;
import ch.naviqore.raptor.router.RaptorTripMaskProvider;
import ch.naviqore.service.config.ServiceConfig;
import ch.naviqore.utils.cache.EvictionCache;
import lombok.RequiredArgsConstructor;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class GtfsTripMaskProvider implements RaptorTripMaskProvider {

    private final GtfsSchedule schedule;
    private final GtfsTripMaskProvider.MaskCache cache;

    GtfsTripMaskProvider(ServiceConfig config, GtfsSchedule schedule) {
        this.schedule = schedule;
        this.cache = new GtfsTripMaskProvider.MaskCache(config.getCacheSize(),
                EvictionCache.Strategy.valueOf(config.getCacheEvictionStrategy().name()));
    }

    public void clearCache() {
        cache.clear();
    }

    @Override
    public Map<String, RaptorTripMask> getTripMask(Map<String, String[]> routeTripIds, LocalDate date) {
        return cache.getMask(date, routeTripIds);
    }

    private Map<String, RaptorTripMask> buildTripMask(Map<String, String[]> routeTripIds, LocalDate date) {
        Map<String, RaptorTripMask> tripMasks = new HashMap<>();

        for (Map.Entry<String, String[]> entry : routeTripIds.entrySet()) {
            String routeId = entry.getKey();
            String[] tripIds = entry.getValue();
            boolean[] tripMask = new boolean[tripIds.length];
            int earliestTripTime = -1;
            int latestTripTime = -1;
            for (int i = 0; i < tripIds.length; i++) {
                tripMask[i] = schedule.getTrips().get(tripIds[i]).getCalendar().isServiceAvailable(date);
            }

            // get first instance of true in tripMask
            for (int i = 0; i < tripMask.length; i++) {
                if (tripMask[i]) {
                    earliestTripTime = schedule.getTrips()
                            .get(tripIds[i])
                            .getStopTimes()
                            .getFirst()
                            .departure()
                            .getTotalSeconds();
                    break;
                }
            }

            for (int i = tripMask.length - 1; i >= 0; i--) {
                if (tripMask[i]) {
                    latestTripTime = schedule.getTrips()
                            .get(tripIds[i])
                            .getStopTimes()
                            .getLast()
                            .arrival()
                            .getTotalSeconds();
                    break;
                }
            }

            tripMasks.put(routeId, new TripMask(earliestTripTime, latestTripTime, tripMask));
        }

        return tripMasks;

    }

    record TripMask(int earliestTripTime, int latestTripTime, boolean[] tripMask) implements RaptorTripMask {
    }

    /**
     * Caches for active services (= GTFS calendars) per date and raptor trip mask instances.
     */
    private class MaskCache {
        private final EvictionCache<Set<Calendar>, Map<String, RaptorTripMask>> maskCache;
        private final EvictionCache<LocalDate, Set<ch.naviqore.gtfs.schedule.model.Calendar>> activeServices;

        /**
         * @param cacheSize the maximum number of trip mask instances to be cached.
         * @param strategy  the cache eviction strategy.
         */
        MaskCache(int cacheSize, EvictionCache.Strategy strategy) {
            maskCache = new EvictionCache<>(cacheSize, strategy);
            activeServices = new EvictionCache<>(Math.min(365, cacheSize * 20), strategy);
        }

        // get cached mask or build a new one
        public Map<String, RaptorTripMask> getMask(LocalDate date, Map<String, String[]> routeTripIds) {
            Set<ch.naviqore.gtfs.schedule.model.Calendar> activeServices = this.activeServices.computeIfAbsent(date,
                    () -> getActiveServices(date));
            return maskCache.computeIfAbsent(activeServices, () -> buildTripMask(routeTripIds, date));
        }

        // get all active calendars form the gtfs for given date, serves as key for caching raptor instances
        private Set<ch.naviqore.gtfs.schedule.model.Calendar> getActiveServices(LocalDate date) {
            return schedule.getCalendars()
                    .values()
                    .stream()
                    .filter(calendar -> calendar.isServiceAvailable(date))
                    .collect(Collectors.toSet());
        }

        // clear the cache, needs to be called when the GTFS schedule changes
        private void clear() {
            activeServices.clear();
            maskCache.clear();
        }

    }

}
