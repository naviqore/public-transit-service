package ch.naviqore.raptor.router;

import ch.naviqore.utils.cache.EvictionCache;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

class RaptorCache {

    // TODO: make these configurable
    private static final int STOP_MASK_CACHE_SIZE = 5;
    private static final EvictionCache.Strategy STOP_MASK_CACHE_STRATEGY = EvictionCache.Strategy.LRU;

    private final EvictionCache<String, int[]> stopTimeCache;

    private final Map<LocalDate, String> serviceIds = new HashMap<>();
    private final RaptorData data;

    RaptorCache(RaptorData data) {
        this.data = data;
        this.stopTimeCache = new EvictionCache<>(STOP_MASK_CACHE_SIZE, STOP_MASK_CACHE_STRATEGY);
    }

    int[] getStopTimesForDate(LocalDate date) {
        String serviceId = serviceIds.get(date);
        if (serviceId == null) {
            serviceId = data.getRaptorTripMaskProvider().getServiceIdForDate(date);
            serviceIds.put(date, serviceId);
        }
        return stopTimeCache.computeIfAbsent(serviceId, () -> createStopTimesForDate(date));
    }

    int[] createStopTimesForDate(LocalDate date) {
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
