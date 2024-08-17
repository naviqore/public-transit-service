package ch.naviqore.raptor.router;

import ch.naviqore.raptor.QueryConfig;
import ch.naviqore.utils.cache.EvictionCache;

import java.time.LocalDate;
import java.util.Map;

/**
 * Provider for stop time int arrays for a given date.
 * <p>
 * This provider uses the {@link RaptorTripMaskProvider} to create the stop time arrays for a given date. The stop time
 * arrays are then cached based on the service id of the date, allowing to handle multiple days with same service id
 * efficiently.
 */
class StopTimeProvider {

    /**
     * The cache for the stop times. Stop time arrays are mapped to service ids, because multiple dates may have the
     * same service id.
     */
    private final EvictionCache<String, int[]> stopTimeCache;

    private final RaptorData data;
    private final RaptorTripMaskProvider tripMaskProvider;

    StopTimeProvider(RaptorData data, RaptorTripMaskProvider tripMaskProvider, int cacheSize,
                     EvictionCache.Strategy cacheStrategy) {
        this.data = data;
        this.tripMaskProvider = tripMaskProvider;
        this.stopTimeCache = new EvictionCache<>(cacheSize, cacheStrategy);
    }

    /**
     * Create the stop times for a given date.
     * <p>
     * The stop time array is built based on the trip mask provided by the {@link RaptorTripMaskProvider} and is
     * structured as follows:
     * <ul>
     *     <li>0: earliest overall stop time (in seconds relative to service date)</li>
     *     <li>1: latest overall stop time (in seconds relative to service date)</li>
     *     <li>n: for each route the stop times are stored in the following order:
     *     <ul>
     *     <li>0: earliest route stop time of day(in seconds relative to service date)</li>
     *     <li>1: latest route stop time od day (in seconds relative to service date)</li>
     *     <li>n: each trip of the route stored as a sequence of 2 x number of stops on trip, in following logic:
     *     stop 1: arrival time, stop 1: departure time, stop 2 arrival time, stop 2 departure time, ...
     *
     * @param date the date for which the stop times should be created (or retrieved from cache)
     * @return the stop times for the given date.
     */
    int[] getStopTimesForDate(LocalDate date, QueryConfig queryConfig) {
        String stopTimesKey = getCacheKeyForStopTimes(date, queryConfig);
        return stopTimeCache.computeIfAbsent(stopTimesKey, () -> createStopTimesForDate(date, queryConfig));
    }

    private String getCacheKeyForStopTimes(LocalDate date, QueryConfig queryConfig) {
        String serviceId = tripMaskProvider.getServiceIdForDate(date);
        String queryConfigKey = String.format("%b-%b-%s", queryConfig.isWheelchairAccessible(),
                queryConfig.isBikeAccessible(), queryConfig.getAllowedTravelModes());
        return serviceId + "-" + queryConfigKey;
    }

    private int[] createStopTimesForDate(LocalDate date, QueryConfig queryConfig) {
        RaptorTripMaskProvider.DayTripMask mask = tripMaskProvider.getDayTripMask(date, queryConfig);

        int[] originalStopTimesArray = data.getRouteTraversal().stopTimes();
        int[] newStopTimesArray = new int[originalStopTimesArray.length];

        // set the global start and end times for the day (initially set to NO_TRIP)
        newStopTimesArray[0] = RaptorTripMaskProvider.RouteTripMask.NO_TRIP;
        newStopTimesArray[1] = RaptorTripMaskProvider.RouteTripMask.NO_TRIP;

        // set the stop times for each route
        for (Map.Entry<String, RaptorTripMaskProvider.RouteTripMask> entry : mask.tripMask().entrySet()) {
            String routeId = entry.getKey();
            RaptorTripMaskProvider.RouteTripMask tripMask = entry.getValue();
            int routeIdx = data.getLookup().routes().get(routeId);
            Route route = data.getRouteTraversal().routes()[routeIdx];
            int numStops = route.numberOfStops();
            int stopTimeIndex = route.firstStopTimeIdx();

            boolean[] booleanMask = tripMask.routeTripMask();

            int earliestRouteStopTime = RaptorTripMaskProvider.RouteTripMask.NO_TRIP;
            int latestRouteStopTime = RaptorTripMaskProvider.RouteTripMask.NO_TRIP;

            int tripOffset = 0;
            for (boolean tripActive : booleanMask) {
                for (int stopOffset = 0; stopOffset < numStops; stopOffset++) {
                    int arrivalIndex = stopTimeIndex + (tripOffset * numStops * 2) + stopOffset * 2 + 2;
                    int departureIndex = arrivalIndex + 1;
                    // arrival and departure
                    if (tripActive) {
                        newStopTimesArray[arrivalIndex] = originalStopTimesArray[arrivalIndex];
                        newStopTimesArray[departureIndex] = originalStopTimesArray[departureIndex];
                        if (earliestRouteStopTime == RaptorTripMaskProvider.RouteTripMask.NO_TRIP) {
                            earliestRouteStopTime = originalStopTimesArray[arrivalIndex];
                        }
                        latestRouteStopTime = originalStopTimesArray[departureIndex];

                    } else {
                        newStopTimesArray[arrivalIndex] = RaptorTripMaskProvider.RouteTripMask.NO_TRIP;
                        newStopTimesArray[departureIndex] = RaptorTripMaskProvider.RouteTripMask.NO_TRIP;
                    }
                }
                tripOffset++;
            }

            // set the earliest and latest stop times for the route
            newStopTimesArray[stopTimeIndex] = earliestRouteStopTime;
            newStopTimesArray[stopTimeIndex + 1] = latestRouteStopTime;

            // maybe update the global start/end times for day
            if (earliestRouteStopTime != RaptorTripMaskProvider.RouteTripMask.NO_TRIP && latestRouteStopTime != RaptorTripMaskProvider.RouteTripMask.NO_TRIP) {
                // set the global earliest stop time if not set or if the new time is earlier
                if (newStopTimesArray[0] == RaptorTripMaskProvider.RouteTripMask.NO_TRIP || earliestRouteStopTime < newStopTimesArray[0]) {
                    newStopTimesArray[0] = earliestRouteStopTime;
                }
                // set the global latest stop time if not set or if the new time is later
                if (newStopTimesArray[1] == RaptorTripMaskProvider.RouteTripMask.NO_TRIP || latestRouteStopTime > newStopTimesArray[1]) {
                    newStopTimesArray[1] = latestRouteStopTime;
                }
            }

        }

        return newStopTimesArray;
    }
}
