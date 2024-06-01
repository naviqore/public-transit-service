package ch.naviqore.service.impl;

import ch.naviqore.gtfs.schedule.model.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Splits the routes of a GTFS schedule into sub-routes. In a GTFS schedule, a route can have multiple trips with
 * different stop sequences. This class groups trips with the same stop sequence into sub-routes and assigns them the
 * parent route.
 *
 * @author munterfi
 */
@Log4j2
public class GtfsRoutePartitioner {

    private final Map<Route, Map<String, SubRoute>> subRoutes = new HashMap<>();

    public GtfsRoutePartitioner(GtfsSchedule schedule) {
        log.info("Partitioning GTFS schedule with {} routes into sub-routes", schedule.getRoutes().size());
        schedule.getRoutes().values().forEach(this::processRoute);
        log.info("Got {} sub-routes in schedule", subRoutes.values().stream().mapToInt(Map::size).sum());
    }

    private void processRoute(Route route) {
        Map<String, SubRoute> sequenceKeyToSubRoute = new HashMap<>();
        route.getTrips().forEach(trip -> {
            String key = generateStopSequenceKey(trip);
            SubRoute subRoute = sequenceKeyToSubRoute.computeIfAbsent(key,
                    s -> new SubRoute(String.format("%s_sr%d", route.getId(), sequenceKeyToSubRoute.size() + 1), route,
                            key, extractStopSequence(trip)));
            subRoute.addTrip(trip);
        });
        subRoutes.put(route, sequenceKeyToSubRoute);
        log.debug("Route {} split into {} sub-routes", route.getId(), sequenceKeyToSubRoute.size());
    }

    private String generateStopSequenceKey(Trip trip) {
        return trip.getStopTimes().stream().map(t -> t.stop().getId()).collect(Collectors.joining("-"));
    }

    private List<Stop> extractStopSequence(Trip trip) {
        List<Stop> sequence = new ArrayList<>();
        for (StopTime stopTime : trip.getStopTimes()) {
            sequence.add(stopTime.stop());
        }

        return sequence;
    }

    public List<SubRoute> getSubRoutes(Route route) {
        Map<String, SubRoute> currentSubRoutes = subRoutes.get(route);
        if (currentSubRoutes == null) {
            throw new IllegalArgumentException("Route " + route.getId() + " not found in schedule");
        }

        return new ArrayList<>(currentSubRoutes.values());
    }

    public SubRoute getSubRoute(Trip trip) {
        Map<String, SubRoute> currentSubRoutes = subRoutes.get(trip.getRoute());
        if (currentSubRoutes == null) {
            throw new IllegalArgumentException("Trip " + trip.getId() + " not found in schedule");
        }
        String key = generateStopSequenceKey(trip);

        return currentSubRoutes.get(key);
    }

    /**
     * A sub-route belongs to a route, but has a unique stop sequence.
     */
    @RequiredArgsConstructor
    @Getter
    public static class SubRoute {
        private final String id;
        private final Route route;
        @Getter(AccessLevel.NONE)
        private final String stopSequenceKey;
        private final List<Stop> stopsSequence;
        private final List<Trip> trips = new ArrayList<>();

        private void addTrip(Trip trip) {
            trips.add(trip);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            var that = (SubRoute) obj;
            return Objects.equals(this.id, that.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }

        public String toString() {
            return "SubRoute[" + "id=" + id + ", " + "route=" + route + ", " + "stopSequence=" + stopSequenceKey + ']';
        }
    }
}
