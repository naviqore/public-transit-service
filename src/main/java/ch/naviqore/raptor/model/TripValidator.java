package ch.naviqore.raptor.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Validates trips of a route.
 */
class TripValidator {
    private final Map<String, Integer> stopSequence = new HashMap<>();
    private final Map<String, StopTime[]> trips = new HashMap<>();

    TripValidator(List<String> stopIds) {
        for (int i = 0; i < stopIds.size(); i++) {
            stopSequence.put(stopIds.get(i), i);
        }
    }

    void addTrip(String tripId) {
        if (trips.containsKey(tripId)) {
            throw new IllegalArgumentException("Trip " + tripId + " already exists.");
        }
        trips.put(tripId, new StopTime[stopSequence.size()]);
    }

    void addStopTime(String tripId, String stopId, StopTime stopTime) {
        StopTime[] stopTimes = trips.get(tripId);
        if (stopTimes == null) {
            throw new IllegalArgumentException("Trip " + tripId + " does not exist.");
        }

        Integer stopIdx = stopSequence.get(stopId);
        if (stopIdx == null) {
            throw new IllegalArgumentException("Stop " + stopId + " does not exist.");
        }

        if (stopTimes[stopIdx] != null) {
            throw new IllegalArgumentException("Stop time for stop " + stopId + " already exists.");
        }

        if (stopIdx > 0) {
            StopTime previousStopTime = stopTimes[stopIdx - 1];
            if (previousStopTime != null && previousStopTime.departure() > stopTime.arrival()) {
                throw new IllegalArgumentException(
                        "Departure time at previous stop is greater than arrival time at current stop.");
            }
        }

        if (stopIdx < stopTimes.length - 1) {
            StopTime nextStopTime = stopTimes[stopIdx + 1];
            if (nextStopTime != null && stopTime.departure() > nextStopTime.arrival()) {
                throw new IllegalArgumentException(
                        "Departure time at current stop is greater than arrival time at next stop.");
            }
        }

        stopTimes[stopIdx] = stopTime;
    }

    void validate() {
        for (Map.Entry<String, StopTime[]> trip : trips.entrySet()) {
            StopTime[] stopTimes = trip.getValue();
            for (Map.Entry<String, Integer> stop : stopSequence.entrySet()) {
                if (stopTimes[stop.getValue()] == null) {
                    throw new IllegalStateException(
                            "Stop time at stop " + stop.getKey() + " on trip " + trip.getKey() + " not set.");
                }
            }
        }
    }
}


