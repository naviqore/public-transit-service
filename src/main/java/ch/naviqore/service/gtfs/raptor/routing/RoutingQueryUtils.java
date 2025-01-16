package ch.naviqore.service.gtfs.raptor.routing;

import ch.naviqore.gtfs.schedule.model.GtfsSchedule;
import ch.naviqore.raptor.RaptorAlgorithm;
import ch.naviqore.service.*;
import ch.naviqore.service.config.ConnectionQueryConfig;
import ch.naviqore.service.config.ServiceConfig;
import ch.naviqore.service.gtfs.raptor.TypeMapper;
import ch.naviqore.service.walk.WalkCalculator;
import ch.naviqore.utils.spatial.GeoCoordinate;
import ch.naviqore.utils.spatial.index.KDTree;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;

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

    // TODO: Maybe give the methods more distinct names from the perspective of the templated cases.

    private final ServiceConfig serviceConfig;
    private final GtfsSchedule schedule;
    private final KDTree<ch.naviqore.gtfs.schedule.model.Stop> spatialStopIndex;
    private final WalkCalculator walkCalculator;
    private final RaptorAlgorithm raptor;

    List<ch.naviqore.raptor.Connection> routeConnections(Map<String, LocalDateTime> sourceStops,
                                                         Map<String, Integer> targetStops, TimeType timeType,
                                                         ConnectionQueryConfig queryConfig) {
        if (timeType == TimeType.DEPARTURE) {
            return raptor.routeEarliestArrival(sourceStops, targetStops, TypeMapper.map(queryConfig));
        } else {
            return raptor.routeLatestDeparture(targetStops, sourceStops, TypeMapper.map(queryConfig));
        }
    }

    Map<String, ch.naviqore.raptor.Connection> createIsolines(Map<String, LocalDateTime> sourceStops, TimeType timeType,
                                                              ConnectionQueryConfig queryConfig) {
        return raptor.routeIsolines(sourceStops, TypeMapper.map(timeType), TypeMapper.map(queryConfig));
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
        List<ch.naviqore.gtfs.schedule.model.Stop> nearestStops = new ArrayList<>(
                spatialStopIndex.rangeSearch(location, serviceConfig.getWalkingSearchRadius()));

        if (nearestStops.isEmpty()) {
            nearestStops.add(spatialStopIndex.nearestNeighbour(location));
        }

        Map<String, Integer> stopsWithWalkTime = new HashMap<>();
        for (ch.naviqore.gtfs.schedule.model.Stop stop : nearestStops) {
            int walkDuration = walkCalculator.calculateWalk(location, stop.getCoordinate()).duration();
            if (walkDuration <= queryConfig.getMaximumWalkingDuration()) {
                stopsWithWalkTime.put(stop.getId(), walkDuration);
            }
        }

        return stopsWithWalkTime;
    }

    Map<String, LocalDateTime> getAllChildStopsFromStop(Stop stop, LocalDateTime time) {
        List<ch.naviqore.gtfs.schedule.model.Stop> stops = schedule.getRelatedStops(stop.getId());
        Map<String, LocalDateTime> stopWithDateTime = new HashMap<>();
        for (ch.naviqore.gtfs.schedule.model.Stop scheduleStop : stops) {
            stopWithDateTime.put(scheduleStop.getId(), time);
        }

        return stopWithDateTime;
    }

    Map<String, Integer> getAllChildStopsFromStop(Stop stop) {
        List<ch.naviqore.gtfs.schedule.model.Stop> stops = schedule.getRelatedStops(stop.getId());
        Map<String, Integer> stopsWithWalkTime = new HashMap<>();
        for (ch.naviqore.gtfs.schedule.model.Stop scheduleStop : stops) {
            stopsWithWalkTime.put(scheduleStop.getId(), 0);
        }

        return stopsWithWalkTime;
    }

    @Nullable Walk createFirstWalk(GeoCoordinate source, String firstStopId, LocalDateTime departureTime) {
        ch.naviqore.gtfs.schedule.model.Stop firstStop = schedule.getStops().get(firstStopId);
        WalkCalculator.Walk firstWalk = walkCalculator.calculateWalk(source, firstStop.getCoordinate());
        int firstWalkDuration = firstWalk.duration() + serviceConfig.getTransferTimeAccessEgress();

        // TODO: Move null check outside method
        if (firstWalkDuration > serviceConfig.getWalkingDurationMinimum()) {
            return TypeMapper.createWalk(firstWalk.distance(), firstWalkDuration, WalkType.FIRST_MILE,
                    departureTime.minusSeconds(firstWalkDuration), departureTime, source, firstStop.getCoordinate(),
                    TypeMapper.map(firstStop));
        }

        return null;
    }

    @Nullable Walk createLastWalk(GeoCoordinate target, String lastStopId, LocalDateTime arrivalTime) {
        ch.naviqore.gtfs.schedule.model.Stop lastScheduleStop = schedule.getStops().get(lastStopId);
        WalkCalculator.Walk lastWalk = walkCalculator.calculateWalk(target, lastScheduleStop.getCoordinate());
        int lastWalkDuration = lastWalk.duration() + serviceConfig.getTransferTimeAccessEgress();

        // TODO: Move null check outside method
        if (lastWalkDuration > serviceConfig.getWalkingDurationMinimum()) {
            return TypeMapper.createWalk(lastWalk.distance(), lastWalkDuration, WalkType.LAST_MILE, arrivalTime,
                    arrivalTime.plusSeconds(lastWalkDuration), lastScheduleStop.getCoordinate(), target,
                    TypeMapper.map(lastScheduleStop));
        }

        return null;
    }

    Connection composeConnection(ch.naviqore.raptor.Connection connection) {
        return TypeMapper.map(connection, null, null, schedule);
    }

    Connection composeConnection(Walk firstMile, ch.naviqore.raptor.Connection connection) {
        return TypeMapper.map(connection, firstMile, null, schedule);
    }

    Connection composeConnection(ch.naviqore.raptor.Connection connection, Walk lastMile) {
        return TypeMapper.map(connection, null, lastMile, schedule);
    }

    Connection composeConnection(Walk firstMile, ch.naviqore.raptor.Connection connection, Walk lastMile) {
        return TypeMapper.map(connection, firstMile, lastMile, schedule);
    }

    // The raptor algorithm does not consider the first mile walk time, so we need to filter out connections
    // that exceed the maximum travel time
    boolean isBelowMaximumTravelTime(Connection serviceConnection, ConnectionQueryConfig queryConfig) {
        return Duration.between(serviceConnection.getArrivalTime(), serviceConnection.getDepartureTime())
                .getSeconds() <= queryConfig.getMaximumTravelTime();
    }

}
