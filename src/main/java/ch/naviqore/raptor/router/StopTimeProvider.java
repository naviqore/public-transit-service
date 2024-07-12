package ch.naviqore.raptor.router;

import ch.naviqore.utils.cache.EvictionCache;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * Provider for stop time int arrays for a given date.
 * <p>
 * This provider uses the {@link RaptorTripMaskProvider} to create the stop time arrays for a given date. The stop time
 * arrays are then cached based on the service id of the date, allowing to handle multiple days with same service id
 * efficiently.
 */
class StopTimeProvider {

    // TODO: make these configurable
    private static final int STOP_MASK_CACHE_SIZE = 5;
    private static final EvictionCache.Strategy STOP_MASK_CACHE_STRATEGY = EvictionCache.Strategy.LRU;

    /**
     * The cache for the stop times. Stop time arrays are mapped to service ids, because multiple dates may have the
     * same service id.
     */
    private final EvictionCache<String, int[]> stopTimeCache;

    private final Map<LocalDate, String> serviceIds = new HashMap<>();
    private final RaptorData data;

    StopTimeProvider(RaptorData data) {
        this.data = data;
        this.stopTimeCache = new EvictionCache<>(STOP_MASK_CACHE_SIZE, STOP_MASK_CACHE_STRATEGY);
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
    int[] getStopTimesForDate(LocalDate date) {
        String serviceId = serviceIds.get(date);
        if (serviceId == null) {
            serviceId = data.getRaptorTripMaskProvider().getServiceIdForDate(date);
            serviceIds.put(date, serviceId);
        }
        return stopTimeCache.computeIfAbsent(serviceId, () -> createStopTimesForDate(date));
    }

    private int[] createStopTimesForDate(LocalDate date) {
        RaptorDayMask mask = data.getRaptorTripMaskProvider().getTripMask(date);

        int[] originalStopTimesArray = data.getRouteTraversal().stopTimes();
        int[] newStopTimesArray = new int[originalStopTimesArray.length];

        // set the global start/end times
        newStopTimesArray[0] = mask.earliestTripTime();
        newStopTimesArray[1] = mask.latestTripTime();

        // set the stop times for each route
        for (Map.Entry<String, TripMask> entry : mask.tripMask().entrySet()) {
            String routeId = entry.getKey();
            TripMask tripMask = entry.getValue();
            int routeIdx = data.getLookup().routes().get(routeId);
            Route route = data.getRouteTraversal().routes()[routeIdx];
            int numStops = route.numberOfStops();
            int stopTimeIndex = route.firstStopTimeIdx();

            newStopTimesArray[stopTimeIndex] = tripMask.earliestTripTime();
            newStopTimesArray[stopTimeIndex + 1] = tripMask.latestTripTime();

            boolean[] booleanMask = tripMask.tripMask();

            int tripOffset = 0;
            for (boolean tripActive : booleanMask) {
                for (int stopOffset = 0; stopOffset < numStops; stopOffset++) {
                    int arrivalIndex = stopTimeIndex + (tripOffset * numStops * 2) + stopOffset * 2 + 2;
                    int departureIndex = arrivalIndex + 1;
                    // arrival and departure
                    if (tripActive) {
                        newStopTimesArray[arrivalIndex] = originalStopTimesArray[arrivalIndex];
                        newStopTimesArray[departureIndex] = originalStopTimesArray[departureIndex];
                    } else {
                        newStopTimesArray[arrivalIndex] = TripMask.NO_TRIP;
                        newStopTimesArray[departureIndex] = TripMask.NO_TRIP;
                    }
                }
                tripOffset++;
            }
        }

        return newStopTimesArray;
    }
}
