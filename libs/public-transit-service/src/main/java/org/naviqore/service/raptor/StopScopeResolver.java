package org.naviqore.service.raptor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.naviqore.service.Stop;
import org.naviqore.service.StopScope;
import org.naviqore.timetable.model.Timetable;
import org.naviqore.utils.spatial.index.KDTree;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
class StopScopeResolver {

    private final Timetable schedule;
    private final KDTree<org.naviqore.timetable.model.Stop> spatialStopIndex;
    private final int walkSearchRadius;

    /**
     * Resolves a stop and scope into a set of unique stop IDs.
     */
    public Set<String> resolve(Stop stop, StopScope scope) {
        return switch (scope) {
            case STRICT -> Set.of(stop.getId());

            case CHILDREN -> collectSubtree(stop.getId());

            case RELATED -> collectSubtree(findRoot(stop.getId()).getId());

            case NEARBY -> spatialStopIndex.rangeSearch(stop.getCoordinate(), walkSearchRadius)
                    .stream()
                    .map(org.naviqore.timetable.model.Stop::getId)
                    .collect(Collectors.toSet());
        };
    }

    /**
     * Finds the root ancestor of a stop by traversing upward through parent references with cycle detection.
     */
    private org.naviqore.timetable.model.Stop findRoot(String stopId) {
        org.naviqore.timetable.model.Stop current = schedule.getStops().get(stopId);
        Set<String> visited = new HashSet<>();
        visited.add(current.getId());

        while (current.getParent().isPresent()) {
            org.naviqore.timetable.model.Stop parent = current.getParent().get();

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
        org.naviqore.timetable.model.Stop start = schedule.getStops().get(startStopId);
        if (start == null) {
            return Collections.emptySet();
        }

        Set<String> result = new HashSet<>();
        Deque<org.naviqore.timetable.model.Stop> queue = new ArrayDeque<>();
        queue.add(start);

        while (!queue.isEmpty()) {
            org.naviqore.timetable.model.Stop current = queue.poll();

            if (result.add(current.getId())) {
                List<? extends org.naviqore.timetable.model.Stop> children = current.getChildren();

                if (children != null) {
                    queue.addAll(children);
                }
            }
        }

        return result;
    }
}