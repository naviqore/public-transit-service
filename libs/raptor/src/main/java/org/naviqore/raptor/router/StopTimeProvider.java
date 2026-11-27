package org.naviqore.raptor.router;

import org.naviqore.raptor.QueryConfig;
import org.naviqore.utils.cache.EvictionCache;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Map;

/**
 * Provider of the stop times of the trips running on a date.
 * <p>
 * This provider uses the {@link RaptorTripMaskProvider} to find the trips running on a date and keeps their stop times,
 * see {@link DayStopTimes}. All stop times are converted to UTC seconds relative to the service date, and the stop
 * times of a date are cached. Since routes may span multiple time zones and daylight saving time (DST) can change
 * offsets, the cache key includes a DST fingerprint: a deterministic string of all unique route zone offsets at local
 * noon. This ensures that UTC-adjusted stop times remain correct even when DST changes occur or multiple zones are
 * involved.
 */
class StopTimeProvider {

    /**
     * The cache for the stop times. The stop times of a date are mapped to its service id, because multiple dates may
     * have the same service id.
     */
    private final EvictionCache<String, DayStopTimes> stopTimeCache;

    private final RaptorData data;
    private final RaptorTripMaskProvider tripMaskProvider;

    /**
     * The time zones of all routes, each once: their offsets on a date are part of the date's cache key, which every
     * query computes for each day it scans.
     */
    private final ZoneId[] routeZones;

    StopTimeProvider(RaptorData data, RaptorTripMaskProvider tripMaskProvider, int cacheSize,
                     EvictionCache.Strategy cacheStrategy) {
        this.data = data;
        this.tripMaskProvider = tripMaskProvider;
        this.stopTimeCache = new EvictionCache<>(cacheSize, cacheStrategy);
        this.routeZones = Arrays.stream(data.getRouteTraversal().routes())
                .map(Route::zoneId)
                .distinct()
                .toArray(ZoneId[]::new);
    }

    /**
     * Gets the stop times of the trips running on a date, built from the trip mask the {@link RaptorTripMaskProvider}
     * provides for it, or retrieved from the cache.
     *
     * @param date        the date.
     * @param queryConfig the query config, which the trip mask depends on.
     * @return the stop times of the date.
     */
    DayStopTimes getStopTimesForDate(LocalDate date, QueryConfig queryConfig) {
        String stopTimesKey = getCacheKeyForStopTimes(date, queryConfig);
        return stopTimeCache.computeIfAbsent(stopTimesKey, () -> createStopTimesForDate(date, queryConfig));
    }

    /**
     * Computes a cache key for stop times that is DST-safe for multiple zones.
     */
    private String getCacheKeyForStopTimes(LocalDate date, QueryConfig queryConfig) {
        String serviceId = tripMaskProvider.getServiceIdForDate(date);

        // offsets of all unique route zones at local noon
        String dstFingerprint = Arrays.stream(routeZones)
                .map(zoneId -> DateTimeConverter.getLocalToUtcOffset(date, zoneId))
                .distinct()
                .sorted()
                .map(String::valueOf)
                .reduce((a, b) -> a + "," + b)
                .orElse("");

        // compose key
        return serviceId + "|" + dstFingerprint + "|" + queryConfig.isWheelchairAccessible() + "|" + queryConfig.isBikeAccessible() + "|" + queryConfig.getAllowedTravelModes();
    }

    /**
     * Creates the stop times of the trips running on a date: the trips are counted route by route first, which places
     * every route's in the day, and then copied there.
     */
    private DayStopTimes createStopTimesForDate(LocalDate date, QueryConfig queryConfig) {
        RaptorTripMaskProvider.DayTripMask mask = tripMaskProvider.getDayTripMask(date, queryConfig);
        Route[] routes = data.getRouteTraversal().routes();

        int[] runningTrips = new int[routes.length];
        forEachRouteTripMask(mask, (routeIdx, tripMask, firstTripOfRoute) -> {
            for (int tripOffset = 0; tripOffset < routes[routeIdx].numberOfTrips(); tripOffset++) {
                if (tripMask[firstTripOfRoute + tripOffset]) {
                    runningTrips[routeIdx]++;
                }
            }
        });

        int[] routeTrips = new int[routes.length + 1];
        int[] routeStopTimes = new int[routes.length];
        int numberOfStopTimes = 0;
        for (int routeIdx = 0; routeIdx < routes.length; routeIdx++) {
            routeTrips[routeIdx + 1] = routeTrips[routeIdx] + runningTrips[routeIdx];
            routeStopTimes[routeIdx] = numberOfStopTimes;
            numberOfStopTimes += runningTrips[routeIdx] * 2 * routes[routeIdx].numberOfStops();
        }

        int[] stopTimes = new int[numberOfStopTimes];
        int[] tripPositions = new int[routeTrips[routes.length]];
        forEachRouteTripMask(mask, (routeIdx, tripMask, firstTripOfRoute) -> {
            Route route = routes[routeIdx];
            int[] timetableStopTimes = data.getRouteTraversal().stopTimes();
            int tripStopTimes = 2 * route.numberOfStops();
            int utcOffset = DateTimeConverter.getLocalToUtcOffset(date, route.zoneId());
            int trip = routeTrips[routeIdx];
            int stopTimeIdx = routeStopTimes[routeIdx];
            for (int tripOffset = 0; tripOffset < route.numberOfTrips(); tripOffset++) {
                if (!tripMask[firstTripOfRoute + tripOffset]) {
                    continue;
                }
                // the stop times of all trips follow the earliest and latest stop time of their route
                int timetableStopTimeIdx = route.firstStopTimeIdx() + 2 + tripOffset * tripStopTimes;
                for (int i = 0; i < tripStopTimes; i++) {
                    stopTimes[stopTimeIdx + i] = timetableStopTimes[timetableStopTimeIdx + i] + utcOffset;
                }
                tripPositions[trip++] = tripOffset;
                stopTimeIdx += tripStopTimes;
            }
        });

        // the trips of a route keep their order at every stop, so its first trip arrives first and its last leaves last
        int[] routeEarliest = new int[routes.length];
        int[] routeLatest = new int[routes.length];
        int earliest = RaptorTripMaskProvider.RouteTripMask.NO_TRIP;
        int latest = RaptorTripMaskProvider.RouteTripMask.NO_TRIP;
        for (int routeIdx = 0; routeIdx < routes.length; routeIdx++) {
            if (runningTrips[routeIdx] == 0) {
                routeEarliest[routeIdx] = RaptorTripMaskProvider.RouteTripMask.NO_TRIP;
                routeLatest[routeIdx] = RaptorTripMaskProvider.RouteTripMask.NO_TRIP;
                continue;
            }
            int routeStopTimesEnd = routeStopTimes[routeIdx] + runningTrips[routeIdx] * 2 * routes[routeIdx].numberOfStops();
            routeEarliest[routeIdx] = stopTimes[routeStopTimes[routeIdx]];
            routeLatest[routeIdx] = stopTimes[routeStopTimesEnd - 1];
            if (earliest == RaptorTripMaskProvider.RouteTripMask.NO_TRIP || routeEarliest[routeIdx] < earliest) {
                earliest = routeEarliest[routeIdx];
            }
            if (latest == RaptorTripMaskProvider.RouteTripMask.NO_TRIP || routeLatest[routeIdx] > latest) {
                latest = routeLatest[routeIdx];
            }
        }

        return new DayStopTimes(stopTimes, routeStopTimes, routeTrips, tripPositions, routeEarliest, routeLatest,
                earliest, latest);
    }

    /**
     * Calls an action for each RAPTOR route with the trip mask of the route it belongs to: the trips of a route are
     * those of its RAPTOR routes, one RAPTOR route after the other.
     */
    private void forEachRouteTripMask(RaptorTripMaskProvider.DayTripMask mask, RouteTripMaskAction action) {
        for (Map.Entry<String, RaptorTripMaskProvider.RouteTripMask> entry : mask.tripMask().entrySet()) {
            boolean[] tripMask = entry.getValue().routeTripMask();
            int firstTripOfRoute = 0;
            for (int routeIdx : data.getLookup().routes().get(entry.getKey())) {
                action.accept(routeIdx, tripMask, firstTripOfRoute);
                firstTripOfRoute += data.getRouteTraversal().routes()[routeIdx].numberOfTrips();
            }
        }
    }

    @FunctionalInterface
    private interface RouteTripMaskAction {

        /**
         * @param routeIdx         the index of the RAPTOR route.
         * @param tripMask         the trip mask of the route the RAPTOR route belongs to.
         * @param firstTripOfRoute the position of the first trip of the RAPTOR route in the trip mask.
         */
        void accept(int routeIdx, boolean[] tripMask, int firstTripOfRoute);
    }
}
