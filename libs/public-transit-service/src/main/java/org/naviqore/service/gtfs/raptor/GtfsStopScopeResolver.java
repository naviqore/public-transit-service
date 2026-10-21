package org.naviqore.service.gtfs.raptor;

import lombok.extern.slf4j.Slf4j;
import org.naviqore.gtfs.schedule.model.GtfsSchedule;
import org.naviqore.service.Stop;
import org.naviqore.service.StopScope;
import org.naviqore.service.spi.TransitSchedule;
import org.naviqore.utils.spatial.index.KDTree;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
class GtfsStopScopeResolver {

    private final TransitSchedule schedule;
    private final int walkSearchRadius;

    GtfsStopScopeResolver(GtfsSchedule schedule, KDTree<org.naviqore.gtfs.schedule.model.Stop> spatialStopIndex,
                          int walkSearchRadius) {
        this(new GtfsTransitSchedule(schedule, spatialStopIndex), walkSearchRadius);
    }

    GtfsStopScopeResolver(TransitSchedule schedule, int walkSearchRadius) {
        this.schedule = schedule;
        this.walkSearchRadius = walkSearchRadius;
    }

    /**
     * Resolves a stop and scope into a set of unique stop IDs.
     */
    public Set<String> resolve(Stop stop, StopScope scope) {
        return switch (scope) {
            case STRICT -> Set.of(stop.getId());

            case CHILDREN -> collectSubtree(stop.getId());

            case RELATED -> collectSubtree(findRoot(stop.getId()).getId());

            case NEARBY -> schedule.getStopsWithinRadius(stop.getCoordinate(), walkSearchRadius)
                    .stream()
                    .map(Stop::getId)
                    .collect(Collectors.toSet());
        };
    }

    /**
     * Finds the root ancestor of a stop by traversing upward through parent references with cycle detection.
     */
    private Stop findRoot(String stopId) {
        Stop current = schedule.getStop(stopId);
        Set<String> visited = new HashSet<>();
        visited.add(current.getId());

        while (schedule.getParent(current.getId()).isPresent()) {
            Stop parent = schedule.getParent(current.getId()).get();

            if (!visited.add(parent.getId())) {
                log.warn("Circular parent_station reference detected at stop '{}'", parent.getId());
                break;
            }

            current = parent;
        }

        return current;
    }

    /**
     * Collects the stop ID and all its descendant stop IDs in the hierarchy starting from the given stop using
     * breadth-first traversal.
     */
    private Set<String> collectSubtree(String startStopId) {
        Stop start = schedule.getStop(startStopId);
        if (start == null) {
            return Collections.emptySet();
        }

        Set<String> result = new HashSet<>();
        Deque<Stop> queue = new ArrayDeque<>();
        queue.add(start);

        while (!queue.isEmpty()) {
            Stop current = queue.poll();

            if (result.add(current.getId())) {
                queue.addAll(schedule.getChildren(current.getId()));
            }
        }

        return result;
    }
}