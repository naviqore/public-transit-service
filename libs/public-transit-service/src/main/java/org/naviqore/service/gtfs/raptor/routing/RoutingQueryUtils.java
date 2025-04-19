package org.naviqore.service.gtfs.raptor.routing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.naviqore.gtfs.schedule.model.GtfsSchedule;
import org.naviqore.raptor.QueryConfig;
import org.naviqore.raptor.RaptorAlgorithm;
import org.naviqore.service.*;
import org.naviqore.service.config.ConnectionQueryConfig;
import org.naviqore.service.config.ServiceConfig;
import org.naviqore.service.gtfs.raptor.TypeMapper;
import org.naviqore.service.walk.WalkCalculator;
import org.naviqore.utils.spatial.GeoCoordinate;
import org.naviqore.utils.spatial.index.KDTree;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private final GtfsSchedule schedule;
    private final KDTree<org.naviqore.gtfs.schedule.model.Stop> spatialStopIndex;
    private final WalkCalculator walkCalculator;
    private final RaptorAlgorithm raptor;

    private static QueryConfig prepareRaptorQueryConfig(ConnectionQueryConfig queryConfig, boolean allowSourceTransfer,
                                                        boolean allowTargetTransfer) {
        QueryConfig config = TypeMapper.map(queryConfig);
        config.setAllowSourceTransfer(allowSourceTransfer);
        config.setAllowTargetTransfer(allowTargetTransfer);

        return config;
    }

    List<org.naviqore.raptor.Connection> routeConnections(Map<String, LocalDateTime> sourceStops,
                                                          Map<String, Integer> targetStops, TimeType timeType,
                                                          ConnectionQueryConfig queryConfig,
                                                          boolean allowSourceTransfer, boolean allowTargetTransfer) {
        QueryConfig config = prepareRaptorQueryConfig(queryConfig, allowSourceTransfer, allowTargetTransfer);

        if (timeType == TimeType.DEPARTURE) {
            return raptor.routeEarliestArrival(sourceStops, targetStops, config);
        } else {
            return raptor.routeLatestDeparture(targetStops, sourceStops, config);
        }
    }

    Map<String, org.naviqore.raptor.Connection> createIsolines(Map<String, LocalDateTime> sourceStops,
                                                               TimeType timeType, ConnectionQueryConfig queryConfig,
                                                               boolean allowSourceTransfer) {
        // allow target transfers does not work for isolines since no targets are defined
        return raptor.routeIsolines(sourceStops, TypeMapper.map(timeType),
                prepareRaptorQueryConfig(queryConfig, allowSourceTransfer, true));
    }

    Map<String, LocalDateTime> getStopsWithWalkTimeFromLocation(GeoCoordinate location, LocalDateTime startTime,
                                                                TimeType timeType, ConnectionQueryConfig queryConfig) {
        Map<String, Integer> stopsWithWalkTime = getStopsWithWalkTimeFromLocation(location, queryConfig);
        return stopsWithWalkTime.entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        entry -> timeType == TimeType.DEPARTURE ? startTime.plusSeconds(
                                entry.getValue()) : startTime.minusSeconds(entry.getValue())));
    }

    Map<String, Integer> getStopsWithWalkTimeFromLocation(GeoCoordinate location, ConnectionQueryConfig queryConfig) {
        List<org.naviqore.gtfs.schedule.model.Stop> nearestStops = new ArrayList<>(
                spatialStopIndex.rangeSearch(location, serviceConfig.getWalkingSearchRadius()));

        if (nearestStops.isEmpty()) {
            nearestStops.add(spatialStopIndex.nearestNeighbour(location));
        }

        Map<String, Integer> stopsWithWalkTime = new HashMap<>();
        for (org.naviqore.gtfs.schedule.model.Stop stop : nearestStops) {
            int walkDuration = walkCalculator.calculateWalk(location, stop.getCoordinate()).duration();
            if (walkDuration <= queryConfig.getMaximumWalkingDuration()) {
                stopsWithWalkTime.put(stop.getId(), walkDuration);
            }
        }

        return stopsWithWalkTime;
    }

    Stop getStopById(String stopId) {
        return TypeMapper.map(schedule.getStops().get(stopId));
    }

    Map<String, LocalDateTime> getAllChildStopsFromStop(Stop stop, LocalDateTime time) {
        List<org.naviqore.gtfs.schedule.model.Stop> stops = schedule.getRelatedStops(stop.getId());
        Map<String, LocalDateTime> stopWithDateTime = new HashMap<>();
        for (org.naviqore.gtfs.schedule.model.Stop scheduleStop : stops) {
            stopWithDateTime.put(scheduleStop.getId(), time);
        }

        return stopWithDateTime;
    }

    Map<String, Integer> getAllChildStopsFromStop(Stop stop) {
        List<org.naviqore.gtfs.schedule.model.Stop> stops = schedule.getRelatedStops(stop.getId());
        Map<String, Integer> stopsWithWalkTime = new HashMap<>();
        for (org.naviqore.gtfs.schedule.model.Stop scheduleStop : stops) {
            stopsWithWalkTime.put(scheduleStop.getId(), 0);
        }

        return stopsWithWalkTime;
    }

    @Nullable Walk createFirstWalk(GeoCoordinate source, String firstStopId, LocalDateTime departureTime) {
        org.naviqore.gtfs.schedule.model.Stop firstStop = schedule.getStops().get(firstStopId);
        WalkCalculator.Walk firstWalk = walkCalculator.calculateWalk(source, firstStop.getCoordinate());
        int duration = firstWalk.duration() + serviceConfig.getTransferTimeAccessEgress();

        if (shouldCreateWalk(duration, firstWalk)) {
            return TypeMapper.createWalk(firstWalk.distance(), duration, WalkType.FIRST_MILE,
                    departureTime.minusSeconds(duration), departureTime, source, firstStop.getCoordinate(),
                    TypeMapper.map(firstStop));
        }

        return null;
    }

    @Nullable
    public Transfer createFirstWalkTransfer(Stop sourceStop, String firstStopId, LocalDateTime departureTime) {
        org.naviqore.gtfs.schedule.model.Stop firstStop = schedule.getStops().get(firstStopId);
        WalkCalculator.Walk firstWalkTransfer = walkCalculator.calculateWalk(sourceStop.getCoordinate(),
                firstStop.getCoordinate());
        int duration = firstWalkTransfer.duration() + serviceConfig.getTransferTimeAccessEgress();

        if (shouldCreateWalk(duration, firstWalkTransfer)) {
            return TypeMapper.createTransfer(firstWalkTransfer.distance(), duration,
                    departureTime.minusSeconds(duration), departureTime, sourceStop, TypeMapper.map(firstStop));
        }

        return null;
    }

    @Nullable Walk createLastWalk(GeoCoordinate target, String lastStopId, LocalDateTime arrivalTime) {
        org.naviqore.gtfs.schedule.model.Stop lastStop = schedule.getStops().get(lastStopId);
        WalkCalculator.Walk lastWalk = walkCalculator.calculateWalk(target, lastStop.getCoordinate());
        int duration = lastWalk.duration() + serviceConfig.getTransferTimeAccessEgress();

        if (shouldCreateWalk(duration, lastWalk)) {
            return TypeMapper.createWalk(lastWalk.distance(), duration, WalkType.LAST_MILE, arrivalTime,
                    arrivalTime.plusSeconds(duration), lastStop.getCoordinate(), target, TypeMapper.map(lastStop));
        }

        return null;
    }

    @Nullable
    public Transfer createLastWalkTransfer(Stop target, String lastStopId, LocalDateTime arrivalTime) {
        org.naviqore.gtfs.schedule.model.Stop lastStop = schedule.getStops().get(lastStopId);
        WalkCalculator.Walk lastWalkTransfer = walkCalculator.calculateWalk(target.getCoordinate(),
                lastStop.getCoordinate());
        int duration = lastWalkTransfer.duration() + serviceConfig.getTransferTimeAccessEgress();

        if (shouldCreateWalk(duration, lastWalkTransfer)) {
            return TypeMapper.createTransfer(lastWalkTransfer.distance(), duration, arrivalTime,
                    arrivalTime.plusSeconds(duration), TypeMapper.map(lastStop), target);
        }

        return null;
    }

    // Only map the walk to the service level if it is longer than the walking duration minimum defined in the service,
    // and it is not a walk from the same location to the same location (this is a fallback; in most cases,
    // this will be filtered out by the first condition).
    private boolean shouldCreateWalk(int duration, WalkCalculator.Walk walk) {
        return duration > serviceConfig.getWalkingDurationMinimum() && walk.duration() > 0;
    }

    Connection composeConnection(org.naviqore.raptor.Connection connection) {
        return TypeMapper.map(connection, null, null, schedule);
    }

    Connection composeConnection(Leg firstMile, org.naviqore.raptor.Connection connection) {
        return TypeMapper.map(connection, firstMile, null, schedule);
    }

    Connection composeConnection(org.naviqore.raptor.Connection connection, Leg lastMile) {
        return TypeMapper.map(connection, null, lastMile, schedule);
    }

    Connection composeConnection(Leg firstMile, org.naviqore.raptor.Connection connection, Leg lastMile) {
        return TypeMapper.map(connection, firstMile, lastMile, schedule);
    }

    // The raptor algorithm does not consider the first mile walk time, so we need to filter out connections
    // that exceed the maximum travel time
    boolean isBelowMaximumTravelTime(Connection serviceConnection, ConnectionQueryConfig queryConfig) {
        return Duration.between(serviceConnection.getArrivalTime(), serviceConnection.getDepartureTime())
                .getSeconds() <= queryConfig.getMaximumTravelTime();
    }
}
