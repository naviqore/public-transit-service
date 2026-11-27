package org.naviqore.raptor.router;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.time.ZoneId;
import java.util.*;

/**
 * Builds route containers that hold valid trips for a given route.
 * <p>
 * The builder ensures that:
 * <ul>
 *     <li>All trips of a route have the same stop sequence.</li>
 *     <li>Each stop time of a trip has a departure time that is temporally after the previous stop time's arrival time.</li>
 *     <li>In the final route containers, all trips are sorted according to their departure time.</li>
 *     <li>No trip of a route container overtakes another: RAPTOR takes the first trip it can catch at a stop and stays
 *     on it, so a route whose trips overtake one another is held in several route containers.</li>
 * </ul>
 */
@Slf4j
class RouteBuilder {

    private final String routeId;
    private final ZoneId zoneId;
    private final Map<Integer, String> stopSequence = new HashMap<>();
    private final Map<String, StopTime[]> trips = new HashMap<>();

    RouteBuilder(String routeId, ZoneId zoneId, List<String> stopIds) {
        this.routeId = routeId;
        this.zoneId = zoneId;
        for (int i = 0; i < stopIds.size(); i++) {
            stopSequence.put(i, stopIds.get(i));
        }
    }

    /**
     * Compares the stop times of two trips by the departure at the first stop, then by the times at the following
     * stops, so that of two trips departing together, the one that is faster comes first and is not overtaken.
     */
    private static int compareStopTimes(StopTime[] stopTimes, StopTime[] otherStopTimes) {
        int comparison = Integer.compare(stopTimes[0].departure(), otherStopTimes[0].departure());
        for (int position = 1; comparison == 0 && position < stopTimes.length; position++) {
            comparison = Integer.compare(stopTimes[position].arrival(), otherStopTimes[position].arrival());
            if (comparison == 0) {
                comparison = Integer.compare(stopTimes[position].departure(), otherStopTimes[position].departure());
            }
        }

        return comparison;
    }

    /**
     * Checks if a trip overtakes an earlier trip, i.e. departs from or arrives at any stop earlier than it. Only the
     * times RAPTOR routes with count: the departures from all stops but the last and the arrivals at all stops but the
     * first.
     */
    private static boolean overtakes(StopTime[] stopTimes, StopTime[] earlierStopTimes) {
        int lastPosition = stopTimes.length - 1;
        for (int position = 0; position <= lastPosition; position++) {
            if (position < lastPosition && stopTimes[position].departure() < earlierStopTimes[position].departure()) {
                return true;
            }
            if (position > 0 && stopTimes[position].arrival() < earlierStopTimes[position].arrival()) {
                return true;
            }
        }

        return false;
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

    /**
     * Builds the route containers of the route: one, or several if trips of the route overtake one another. Each trip
     * joins the first container whose last trip it does not overtake, so the trips of a container are in the same order
     * at every stop.
     */
    List<RouteContainer> build() {
        log.debug("Validating and building route {}", routeId);
        validate();

        // sort trips by the departure time of the first stop, trips departing together by their following times, and
        // trips with the same times by id
        List<Map.Entry<String, StopTime[]>> sortedEntries = new ArrayList<>(trips.entrySet());
        sortedEntries.sort(Map.Entry.<String, StopTime[]>comparingByValue(RouteBuilder::compareStopTimes)
                .thenComparing(Map.Entry.comparingByKey()));

        // populate the trips of each container in the same order as sortedEntries, LinkedHashMap stores insertion order
        List<LinkedHashMap<String, StopTime[]>> containerTrips = new ArrayList<>();
        for (Map.Entry<String, StopTime[]> entry : sortedEntries) {
            LinkedHashMap<String, StopTime[]> sortedTrips = null;
            for (LinkedHashMap<String, StopTime[]> candidate : containerTrips) {
                if (!overtakes(entry.getValue(), candidate.lastEntry().getValue())) {
                    sortedTrips = candidate;
                    break;
                }
            }

            if (sortedTrips == null) {
                sortedTrips = new LinkedHashMap<>();
                containerTrips.add(sortedTrips);
            }

            sortedTrips.put(entry.getKey(), entry.getValue());
        }

        if (containerTrips.size() > 1) {
            log.debug("Route {} has trips overtaking one another, split into {} route containers", routeId,
                    containerTrips.size());
        }

        return containerTrips.stream()
                .map(sortedTrips -> new RouteContainer(routeId, zoneId, stopSequence, sortedTrips))
                .toList();
    }

    record RouteContainer(String id, ZoneId zoneId, Map<Integer, String> stopSequence,
                          LinkedHashMap<String, StopTime[]> trips) implements Comparable<RouteContainer> {

        /**
         * Orders route containers by the departure of their first trip, then by id, and the containers of a route by
         * the id of their first trip, which no other container of the route holds.
         */
        private static final Comparator<RouteContainer> ORDER = Comparator.comparingInt(RouteContainer::firstDeparture)
                .thenComparing(RouteContainer::id)
                .thenComparing(RouteContainer::firstTripId);

        @Override
        public int compareTo(@NonNull RouteContainer o) {
            return ORDER.compare(this, o);
        }

        private int firstDeparture() {
            return trips.firstEntry().getValue()[0].departure();
        }

        private String firstTripId() {
            return trips.firstEntry().getKey();
        }

    }
}


