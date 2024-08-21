package ch.naviqore.service.gtfs.raptor.convert;

import ch.naviqore.gtfs.schedule.model.GtfsSchedule;
import ch.naviqore.gtfs.schedule.model.Route;
import ch.naviqore.gtfs.schedule.model.Trip;
import ch.naviqore.gtfs.schedule.type.AccessibilityInformation;
import ch.naviqore.gtfs.schedule.type.BikeInformation;
import ch.naviqore.gtfs.schedule.type.DefaultRouteType;
import ch.naviqore.gtfs.schedule.type.RouteTypeMapper;
import ch.naviqore.raptor.QueryConfig;
import ch.naviqore.raptor.TravelMode;
import ch.naviqore.raptor.router.RaptorTripMaskProvider;
import ch.naviqore.service.config.ServiceConfig;
import ch.naviqore.utils.cache.EvictionCache;
import lombok.Setter;

import java.time.LocalDate;
import java.util.EnumSet;
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

    private static EnumSet<DefaultRouteType> mapToRouteTypes(EnumSet<TravelMode> travelModes) {
        if (travelModes.isEmpty()) {
            return EnumSet.allOf(DefaultRouteType.class);
        }
        EnumSet<DefaultRouteType> routeTypes = EnumSet.noneOf(DefaultRouteType.class);
        for (TravelMode travelMode : travelModes) {
            routeTypes.addAll(map(travelMode));
        }
        return routeTypes;
    }

    private static EnumSet<DefaultRouteType> map(TravelMode travelMode) {
        if (travelMode.equals(TravelMode.BUS)) {
            return EnumSet.of(DefaultRouteType.BUS, DefaultRouteType.TROLLEYBUS);
        } else if (travelMode.equals(TravelMode.TRAM)) {
            return EnumSet.of(DefaultRouteType.TRAM, DefaultRouteType.CABLE_TRAM);
        } else if (travelMode.equals(TravelMode.RAIL)) {
            return EnumSet.of(DefaultRouteType.RAIL, DefaultRouteType.MONORAIL);
        } else if (travelMode.equals(TravelMode.SHIP)) {
            return EnumSet.of(DefaultRouteType.FERRY);
        } else if (travelMode.equals(TravelMode.SUBWAY)) {
            return EnumSet.of(DefaultRouteType.SUBWAY);
        } else if (travelMode.equals(TravelMode.AERIAL_LIFT)) {
            return EnumSet.of(DefaultRouteType.AERIAL_LIFT);
        } else if (travelMode.equals(TravelMode.FUNICULAR)) {
            return EnumSet.of(DefaultRouteType.FUNICULAR);
        } else {
            // should never happen
            throw new IllegalArgumentException("Travel mode not supported");
        }
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
        Map<String, RouteTripMask> tripMasks = new HashMap<>();

        // the travel mode filter is only active if not all travel modes are allowed (no filtering is required when all
        // travel modes are allowed)
        boolean hasTravelModeFilter = queryConfig.getAllowedTravelModes().size() != TravelMode.values().length;
        EnumSet<DefaultRouteType> allowedRouteTypes = mapToRouteTypes(queryConfig.getAllowedTravelModes());

        for (Map.Entry<String, String[]> entry : tripIds.entrySet()) {
            String routeId = entry.getKey();
            String[] tripIds = entry.getValue();

            boolean[] tripMask = new boolean[tripIds.length];

            if (hasTravelModeFilter) {
                Trip firstTripOfRoute = schedule.getTrips().get(tripIds[0]);
                Route route = firstTripOfRoute.getRoute();
                DefaultRouteType routeType = RouteTypeMapper.map(route.getType());
                if (!allowedRouteTypes.contains(routeType)) {
                    // no need for further checks if route type is not allowed
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
