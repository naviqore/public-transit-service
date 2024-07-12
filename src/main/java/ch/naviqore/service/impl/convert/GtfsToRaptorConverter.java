package ch.naviqore.service.impl.convert;

import ch.naviqore.gtfs.schedule.model.*;
import ch.naviqore.gtfs.schedule.type.TransferType;
import ch.naviqore.raptor.RaptorAlgorithm;
import ch.naviqore.raptor.router.RaptorRouterBuilder;
import ch.naviqore.service.impl.transfer.TransferGenerator;
import ch.naviqore.utils.cache.EvictionCache;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
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
 *
 * @author munterfi
 */
@Slf4j
public class GtfsToRaptorConverter {

    private final Set<GtfsRoutePartitioner.SubRoute> addedSubRoutes = new HashSet<>();
    private final Set<String> addedStops = new HashSet<>();
    private final RaptorRouterBuilder builder;
    private final GtfsRoutePartitioner partitioner;
    private final List<TransferGenerator.Transfer> additionalTransfers;
    private final GtfsSchedule schedule;

    public GtfsToRaptorConverter(GtfsSchedule schedule, int sameStopTransferTime, int stopTimeCacheSize,
                                 EvictionCache.Strategy stopTimeCacheStrategy) {
        this(schedule, sameStopTransferTime, 1, new GtfsTripMaskProvider(schedule), stopTimeCacheSize,
                stopTimeCacheStrategy);
    }

    public GtfsToRaptorConverter(GtfsSchedule schedule, int sameStopTransferTime, int maxDaysToScan,
                                 GtfsTripMaskProvider tripMaskProvider, int stopTimeCacheSize,
                                 EvictionCache.Strategy stopTimeCacheStrategy) {
        this(schedule, List.of(), sameStopTransferTime, maxDaysToScan, tripMaskProvider, stopTimeCacheSize,
                stopTimeCacheStrategy);
    }

    public GtfsToRaptorConverter(GtfsSchedule schedule, List<TransferGenerator.Transfer> additionalTransfers,
                                 int sameStopTransferTime, int maxDaysToScan, GtfsTripMaskProvider tripMaskProvider,
                                 int stopTimeCacheSize, EvictionCache.Strategy stopTimeCacheStrategy) {
        this.partitioner = new GtfsRoutePartitioner(schedule);
        this.additionalTransfers = additionalTransfers;
        this.schedule = schedule;
        this.builder = RaptorAlgorithm.builder(sameStopTransferTime, maxDaysToScan, tripMaskProvider, stopTimeCacheSize,
                stopTimeCacheStrategy);
    }

    public RaptorAlgorithm convert() {
        Collection<Trip> trips = schedule.getTrips().values();
        log.info("Converting {} trips from GTFS schedule to Raptor model", trips.size());

        for (Trip trip : trips) {
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

        // add additional transfers
        for (TransferGenerator.Transfer transfer : additionalTransfers) {

            // TODO: Just overwrite with precedence order, avoid costly lookups
            if (schedule.getStops()
                    .get(transfer.from().getId())
                    .getTransfers()
                    .stream()
                    .anyMatch(t -> t.getFromStop().equals(transfer.from()) && t.getToStop()
                            .equals(transfer.to()) && t.getTransferType() == TransferType.MINIMUM_TIME)) {
                log.debug(
                        "Omit adding additional transfer from {} to {} with duration {} as it has already been defined",
                        transfer.from().getId(), transfer.to().getId(), transfer.duration());
                continue;
            }
            try {
                builder.addTransfer(transfer.from().getId(), transfer.to().getId(), transfer.duration());
            } catch (IllegalArgumentException e) {
                // TODO: Same problem as above
                log.debug("Omit adding transfer: {}", e.getMessage());
            }
        }

    }
}
