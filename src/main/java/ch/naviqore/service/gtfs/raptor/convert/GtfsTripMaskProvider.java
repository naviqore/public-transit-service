package ch.naviqore.service.gtfs.raptor.convert;

import ch.naviqore.gtfs.schedule.model.GtfsSchedule;
import ch.naviqore.raptor.QueryConfig;
import ch.naviqore.raptor.router.RaptorTripMaskProvider;
import ch.naviqore.service.config.ServiceConfig;
import ch.naviqore.utils.cache.EvictionCache;
import lombok.Setter;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class GtfsTripMaskProvider implements RaptorTripMaskProvider {

    private final GtfsSchedule schedule;
    private final GtfsTripMaskProvider.MaskCache cache;
    @Setter
    private Map<String, String[]> tripIds = null;

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
    public DayTripMask getDayTripMask(LocalDate date, QueryConfig queryConfig) {
        if (tripIds == null) {
            throw new IllegalStateException("Trip ids not set");
        }
        return buildTripMask(date, cache.getActiveServices(date));
    }

    private DayTripMask buildTripMask(LocalDate date, String serviceId) {
        Map<String, RouteTripMask> tripMasks = new HashMap<>();

        for (Map.Entry<String, String[]> entry : tripIds.entrySet()) {
            String routeId = entry.getKey();
            String[] tripIds = entry.getValue();
            boolean[] tripMask = new boolean[tripIds.length];
            for (int i = 0; i < tripIds.length; i++) {
                tripMask[i] = schedule.getTrips().get(tripIds[i]).getCalendar().isServiceAvailable(date);
            }

            tripMasks.put(routeId, new RouteTripMask(tripMask));
        }

        return new DayTripMask(serviceId, date, tripMasks);
    }

    /**
     * Caches for active services (= GTFS calendars) per date and raptor trip mask instances.
     */
    private class MaskCache {
        private final EvictionCache<LocalDate, String> activeServices;

        /**
         * @param cacheSize the maximum number of trip mask instances to be cached.
         * @param strategy  the cache eviction strategy.
         */
        MaskCache(int cacheSize, EvictionCache.Strategy strategy) {
            activeServices = new EvictionCache<>(Math.min(365, cacheSize * 20), strategy);
        }

        public String getActiveServices(LocalDate date) {
            return activeServices.computeIfAbsent(date, () -> getActiveServicesFromSchedule(date));
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
        }

    }

}
