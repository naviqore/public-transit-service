package ch.naviqore.raptor;

import ch.naviqore.gtfs.schedule.model.GtfsSchedule;
import ch.naviqore.gtfs.schedule.model.Stop;
import ch.naviqore.gtfs.schedule.model.StopTime;
import ch.naviqore.gtfs.schedule.model.Trip;
import ch.naviqore.raptor.model.Raptor;
import ch.naviqore.raptor.model.RaptorBuilder;
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
@Log4j2
public class GtfsToRaptorConverter {

    private final Set<GtfsRoutePartitioner.SubRoute> subRoutes = new HashSet<>();
    private final Set<Stop> stops = new HashSet<>();
    private final RaptorBuilder builder = Raptor.builder();
    private final GtfsRoutePartitioner partitioner;
    private final GtfsSchedule schedule;

    public GtfsToRaptorConverter(GtfsSchedule schedule) {
        this.partitioner = new GtfsRoutePartitioner(schedule);
        this.schedule = schedule;
    }

    public Raptor convert(LocalDate date) {
        List<Trip> activeTrips = schedule.getActiveTrips(date);
        log.info("Converting {} active trips from GTFS schedule to Raptor model", activeTrips.size());
        for (Trip trip : activeTrips) {
            // Route route = trip.getRoute();
            GtfsRoutePartitioner.SubRoute subRoute = partitioner.getSubRoute(trip);
            if (!subRoutes.contains(subRoute)) {
                subRoutes.add(subRoute);
                builder.addRoute(subRoute.getId());
                for (StopTime stopTime : trip.getStopTimes()) {
                    if (!stops.contains(stopTime.stop())) {
                        stops.add(stopTime.stop());
                        builder.addStop(stopTime.stop().getId());
                    }
                    builder.addRouteStop(stopTime.stop().getId(), subRoute.getId());
                }
            }
            for (StopTime stopTime : trip.getStopTimes()) {
                builder.addStopTime(stopTime.stop().getId(), subRoute.getId(), stopTime.arrival().getTotalSeconds(),
                        stopTime.departure().getTotalSeconds());
            }
        }

        return builder.build();
    }
}
