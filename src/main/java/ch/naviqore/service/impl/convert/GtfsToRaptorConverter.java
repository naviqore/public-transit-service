package ch.naviqore.service.impl.convert;

import ch.naviqore.gtfs.schedule.model.*;
import ch.naviqore.gtfs.schedule.type.TransferType;
import ch.naviqore.raptor.Raptor;
import ch.naviqore.raptor.RaptorBuilder;
import ch.naviqore.service.impl.transfer.TransferGenerator;
import lombok.extern.log4j.Log4j2;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Maps GTFS schedule to Raptor
 * <p>
 * For each sub-route in a GTFS route a route in Raptor is created. Only the "minimum time" transfers between different
 * stations are considered as Raptor transfers. In the Raptor model, transfers are treated exclusively as pedestrian
 * paths between stations, reflecting necessary walking connections. Thus, other types of GTFS transfers are omitted
 * from the mapping process to align with Raptor's conceptual model.
 *
 * @author munterfi
 */
@Log4j2
public class GtfsToRaptorConverter {

    private final Set<GtfsRoutePartitioner.SubRoute> addedSubRoutes = new HashSet<>();
    private final Set<String> addedStops = new HashSet<>();
    private final RaptorBuilder builder = Raptor.builder();
    private final GtfsRoutePartitioner partitioner;
    private final List<TransferGenerator.Transfer> additionalTransfers;
    private final GtfsSchedule schedule;

    public GtfsToRaptorConverter(GtfsSchedule schedule) {
        this(schedule, List.of());
    }

    public GtfsToRaptorConverter(GtfsSchedule schedule, List<TransferGenerator.Transfer> additionalTransfers) {
        this.partitioner = new GtfsRoutePartitioner(schedule);
        this.additionalTransfers = additionalTransfers;
        this.schedule = schedule;
    }

    public Raptor convert(LocalDate date) {
        List<Trip> activeTrips = schedule.getActiveTrips(date);
        log.info("Converting {} active trips from GTFS schedule to Raptor model", activeTrips.size());

        for (Trip trip : activeTrips) {
            GtfsRoutePartitioner.SubRoute subRoute = partitioner.getSubRoute(trip);

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
                if (transfer.getTransferType() == TransferType.MINIMUM_TIME && stop != transfer.getToStop() && transfer.getMinTransferTime()
                        .isPresent()) {
                    try {
                        builder.addTransfer(stop.getId(), transfer.getToStop().getId(),
                                transfer.getMinTransferTime().get());
                    } catch (IllegalArgumentException e) {
                        // TODO: Problem is that with active trips we already filtered some stops which have no active
                        //  trip anymore, so they are not added. Maybe we should build the Raptor always for the
                        //  complete schedule, and add use masking array for the stop times of when we want to create
                        //  routes at a specific date. This would also be more efficient.
                        log.warn("Omit adding transfer: {}", e.getMessage());
                    }
                }
            }
        }

        for (TransferGenerator.Transfer transfer : additionalTransfers) {

            if (transfer.from() == transfer.to()) {
                // TODO: Make Raptor handle same station transfers correctly. This is a workaround to avoid adding
                //  transfers between the same station, as not implemented yet.
                log.warn("Omit adding transfer from {} 2to {} with duration {} as it is the same stop",
                        transfer.from().getId(), transfer.to().getId(), transfer.duration());
                continue;
            }

            if (schedule.getStops()
                    .get(transfer.from().getId())
                    .getTransfers()
                    .stream()
                    .anyMatch(t -> t.getFromStop().equals(transfer.from()) && t.getToStop()
                            .equals(transfer.to()) && t.getTransferType() == TransferType.MINIMUM_TIME)) {
                log.warn(
                        "Omit adding additional transfer from {} to {} with duration {} as it has already been defined",
                        transfer.from().getId(), transfer.to().getId(), transfer.duration());
                continue;
            }
            try {
                builder.addTransfer(transfer.from().getId(), transfer.to().getId(), transfer.duration());
            } catch (IllegalArgumentException e) {
                // TODO: Same problem as above
                log.warn("Omit adding transfer: {}", e.getMessage());
            }
        }

    }
}
