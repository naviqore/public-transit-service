package org.naviqore.service.gtfs.raptor;

import org.jspecify.annotations.Nullable;
import org.naviqore.gtfs.schedule.model.GtfsSchedule;
import org.naviqore.gtfs.schedule.type.AccessibilityInformation;
import org.naviqore.gtfs.schedule.type.BikeInformation;
import org.naviqore.service.Route;
import org.naviqore.service.Stop;
import org.naviqore.service.TimeType;
import org.naviqore.service.Validity;
import org.naviqore.service.spi.ScheduledStopTime;
import org.naviqore.service.spi.ScheduledTrip;
import org.naviqore.service.spi.TransitSchedule;
import org.naviqore.utils.spatial.GeoCoordinate;
import org.naviqore.utils.spatial.index.KDTree;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Adapts a {@link GtfsSchedule} to the {@link TransitSchedule} the public transit service is built on.
 */
public class GtfsTransitSchedule implements TransitSchedule {

    private final GtfsSchedule schedule;
    private final KDTree<org.naviqore.gtfs.schedule.model.Stop> spatialStopIndex;

    public GtfsTransitSchedule(GtfsSchedule schedule,
                               KDTree<org.naviqore.gtfs.schedule.model.Stop> spatialStopIndex) {
        this.schedule = schedule;
        this.spatialStopIndex = spatialStopIndex;
    }

    private static ScheduledStopTime map(org.naviqore.gtfs.schedule.model.StopTime stopTime) {
        return new ScheduledStopTime(stopTime.trip().getId(), stopTime.stop().getId(),
                stopTime.arrival().getTotalSeconds(), stopTime.departure().getTotalSeconds());
    }

    @Override
    public Validity getValidity() {
        return new GtfsRaptorValidity(schedule);
    }

    @Override
    public boolean hasAccessibilityInformation() {
        return schedule.hasTripAccessibilityInformation();
    }

    @Override
    public boolean hasBikeInformation() {
        return schedule.hasTripBikeInformation();
    }

    @Override
    public List<Stop> getStops() {
        return schedule.getStops().values().stream().map(TypeMapper::map).toList();
    }

    @Override
    public @Nullable Stop getStop(String stopId) {
        return TypeMapper.map(schedule.getStops().get(stopId));
    }

    @Override
    public Optional<Stop> getParent(String stopId) {
        return schedule.getStops().get(stopId).getParent().map(TypeMapper::map);
    }

    @Override
    public List<Stop> getChildren(String stopId) {
        List<org.naviqore.gtfs.schedule.model.Stop> children = schedule.getStops().get(stopId).getChildren();
        return children == null ? List.of() : children.stream().map(TypeMapper::map).toList();
    }

    @Override
    public List<Stop> getStopsWithinRadius(GeoCoordinate location, int radius) {
        return spatialStopIndex.rangeSearch(location, radius).stream().map(TypeMapper::map).toList();
    }

    @Override
    public @Nullable Stop getNearestStop(GeoCoordinate location) {
        return TypeMapper.map(spatialStopIndex.nearestNeighbor(location));
    }

    @Override
    public @Nullable Route getRoute(String routeId) {
        org.naviqore.gtfs.schedule.model.Route route = schedule.getRoutes().get(routeId);
        return route == null ? null : TypeMapper.map(route);
    }

    @Override
    public @Nullable ScheduledTrip getTrip(String tripId) {
        org.naviqore.gtfs.schedule.model.Trip trip = schedule.getTrips().get(tripId);

        if (trip == null) {
            return null;
        }

        return new ScheduledTrip(trip.getId(), trip.getHeadSign(), trip.getRoute().getId(),
                trip.getRoute().getAgency().timezone(),
                trip.getStopTimes().stream().map(GtfsTransitSchedule::map).toList(),
                trip.getBikesAllowed() == BikeInformation.ALLOWED,
                trip.getWheelchairAccessible() == AccessibilityInformation.ACCESSIBLE);
    }

    @Override
    public boolean isTripActive(String tripId, LocalDate serviceDay) {
        return schedule.getTrips().get(tripId).getCalendar().isServiceAvailable(serviceDay);
    }

    @Override
    public List<ScheduledStopTime> getStopTimes(String stopId, OffsetDateTime from, OffsetDateTime to,
                                                TimeType timeType) {
        return schedule.getStopTimes(stopId, from, to, TypeMapper.mapToGtfs(timeType))
                .stream()
                .map(GtfsTransitSchedule::map)
                .toList();
    }

}
