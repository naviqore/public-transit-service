package ch.naviqore.raptor.simple.gtfs;

import ch.naviqore.gtfs.schedule.model.*;
import ch.naviqore.gtfs.schedule.type.TransferType;
import ch.naviqore.raptor.RaptorAlgorithm;
import ch.naviqore.raptor.simple.RaptorRouterBuilder;
import ch.naviqore.raptor.simple.SimpleRaptorRouter;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Maps GTFS schedule to Raptor
 * <p>
 * For each sub-route in a GTFS route a route in Raptor is created. Only the "minimum time" transfers between different
 * stops are considered as Raptor transfers. In the Raptor model, transfers are treated exclusively as pedestrian paths
 * between stops, reflecting necessary walking connections. Thus, other types of GTFS transfers are omitted from the
 * mapping process to align with Raptor's conceptual model.
 */
@Slf4j
public class Converter {

    private final Set<RoutePartitioner.SubRoute> addedSubRoutes = new HashSet<>();
    private final Set<String> addedStops = new HashSet<>();
    private final RaptorRouterBuilder builder;
    private final RoutePartitioner partitioner;
    private final GtfsSchedule schedule;

    public Converter(GtfsSchedule schedule, int sameStopTransferTime) {
        this.partitioner = new RoutePartitioner(schedule);
        this.schedule = schedule;
        this.builder = SimpleRaptorRouter.builder(sameStopTransferTime);
    }

    public RaptorAlgorithm convert(LocalDate date) {
        List<Trip> activeTrips = schedule.getActiveTrips(date);
        log.info("Converting {} active trips from GTFS schedule to Raptor model", activeTrips.size());

        for (Trip trip : activeTrips) {
            RoutePartitioner.SubRoute subRoute = partitioner.getSubRoute(trip);

            // add route if not already
            if (!addedSubRoutes.contains(subRoute)) {
                List<String> stopIds = subRoute.getStopsSequence().stream().map(Stop::getId).toList();

                // add stops of that are not already added
                for (String stopId : stopIds) {
                    if (!addedStops.contains(stopId)) {
                        builder.addStop(stopId);
                        addedStops.add(stopId);
                    }
                }

                builder.addRoute(subRoute.getId(), stopIds);
                addedSubRoutes.add(subRoute);
            }

            // add current trip
            builder.addTrip(trip.getId(), subRoute.getId());
            List<StopTime> stopTimes = trip.getStopTimes();
            for (int i = 0; i < stopTimes.size(); i++) {
                StopTime stopTime = stopTimes.get(i);
                builder.addStopTime(subRoute.getId(), trip.getId(), i, stopTime.stop().getId(),
                        stopTime.arrival().getTotalSeconds(), stopTime.departure().getTotalSeconds());
            }
        }

        addTransfers();

        return builder.build();
    }

    private void addTransfers() {
        for (String stopId : addedStops) {
            Stop stop = schedule.getStops().get(stopId);
            for (Transfer transfer : stop.getTransfers()) {
                if (transfer.getTransferType() == TransferType.MINIMUM_TIME && transfer.getMinTransferTime()
                        .isPresent()) {
                    try {
                        builder.addTransfer(stop.getId(), transfer.getToStop().getId(),
                                transfer.getMinTransferTime().get());
                    } catch (IllegalArgumentException e) {
                        // TODO: Problem is that with active trips we already filtered some stops which have no active
                        //  trip anymore, so they are not added. Maybe we should build the Raptor always for the
                        //  complete schedule, and add use masking array for the stop times of when we want to create
                        //  routes at a specific date. This would also be more efficient.
                        log.debug("Omit adding transfer: {}", e.getMessage());
                    }
                }
            }
        }
    }
}
