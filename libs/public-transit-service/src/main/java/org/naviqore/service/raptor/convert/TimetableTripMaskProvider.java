package org.naviqore.service.raptor.convert;

import org.jspecify.annotations.Nullable;
import org.naviqore.raptor.QueryConfig;
import org.naviqore.raptor.router.RaptorTripMaskProvider;
import org.naviqore.service.config.ServiceConfig;
import org.naviqore.timetable.model.Calendar;
import org.naviqore.timetable.model.Timetable;
import org.naviqore.timetable.model.Trip;
import org.naviqore.timetable.type.AccessibilityInformation;
import org.naviqore.timetable.type.BikeInformation;
import org.naviqore.timetable.type.TravelMode;
import org.naviqore.utils.cache.EvictionCache;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Provides the trip masks of a timetable to RAPTOR.
 * <p>
 * When RAPTOR sets its trips, each trip is resolved once to its calendar, travel mode, wheelchair accessibility and
 * bike information. A day's mask then asks each calendar once whether it runs on that day, and each trip reads the
 * answer of its calendar.
 */
public class TimetableTripMaskProvider implements RaptorTripMaskProvider {

    private static final byte WHEELCHAIR_ACCESSIBLE = 1;
    private static final byte BIKES_ALLOWED = 2;

    private final Timetable schedule;
    private final TimetableTripMaskProvider.MaskCache cache;
    private Calendar[] calendars = new Calendar[0];
    private RouteTrips @Nullable [] routes = null;

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

    private static byte flagsOf(Trip trip) {
        byte flags = 0;
        if (trip.getWheelchairAccessible() == AccessibilityInformation.ACCESSIBLE) {
            flags |= WHEELCHAIR_ACCESSIBLE;
        }
        if (trip.getBikesAllowed() == BikeInformation.ALLOWED) {
            flags |= BIKES_ALLOWED;
        }
        return flags;
    }

    public void clearCache() {
        cache.clear();
    }

    @Override
    public void setTripIds(Map<String, String[]> routeTripIds) {
        boolean hasTripFlags = schedule.hasTripAccessibilityInformation() || schedule.hasTripBikeInformation();
        Map<String, Integer> calendarIndices = new HashMap<>();
        List<Calendar> calendars = new ArrayList<>();
        List<RouteTrips> routes = new ArrayList<>(routeTripIds.size());

        for (Map.Entry<String, String[]> entry : routeTripIds.entrySet()) {
            String[] tripIds = entry.getValue();
            TravelMode travelMode = null;
            int[] tripCalendars = new int[tripIds.length];
            byte[] tripFlags = hasTripFlags ? new byte[tripIds.length] : null;

            for (int i = 0; i < tripIds.length; i++) {
                Trip trip = schedule.getTrips().get(tripIds[i]);
                if (i == 0) {
                    travelMode = trip.getRoute().getTravelMode();
                }

                Calendar calendar = trip.getCalendar();
                Integer calendarIndex = calendarIndices.get(calendar.getId());
                if (calendarIndex == null) {
                    calendarIndex = calendars.size();
                    calendarIndices.put(calendar.getId(), calendarIndex);
                    calendars.add(calendar);
                }
                tripCalendars[i] = calendarIndex;

                if (tripFlags != null) {
                    tripFlags[i] = flagsOf(trip);
                }
            }

            routes.add(new RouteTrips(entry.getKey(), travelMode, tripCalendars, tripFlags));
        }

        this.calendars = calendars.toArray(Calendar[]::new);
        this.routes = routes.toArray(RouteTrips[]::new);
    }

    @Override
    public String getServiceIdForDate(LocalDate date) {
        return cache.getActiveServices(date);
    }

    @Override
    public DayTripMask getDayTripMask(LocalDate date, QueryConfig queryConfig) {
        RouteTrips[] routes = this.routes;
        if (routes == null) {
            throw new IllegalStateException("Trip ids not set");
        }
        return buildTripMask(routes, date, cache.getActiveServices(date), queryConfig);
    }

    private DayTripMask buildTripMask(RouteTrips[] routes, LocalDate date, String serviceId, QueryConfig queryConfig) {
        validateQueryConfig(queryConfig);

        boolean hasTravelModeFilter = queryConfig.needsTravelModeFiltering();
        EnumSet<TravelMode> allowedTravelModes = mapToTravelModes(queryConfig.getAllowedTravelModes());
        int requiredFlags = 0;
        if (queryConfig.isWheelchairAccessible()) {
            requiredFlags |= WHEELCHAIR_ACCESSIBLE;
        }
        if (queryConfig.isBikeAccessible()) {
            requiredFlags |= BIKES_ALLOWED;
        }

        // ask each calendar once, then each trip reads the answer of its calendar
        boolean[] running = new boolean[calendars.length];
        for (int i = 0; i < calendars.length; i++) {
            running[i] = calendars[i].isServiceAvailable(date);
        }

        Map<String, RouteTripMask> tripMasks = HashMap.newHashMap(routes.length);
        for (RouteTrips route : routes) {
            int[] tripCalendars = route.calendars();
            boolean[] tripMask = new boolean[tripCalendars.length];

            // no need for further checks if travel mode is not allowed
            if (!hasTravelModeFilter || allowedTravelModes.contains(route.travelMode())) {
                for (int i = 0; i < tripMask.length; i++) {
                    tripMask[i] = running[tripCalendars[i]] && route.hasFlags(i, requiredFlags);
                }
            }

            tripMasks.put(route.id(), new RouteTripMask(tripMask));
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
     * The trips of a RAPTOR route in RAPTOR's order: the index of each trip's calendar and, if the timetable has
     * wheelchair or bike information, each trip's flags. The travel mode is the one of the route's first trip.
     */
    private record RouteTrips(String id, @Nullable TravelMode travelMode, int[] calendars, byte @Nullable [] flags) {

        boolean hasFlags(int trip, int requiredFlags) {
            return requiredFlags == 0 || flags != null && (flags[trip] & requiredFlags) == requiredFlags;
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
