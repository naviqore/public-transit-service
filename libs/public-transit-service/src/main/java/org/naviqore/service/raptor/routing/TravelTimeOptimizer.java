package org.naviqore.service.raptor.routing;

import lombok.extern.slf4j.Slf4j;
import org.naviqore.raptor.Connection;
import org.naviqore.raptor.QueryConfig;
import org.naviqore.raptor.RaptorAlgorithm;
import org.naviqore.raptor.TimeType;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Shortens the travel time of the connections of a query at a time. For each number of trips, a query of RAPTOR finds
 * the earliest arrival (the latest departure, for an arrival query), but takes the first trip it can catch, so a
 * connection may leave earlier than its arrival needs (arrive later than its departure allows). A query in the other
 * direction from the connection's arrival, with as many trips at most, finds the latest departure for it (from its
 * departure, the earliest arrival), which then replaces the connection if it is later (earlier).
 * <p>
 * Both queries run in the direction of the search: the query in the other direction starts at the target stops, at the
 * time the connection reaches the target less (plus) the walk from each, and ends at the source stops. A source stop's
 * time becomes a walk relative to the earliest (latest) source time, which ranks the departures from the source stops
 * as the original query does. The travel time of the query is bounded by the connection's, so that it only routes what
 * can improve on it.
 */
@Slf4j
final class TravelTimeOptimizer {

    private final RaptorAlgorithm raptor;
    private final Map<String, OffsetDateTime> sourceStops;
    private final Map<String, Integer> targetStops;
    private final TimeType timeType;
    private final QueryConfig config;
    private final boolean forward;

    // the source stops as the target of the query in the other direction: the time at each relative to the earliest
    // (latest) source time, in seconds
    private final Map<String, Integer> sourceStopsAsTarget = new HashMap<>();

    /**
     * @param raptor      the router.
     * @param sourceStops the source stops of the query, and the time at each.
     * @param targetStops the target stops of the query, and the walk from each to the target (from the origin to
     *                    each).
     * @param timeType    the direction of the query.
     * @param config      the configuration of the query.
     */
    TravelTimeOptimizer(RaptorAlgorithm raptor, Map<String, OffsetDateTime> sourceStops,
                        Map<String, Integer> targetStops, TimeType timeType, QueryConfig config) {
        this.raptor = raptor;
        this.sourceStops = sourceStops;
        this.targetStops = targetStops;
        this.timeType = timeType;
        this.config = config;
        this.forward = timeType == TimeType.DEPARTURE;

        Comparator<OffsetDateTime> byInstant = Comparator.comparing(OffsetDateTime::toInstant);
        OffsetDateTime first = forward ? Collections.min(sourceStops.values(), byInstant) : Collections.max(
                sourceStops.values(), byInstant);
        sourceStops.forEach((stopId, time) -> sourceStopsAsTarget.put(stopId,
                (int) Math.abs(time.toEpochSecond() - first.toEpochSecond())));
    }

    private static long epochSecond(OffsetDateTime time) {
        return time.toEpochSecond();
    }

    /**
     * Optimizes the travel time of each connection of a query.
     *
     * @param connections the connections of the query at a time, in the direction of the search.
     * @return the connections, each replaced by a shorter one of the same arrival (departure) and number of trips if
     * there is one.
     */
    List<Connection> optimize(List<Connection> connections) {
        List<Connection> optimized = new ArrayList<>(connections.size());
        for (Connection connection : connections) {
            optimized.add(optimize(connection));
        }

        return optimized;
    }

    private Connection optimize(Connection connection) {
        int trips = connection.getRouteLegs().size();
        if (trips == 0) {
            // on foot alone, it leaves when it arrives less the walk
            return connection;
        }

        long timeAtTarget = timeAtTarget(connection);
        Map<String, OffsetDateTime> reverseSourceStops = new HashMap<>();
        OffsetDateTime reference = forward ? connection.getArrivalTime() : connection.getDepartureTime();
        targetStops.forEach((stopId, walk) -> reverseSourceStops.put(stopId,
                reference.plusSeconds(timeAtTarget - epochSecond(reference) - (forward ? walk : -walk))));

        List<Connection> candidates;
        try {
            candidates = raptor.connectionQuery(reverseSourceStops, sourceStopsAsTarget,
                    forward ? TimeType.ARRIVAL : TimeType.DEPARTURE, reverseConfig(connection, trips, timeAtTarget));
        } catch (IllegalArgumentException e) {
            log.debug("No query in the other direction for the travel time of a connection: {}", e.getMessage());
            return connection;
        }

        Connection best = connection;
        long bestTime = timeAtSource(connection);
        for (Connection candidate : candidates) {
            long candidateTime = timeAtSource(candidate);
            boolean better = forward ? candidateTime > bestTime : candidateTime < bestTime;
            boolean sameTrips = candidate.getRouteLegs().size() == trips;
            if (better && sameTrips && reachesTargetBy(candidate, timeAtTarget) && leavesSourceStopInTime(candidate)) {
                best = candidate;
                bestTime = candidateTime;
            }
        }

        return best;
    }

    /**
     * The configuration of the query in the other direction: as many trips at most, transfers allowed at the other
     * ends, and a travel time no longer than the connection's, from the time it reaches the target at to the time it
     * leaves the source stops at, as the query in the other direction measures both.
     */
    private QueryConfig reverseConfig(Connection connection, int trips, long timeAtTarget) {
        long timeAtSource = timeAtSource(connection);
        // the earliest (latest) source time of the query in the other direction, from which it measures travel times
        long firstReverseSourceTime = timeAtTarget - (forward ? minimumWalk() : -minimumWalk());
        long travelTime = Math.max(1, Math.abs(firstReverseSourceTime - timeAtSource));

        return config.toBuilder()
                .maximumTransfers(trips - 1)
                .maximumTravelDuration((int) Math.min(config.getMaximumTravelDuration(), travelTime))
                .allowSourceTransfer(config.isAllowTargetTransfer())
                .allowTargetTransfer(config.isAllowSourceTransfer())
                .build();
    }

    private int minimumWalk() {
        return targetStops.values().stream().mapToInt(Integer::intValue).min().orElse(0);
    }

    /**
     * The time a connection reaches the target at: after the walk from its last stop, or for an arrival query, before
     * the walk to its first stop.
     */
    private long timeAtTarget(Connection connection) {
        if (forward) {
            return epochSecond(connection.getArrivalTime()) + targetStops.get(connection.getToStopId());
        }
        return epochSecond(connection.getDepartureTime()) - targetStops.get(connection.getFromStopId());
    }

    /**
     * The time a connection leaves the source stops at, in the ranking of the query: its departure (arrival) less
     * (plus) the time of its source stop relative to the earliest (latest) source time.
     */
    private long timeAtSource(Connection connection) {
        String sourceStop = forward ? connection.getFromStopId() : connection.getToStopId();
        long time = epochSecond(forward ? connection.getDepartureTime() : connection.getArrivalTime());
        int relative = sourceStopsAsTarget.getOrDefault(sourceStop, 0);
        return forward ? time - relative : time + relative;
    }

    private boolean reachesTargetBy(Connection connection, long timeAtTarget) {
        String targetStop = forward ? connection.getToStopId() : connection.getFromStopId();
        Integer walk = targetStops.get(targetStop);
        if (walk == null) {
            return false;
        }

        if (forward) {
            return epochSecond(connection.getArrivalTime()) + walk <= timeAtTarget;
        }
        return epochSecond(connection.getDepartureTime()) - walk >= timeAtTarget;
    }

    /**
     * Whether a connection leaves a source stop no earlier than its time (reaches it no later), which the query in the
     * other direction does not know.
     */
    private boolean leavesSourceStopInTime(Connection connection) {
        String sourceStop = forward ? connection.getFromStopId() : connection.getToStopId();
        OffsetDateTime sourceTime = sourceStops.get(sourceStop);
        if (sourceTime == null) {
            return false;
        }

        if (forward) {
            return !connection.getDepartureTime().isBefore(sourceTime);
        }
        return !connection.getArrivalTime().isAfter(sourceTime);
    }

}
