package org.naviqore.service.raptor.routing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.naviqore.raptor.QueryConfig;
import org.naviqore.raptor.RaptorAlgorithm;
import org.naviqore.service.*;
import org.naviqore.service.config.ConnectionQueryConfig;
import org.naviqore.service.config.ServiceConfig;
import org.naviqore.service.raptor.TypeMapper;
import org.naviqore.service.walk.WalkCalculator;
import org.naviqore.timetable.model.Timetable;
import org.naviqore.utils.spatial.GeoCoordinate;
import org.naviqore.utils.spatial.index.KDTree;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Utility class for preparing and composing results of templated connection and isoline queries.
 * <p>
 * Constant components (service configuration, schedule, walk calculator, and RAPTOR router) are provided via the
 * constructor. This class should be instantiated once per service configuration.
 */
@Slf4j
@RequiredArgsConstructor
class RoutingQueryUtils {

    private final ServiceConfig serviceConfig;
    private final Timetable schedule;
    private final KDTree<org.naviqore.timetable.model.Stop> spatialStopIndex;
    private final WalkCalculator walkCalculator;
    private final RaptorAlgorithm raptor;

    private static QueryConfig prepareRaptorQueryConfig(ConnectionQueryConfig queryConfig, boolean allowSourceTransfer,
                                                        boolean allowTargetTransfer) {
        return TypeMapper.map(queryConfig)
                .toBuilder()
                .allowSourceTransfer(allowSourceTransfer)
                .allowTargetTransfer(allowTargetTransfer)
                .build();
    }

    /**
     * Routes connections in the direction of the search: from the source stops at their times to the target stops,
     * backward in time for an arrival query. Without a time window, a query of RAPTOR, whose travel times are optimized
     * if the service is configured to (see {@link TravelTimeOptimizer}); with one, a range query, which reports the
     * latest departures (earliest arrivals) in the window anyway.
     */
    List<org.naviqore.raptor.Connection> routeConnections(Map<String, OffsetDateTime> sourceStops,
                                                          Map<String, Integer> targetStops, TimeType timeType,
                                                          ConnectionQueryConfig queryConfig,
                                                          boolean allowSourceTransfer, boolean allowTargetTransfer) {
        QueryConfig config = prepareRaptorQueryConfig(queryConfig, allowSourceTransfer, allowTargetTransfer);
        org.naviqore.raptor.TimeType raptorTimeType = TypeMapper.mapToRaptor(timeType);
        if (queryConfig.getTimeWindowDuration() > 0) {
            return raptor.connectionRangeQuery(sourceStops, targetStops, raptorTimeType,
                    Duration.ofSeconds(queryConfig.getTimeWindowDuration()), config);
        }

        List<org.naviqore.raptor.Connection> connections = raptor.connectionQuery(sourceStops, targetStops,
                raptorTimeType, config);
        if (!serviceConfig.isOptimizeTravelTime()) {
            return connections;
        }

        return new TravelTimeOptimizer(raptor, sourceStops, targetStops, raptorTimeType, config).optimize(connections);
    }

    /**
     * Routes isolines: the earliest arrival (latest departure) at each stop, or with a time window, the shortest travel
     * time among the connections leaving (arriving) in the window.
     */
    Map<String, org.naviqore.raptor.Connection> routeIsolines(Map<String, OffsetDateTime> sourceStops,
                                                              TimeType timeType, ConnectionQueryConfig queryConfig,
                                                              boolean allowSourceTransfer) {
        // allow target transfers does not work for isolines since no targets are defined
        QueryConfig config = prepareRaptorQueryConfig(queryConfig, allowSourceTransfer, true);
        org.naviqore.raptor.TimeType raptorTimeType = TypeMapper.mapToRaptor(timeType);
        if (queryConfig.getTimeWindowDuration() > 0) {
            return raptor.isolineRangeQuery(sourceStops, raptorTimeType,
                    Duration.ofSeconds(queryConfig.getTimeWindowDuration()), config);
        }

        return raptor.isolineQuery(sourceStops, raptorTimeType, config);
    }

    Map<String, OffsetDateTime> getStopsWithWalkTimeFromLocation(GeoCoordinate location, OffsetDateTime startTime,
                                                                 TimeType timeType, ConnectionQueryConfig queryConfig) {
        Map<String, Integer> stopsWithWalkTime = getStopsWithWalkTimeFromLocation(location, queryConfig);
        return stopsWithWalkTime.entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        entry -> timeType == TimeType.DEPARTURE ? startTime.plusSeconds(
                                entry.getValue()) : startTime.minusSeconds(entry.getValue())));
    }

    Map<String, Integer> getStopsWithWalkTimeFromLocation(GeoCoordinate location, ConnectionQueryConfig queryConfig) {
        List<org.naviqore.timetable.model.Stop> nearestStops = new ArrayList<>(
                spatialStopIndex.rangeSearch(location, serviceConfig.getWalkSearchRadius()));

        if (nearestStops.isEmpty()) {
            nearestStops.add(spatialStopIndex.nearestNeighbor(location));
        }

        Map<String, Integer> stopsWithWalkTime = new HashMap<>();
        for (org.naviqore.timetable.model.Stop stop : nearestStops) {
            int walkDuration = walkCalculator.calculateWalk(location, stop.getCoordinate()).duration();
            if (walkDuration <= queryConfig.getMaximumWalkDuration()) {
                // add access and egress time to the walk duration to account for the time between location and vehicle at stop
                stopsWithWalkTime.put(stop.getId(), walkDuration + serviceConfig.getTransferDurationAccessEgress());
            }
        }

        return stopsWithWalkTime;
    }

    Stop getStopById(String stopId) {
        return TypeMapper.map(Objects.requireNonNull(schedule.getStops().get(stopId)));
    }

    Map<String, OffsetDateTime> getAllChildStopsFromStop(Stop stop, OffsetDateTime time) {
        List<? extends org.naviqore.timetable.model.Stop> stops = schedule.getStopWithChildren(stop.getId());
        Map<String, OffsetDateTime> stopWithDateTime = new HashMap<>();
        for (org.naviqore.timetable.model.Stop scheduleStop : stops) {
            stopWithDateTime.put(scheduleStop.getId(), time);
        }

        return stopWithDateTime;
    }

    Map<String, Integer> getAllChildStopsFromStop(Stop stop) {
        List<? extends org.naviqore.timetable.model.Stop> stops = schedule.getStopWithChildren(stop.getId());
        Map<String, Integer> stopsWithWalkTime = new HashMap<>();
        for (org.naviqore.timetable.model.Stop scheduleStop : stops) {
            stopsWithWalkTime.put(scheduleStop.getId(), 0);
        }

        return stopsWithWalkTime;
    }

    /**
     * The first mile from a location to the first stop of a connection, before it leaves the stop at the given time.
     */
    Mile createFirstWalk(GeoCoordinate source, String firstStopId, OffsetDateTime departureTime) {
        org.naviqore.timetable.model.Stop firstStop = Objects.requireNonNull(schedule.getStops().get(firstStopId));
        WalkCalculator.Walk firstWalk = walkCalculator.calculateWalk(source, firstStop.getCoordinate());
        int duration = firstWalk.duration();
        int durationWithAccess = duration + serviceConfig.getTransferDurationAccessEgress();

        Walk walk = null;
        if (shouldCreateWalk(duration, firstWalk)) {
            walk = TypeMapper.createWalk(firstWalk.distance(), duration, WalkType.FIRST_MILE,
                    // shift walk time backwards to account access and egress time at the stop
                    departureTime.minusSeconds(durationWithAccess),
                    departureTime.minusSeconds(serviceConfig.getTransferDurationAccessEgress()), source,
                    firstStop.getCoordinate(), TypeMapper.map(firstStop));
        }

        return new Mile(walk, durationWithAccess);
    }

    /**
     * The first mile from a stop without departures to the first stop of a connection, before it leaves the stop at the
     * given time.
     */
    Mile createFirstWalkTransfer(Stop sourceStop, String firstStopId, OffsetDateTime departureTime) {
        org.naviqore.timetable.model.Stop firstStop = Objects.requireNonNull(schedule.getStops().get(firstStopId));
        WalkCalculator.Walk firstWalkTransfer = walkCalculator.calculateWalk(sourceStop.getCoordinate(),
                firstStop.getCoordinate());
        int duration = firstWalkTransfer.duration() + serviceConfig.getTransferDurationAccessEgress();

        Transfer transfer = null;
        if (shouldCreateWalk(duration, firstWalkTransfer)) {
            transfer = TypeMapper.createTransfer(firstWalkTransfer.distance(), duration,
                    departureTime.minusSeconds(duration), departureTime, sourceStop, TypeMapper.map(firstStop));
        }

        return new Mile(transfer, duration);
    }

    /**
     * The last mile from the last stop of a connection, reached at the given time, to a location.
     */
    Mile createLastWalk(GeoCoordinate target, String lastStopId, OffsetDateTime arrivalTime) {
        org.naviqore.timetable.model.Stop lastStop = Objects.requireNonNull(schedule.getStops().get(lastStopId));
        WalkCalculator.Walk lastWalk = walkCalculator.calculateWalk(target, lastStop.getCoordinate());
        int duration = lastWalk.duration() + serviceConfig.getTransferDurationAccessEgress();

        Walk walk = null;
        if (shouldCreateWalk(duration, lastWalk)) {
            walk = TypeMapper.createWalk(lastWalk.distance(), duration, WalkType.LAST_MILE, arrivalTime,
                    arrivalTime.plusSeconds(duration), lastStop.getCoordinate(), target, TypeMapper.map(lastStop));
        }

        return new Mile(walk, duration);
    }

    /**
     * The last mile from the last stop of a connection, reached at the given time, to a stop without arrivals.
     */
    Mile createLastWalkTransfer(Stop target, String lastStopId, OffsetDateTime arrivalTime) {
        org.naviqore.timetable.model.Stop lastStop = Objects.requireNonNull(schedule.getStops().get(lastStopId));
        WalkCalculator.Walk lastWalkTransfer = walkCalculator.calculateWalk(target.getCoordinate(),
                lastStop.getCoordinate());
        int duration = lastWalkTransfer.duration() + serviceConfig.getTransferDurationAccessEgress();

        Transfer transfer = null;
        if (shouldCreateWalk(duration, lastWalkTransfer)) {
            transfer = TypeMapper.createTransfer(lastWalkTransfer.distance(), duration, arrivalTime,
                    arrivalTime.plusSeconds(duration), TypeMapper.map(lastStop), target);
        }

        return new Mile(transfer, duration);
    }

    // Only map the walk to the service level if it is longer than the walking duration minimum defined in the service,
    // and it is not a walk from the same location to the same location (this is a fallback; in most cases,
    // this will be filtered out by the first condition).
    private boolean shouldCreateWalk(int duration, WalkCalculator.Walk walk) {
        return duration > serviceConfig.getWalkDurationMinimum() && walk.duration() > 0;
    }

    Connection composeConnection(org.naviqore.raptor.Connection connection) {
        return composeConnection(Mile.NONE, connection, Mile.NONE, new TypeMapper.Trips());
    }

    Connection composeConnection(Mile firstMile, org.naviqore.raptor.Connection connection, Mile lastMile) {
        return composeConnection(firstMile, connection, lastMile, new TypeMapper.Trips());
    }

    /**
     * Composes a connection that leaves the origin before its first mile and reaches the destination after its last
     * mile, whether or not they are legs of their own, so that its times are those its window applies to.
     */
    Connection composeConnection(Mile firstMile, org.naviqore.raptor.Connection connection, Mile lastMile,
                                 TypeMapper.Trips trips) {
        return TypeMapper.map(connection, firstMile.leg(), lastMile.leg(),
                connection.getDepartureTime().minusSeconds(firstMile.duration()),
                connection.getArrivalTime().plusSeconds(lastMile.duration()), schedule, trips);
    }

    // the router does not count the first and last mile, so the connections are filtered by their whole travel time
    boolean isBelowMaximumTravelTime(Connection serviceConnection, ConnectionQueryConfig queryConfig) {
        return Duration.between(serviceConnection.getDepartureTime(), serviceConnection.getArrivalTime())
                .toSeconds() <= queryConfig.getMaximumTravelDuration();
    }

    /**
     * The way between a location and the first or last stop of a connection: its leg, unless too short to be one, and
     * its duration with the access or egress time, which the connection takes either way.
     *
     * @param leg      the walk or transfer, or null if left out.
     * @param duration the duration in seconds.
     */
    record Mile(@Nullable Leg leg, int duration) {

        static final Mile NONE = new Mile(null, 0);
    }
}
