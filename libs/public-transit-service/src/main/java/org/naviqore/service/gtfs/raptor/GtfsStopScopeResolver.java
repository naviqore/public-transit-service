package org.naviqore.service.gtfs.raptor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.naviqore.gtfs.schedule.model.GtfsSchedule;
import org.naviqore.service.Stop;
import org.naviqore.service.StopScope;
import org.naviqore.utils.spatial.index.KDTree;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
class GtfsStopScopeResolver {

    private final GtfsSchedule schedule;
    private final KDTree<org.naviqore.gtfs.schedule.model.Stop> spatialStopIndex;
    private final int walkSearchRadius;

    /**
     * Resolves a stop and scope into a set of unique stop IDs.
     */
    public Set<String> resolve(Stop stop, StopScope scope) {
        return switch (scope) {
            case STRICT -> Set.of(stop.getId());

            case CHILDREN -> collectChildren(stop.getId());

            case RELATED -> collectChildren(findRoot(stop.getId()).getId());

            case NEARBY -> spatialStopIndex.rangeSearch(stop.getCoordinate(), walkSearchRadius)
                    .stream()
                    .map(org.naviqore.gtfs.schedule.model.Stop::getId)
                    .collect(Collectors.toSet());
        };
    }

    /**
     * Finds the root ancestor of a stop by traversing upward through parent references with cycle detection.
     */
    private org.naviqore.gtfs.schedule.model.Stop findRoot(String stopId) {
        org.naviqore.gtfs.schedule.model.Stop current = schedule.getStops().get(stopId);
        Set<String> visited = new HashSet<>();
        visited.add(current.getId());

        while (current.getParent().isPresent()) {
            org.naviqore.gtfs.schedule.model.Stop parent = current.getParent().get();

            if (!visited.add(parent.getId())) {
                log.warn("Circular parent_station reference detected at stop '{}'", parent.getId());
                break;
            }

            current = parent;
        }

        return current;
    }

    /**
     * Collects all children stop IDs in the hierarchy starting from the given stop using breadth-first traversal.
     */
    private Set<String> collectChildren(String startStopId) {
        org.naviqore.gtfs.schedule.model.Stop start = schedule.getStops().get(startStopId);
        if (start == null) {
            return Collections.emptySet();
        }

        Set<String> result = new HashSet<>();
        Deque<org.naviqore.gtfs.schedule.model.Stop> queue = new ArrayDeque<>();
        queue.add(start);

        while (!queue.isEmpty()) {
            org.naviqore.gtfs.schedule.model.Stop current = queue.poll();

            if (result.add(current.getId())) {
                List<org.naviqore.gtfs.schedule.model.Stop> children = current.getChildren();

                if (children != null) {
                    queue.addAll(children);
                }
            }
        }

        return result;
    }
}