package ch.naviqore;

import ch.naviqore.gtfs.schedule.model.GtfsSchedule;
import ch.naviqore.gtfs.schedule.model.Route;
import ch.naviqore.gtfs.schedule.model.StopTime;
import ch.naviqore.gtfs.schedule.model.Trip;
import ch.naviqore.raptor.model.RouteTraversal;
import ch.naviqore.raptor.model.RouteTraversalBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Maps GTFS schedule to Raptor
 *
 * @author munterfi
 */
@RequiredArgsConstructor
@Log4j2
public class GtfsToRaptorMapper {

    private final Set<Route> routes = new HashSet<>();
    private final RouteTraversalBuilder builder;

    public RouteTraversal map(GtfsSchedule schedule, LocalDate date) {
        List<Trip> activeTrips = schedule.getActiveTrips(date);
        log.info("Mapping {} active trips from GTFS schedule to Raptor model", activeTrips.size());
        for (Trip trip : activeTrips) {
            Route route = trip.getRoute();
            if (!routes.contains(route)) {
                routes.add(route);
                builder.addRoute(route.getId());
                // TODO: Add test for consistency of route stops
                for (StopTime stopTime : trip.getStopTimes()) {
                    builder.addRouteStop(stopTime.stop().getId(), route.getId());
                }
            }
            for (StopTime stopTime : trip.getStopTimes()) {
                builder.addStopTime(stopTime.stop().getId(), route.getId(), stopTime.arrival().getTotalSeconds(),
                        stopTime.departure().getTotalSeconds());
            }
        }

        return builder.build();
    }
}
