package org.naviqore.service.raptor.convert;

import lombok.Setter;
import org.naviqore.raptor.QueryConfig;
import org.naviqore.raptor.router.RaptorTripMaskProvider;
import org.naviqore.service.config.ServiceConfig;
import org.naviqore.timetable.model.Route;
import org.naviqore.timetable.model.Timetable;
import org.naviqore.timetable.model.Trip;
import org.naviqore.timetable.type.AccessibilityInformation;
import org.naviqore.timetable.type.BikeInformation;
import org.naviqore.timetable.type.TravelMode;
import org.naviqore.utils.cache.EvictionCache;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class TimetableTripMaskProvider implements RaptorTripMaskProvider {

    private final Timetable schedule;
    private final TimetableTripMaskProvider.MaskCache cache;
    @Setter
    private Map<String, String[]> tripIds = null;

    public TimetableTripMaskProvider(Timetable schedule) {
        this(schedule, ServiceConfig.DEFAULT_CACHE_SIZE,
                EvictionCache.Strategy.valueOf(ServiceConfig.DEFAULT_CACHE_EVICTION_STRATEGY.name()));
    }

    public TimetableTripMaskProvider(Timetable schedule, int cacheSize, EvictionCache.Strategy strategy) {
        this.schedule = schedule;
        this.cache = new TimetableTripMaskProvider.MaskCache(cacheSize, strategy);
    }

    private static EnumSet<TravelMode> mapToTravelModes(EnumSet<org.naviqore.raptor.TravelMode> travelModes) {
        if (travelModes.isEmpty()) {
            return EnumSet.allOf(TravelMode.class);
        }
        EnumSet<TravelMode> timetableTravelModes = EnumSet.noneOf(TravelMode.class);
        for (org.naviqore.raptor.TravelMode travelMode : travelModes) {
            timetableTravelModes.add(TravelMode.valueOf(travelMode.name()));
        }
        return timetableTravelModes;
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
        return buildTripMask(date, cache.getActiveServices(date), queryConfig);
    }

    private DayTripMask buildTripMask(LocalDate date, String serviceId, QueryConfig queryConfig) {
        validateQueryConfig(queryConfig);
        Map<String, RouteTripMask> tripMasks = new HashMap<>();

        boolean hasTravelModeFilter = queryConfig.needsTravelModeFiltering();
        EnumSet<TravelMode> allowedTravelModes = mapToTravelModes(queryConfig.getAllowedTravelModes());

        for (Map.Entry<String, String[]> entry : tripIds.entrySet()) {
            String routeId = entry.getKey();
            String[] tripIds = entry.getValue();

            boolean[] tripMask = new boolean[tripIds.length];

            if (hasTravelModeFilter) {
                Trip firstTripOfRoute = schedule.getTrips().get(tripIds[0]);
                Route route = firstTripOfRoute.getRoute();
                if (!allowedTravelModes.contains(route.getTravelMode())) {
                    // no need for further checks if travel mode is not allowed
                    tripMasks.put(routeId, new RouteTripMask(tripMask));
                    continue;
                }
            }

            for (int i = 0; i < tripIds.length; i++) {
                Trip trip = schedule.getTrips().get(tripIds[i]);
                if (!trip.getCalendar().isServiceAvailable(date)) {
                    tripMask[i] = false;
                    continue;
                }

                if (queryConfig.isWheelchairAccessible()) {
                    if (trip.getWheelchairAccessible() != AccessibilityInformation.ACCESSIBLE) {
                        tripMask[i] = false;
                        continue;
                    }
                }

                if (queryConfig.isBikeAccessible()) {
                    if (trip.getBikesAllowed() != BikeInformation.ALLOWED) {
                        tripMask[i] = false;
                        continue;
                    }
                }

                tripMask[i] = true;
            }

            tripMasks.put(routeId, new RouteTripMask(tripMask));
        }

        return new DayTripMask(serviceId, date, tripMasks);
    }

    private void validateQueryConfig(QueryConfig queryConfig) {
        if (queryConfig.isWheelchairAccessible() && !schedule.hasTripAccessibilityInformation()) {
            throw new IllegalArgumentException("Timetable does not contain wheelchair accessibility information");
        }
        if (queryConfig.isBikeAccessible() && !schedule.hasTripBikeInformation()) {
            throw new IllegalArgumentException("Timetable does not contain bike accessibility information");
        }
    }

    /**
     * Caches for active services (= timetable calendars) per date and raptor trip mask instances.
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

        // get all active calendars from the timetable for given date, serves as key for caching raptor instances
        private String getActiveServicesFromSchedule(LocalDate date) {
            return schedule.getCalendars()
                    .values()
                    .stream()
                    .filter(calendar -> calendar.isServiceAvailable(date))
                    .map(org.naviqore.timetable.model.Calendar::getId)
                    .collect(Collectors.joining(","));
        }

        // clear the cache, needs to be called when the timetable changes
        private void clear() {
            activeServices.clear();
        }

    }

}
