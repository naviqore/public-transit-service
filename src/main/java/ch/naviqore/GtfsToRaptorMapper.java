package ch.naviqore;

import ch.naviqore.gtfs.schedule.model.*;
import ch.naviqore.raptor.model.Raptor;
import ch.naviqore.raptor.model.RaptorBuilder;
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

    private final Set<Stop> stops = new HashSet<>();
    private final Set<Route> routes = new HashSet<>();
    private final RaptorBuilder builder;

    public Raptor map(GtfsSchedule schedule, LocalDate date) {
        List<Trip> activeTrips = schedule.getActiveTrips(date);
        log.info("Mapping {} active trips from GTFS schedule to Raptor model", activeTrips.size());
        for (Trip trip : activeTrips) {
            Route route = trip.getRoute();
            if (!routes.contains(route)) {
                routes.add(route);
                builder.addRoute(route.getId());
                // TODO: Add test for consistency of route stops
                for (StopTime stopTime : trip.getStopTimes()) {
                    if (!stops.contains(stopTime.stop())) {
                        stops.add(stopTime.stop());
                        builder.addStop(stopTime.stop().getId());
                    }
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
