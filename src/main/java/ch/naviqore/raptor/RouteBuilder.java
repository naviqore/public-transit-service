package ch.naviqore.raptor;

import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Builds route containers that hold valid trips for a given route.
 * <p>
 * The builder ensures that:
 * <ul>
 *     <li>All trips of a route have the same stop sequence.</li>
 *     <li>Each stop time of a trip has a departure time that is temporally after the previous stop time's arrival time.</li>
 *     <li>In the final route container all trips are sorted according to their departure time.</li>
 * </ul>
 */
@Log4j2
class RouteBuilder {

    private final String routeId;
    private final Map<Integer, String> stopSequence = new HashMap<>();
    private final Map<String, StopTime[]> trips = new HashMap<>();

    RouteBuilder(String routeId, List<String> stopIds) {
        this.routeId = routeId;
        for (int i = 0; i < stopIds.size(); i++) {
            stopSequence.put(i, stopIds.get(i));
        }
    }

    void addTrip(String tripId) {
        log.debug("Adding trip: id={} routeId={}", tripId, routeId);
        if (trips.containsKey(tripId)) {
            throw new IllegalArgumentException("Trip " + tripId + " already exists.");
        }
        trips.put(tripId, new StopTime[stopSequence.size()]);
    }

    void addStopTime(String tripId, int position, String stopId, StopTime stopTime) {
        log.debug("Adding stop time: tripId={}, position={}, stopId={}, stopTime={}", tripId, position, stopId,
                stopTime);

        if (position < 0 || position >= stopSequence.size()) {
            throw new IllegalArgumentException(
                    "Position " + position + " is out of bounds [0, " + stopSequence.size() + ").");
        }

        StopTime[] stopTimes = trips.get(tripId);
        if (stopTimes == null) {
            throw new IllegalArgumentException("Trip " + tripId + " does not exist.");
        }

        if (!stopSequence.get(position).equals(stopId)) {
            throw new IllegalArgumentException("Stop " + stopId + " does not match stop " + stopSequence.get(
                    position) + " at position " + position + ".");
        }

        if (stopTimes[position] != null) {
            throw new IllegalArgumentException("Stop time for stop " + stopId + " already exists.");
        }

        if (position > 0) {
            StopTime previousStopTime = stopTimes[position - 1];
            if (previousStopTime != null && previousStopTime.departure() > stopTime.arrival()) {
                throw new IllegalArgumentException(
                        "Departure time at previous stop is greater than arrival time at current stop.");
            }
        }

        if (position < stopTimes.length - 1) {
            StopTime nextStopTime = stopTimes[position + 1];
            if (nextStopTime != null && stopTime.departure() > nextStopTime.arrival()) {
                throw new IllegalArgumentException(
                        "Departure time at current stop is greater than arrival time at next stop.");
            }
        }

        stopTimes[position] = stopTime;
    }

    private void validate() {
        for (Map.Entry<String, StopTime[]> trip : trips.entrySet()) {
            StopTime[] stopTimes = trip.getValue();
            for (Map.Entry<Integer, String> stop : stopSequence.entrySet()) {
                // ensure all stop times are set and therefore all trips must have the same stops
                if (stopTimes[stop.getKey()] == null) {
                    throw new IllegalStateException(
                            "Stop time at stop " + stop.getKey() + " on trip " + trip.getKey() + " not set.");
                }
            }
        }
    }

    RouteContainer build() {
        log.debug("Validating and building route {}", routeId);
        validate();

        // sort trips by the departure time of the first stop
        List<Map.Entry<String, StopTime[]>> sortedEntries = new ArrayList<>(trips.entrySet());
        sortedEntries.sort(Comparator.comparingInt(entry -> entry.getValue()[0].departure()));

        // populate sortedTrips in the same order as sortedEntries, LinkedHashMap stores insertion order
        LinkedHashMap<String, StopTime[]> sortedTrips = new LinkedHashMap<>(sortedEntries.size());
        for (Map.Entry<String, StopTime[]> entry : sortedEntries) {
            sortedTrips.put(entry.getKey(), entry.getValue());
        }

        return new RouteContainer(routeId, stopSequence, sortedTrips);
    }

    record RouteContainer(String id, Map<Integer, String> stopSequence,
                          LinkedHashMap<String, StopTime[]> trips) implements Comparable<RouteContainer> {

        @Override
        public int compareTo(@NotNull RouteContainer o) {
            StopTime thisFirstStopTime = this.trips.values().iterator().next()[0];
            StopTime otherFirstStopTime = o.trips.values().iterator().next()[0];
            return Integer.compare(thisFirstStopTime.departure(), otherFirstStopTime.departure());
        }

    }
}


