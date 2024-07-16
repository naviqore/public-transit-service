package ch.naviqore.service.impl.convert;

import ch.naviqore.gtfs.schedule.model.GtfsSchedule;
import ch.naviqore.raptor.router.RaptorDayMask;
import ch.naviqore.raptor.router.RaptorTripMaskProvider;
import ch.naviqore.raptor.router.TripMask;
import ch.naviqore.service.config.ServiceConfig;
import ch.naviqore.utils.cache.EvictionCache;
import lombok.Setter;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class GtfsTripMaskProvider implements RaptorTripMaskProvider {

    private final GtfsSchedule schedule;
    @Setter
    private Map<String, String[]> tripIds = null;
    private final GtfsTripMaskProvider.MaskCache cache;

    public GtfsTripMaskProvider(GtfsSchedule schedule) {
        this(schedule, ServiceConfig.DEFAULT_CACHE_SIZE,
                EvictionCache.Strategy.valueOf(ServiceConfig.DEFAULT_CACHE_EVICTION_STRATEGY.name()));
    }

    public GtfsTripMaskProvider(GtfsSchedule schedule, int cacheSize, EvictionCache.Strategy strategy) {
        this.schedule = schedule;
        this.cache = new GtfsTripMaskProvider.MaskCache(cacheSize, strategy);
    }

    public void clearCache() {
        cache.clear();
    }

    @Override
    public String getServiceIdForDate(LocalDate date) {
        return cache.getActiveServices(date);
    }

    @Override
    public RaptorDayMask getTripMask(LocalDate date) {
        if (tripIds == null) {
            throw new IllegalStateException("Trip ids not set");
        }
        return cache.getMask(date);
    }

    private RaptorDayMask buildTripMask(LocalDate date, String serviceId) {
        Map<String, TripMask> tripMasks = new HashMap<>();

        for (Map.Entry<String, String[]> entry : tripIds.entrySet()) {
            String routeId = entry.getKey();
            String[] tripIds = entry.getValue();
            boolean[] tripMask = new boolean[tripIds.length];
            for (int i = 0; i < tripIds.length; i++) {
                tripMask[i] = schedule.getTrips().get(tripIds[i]).getCalendar().isServiceAvailable(date);
            }

            tripMasks.put(routeId, new TripMask(tripMask));
        }

        return new RaptorDayMask(serviceId, date, tripMasks);
    }

    /**
     * Caches for active services (= GTFS calendars) per date and raptor trip mask instances.
     */
    private class MaskCache {
        private final EvictionCache<String, RaptorDayMask> maskCache;
        private final EvictionCache<LocalDate, String> activeServices;

        /**
         * @param cacheSize the maximum number of trip mask instances to be cached.
         * @param strategy  the cache eviction strategy.
         */
        MaskCache(int cacheSize, EvictionCache.Strategy strategy) {
            maskCache = new EvictionCache<>(cacheSize, strategy);
            activeServices = new EvictionCache<>(Math.min(365, cacheSize * 20), strategy);
        }

        public String getActiveServices(LocalDate date) {
            return activeServices.computeIfAbsent(date, () -> getActiveServicesFromSchedule(date));
        }

        // get cached mask or build a new one
        public RaptorDayMask getMask(LocalDate date) {
            String activeServices = this.getActiveServices(date);
            return maskCache.computeIfAbsent(activeServices, () -> buildTripMask(date, activeServices));
        }

        // get all active calendars form the gtfs for given date, serves as key for caching raptor instances
        private String getActiveServicesFromSchedule(LocalDate date) {
            return schedule.getCalendars()
                    .values()
                    .stream()
                    .filter(calendar -> calendar.isServiceAvailable(date))
                    .map(ch.naviqore.gtfs.schedule.model.Calendar::getId)
                    .collect(Collectors.joining(","));
        }

        // clear the cache, needs to be called when the GTFS schedule changes
        private void clear() {
            activeServices.clear();
            maskCache.clear();
        }

    }

}
