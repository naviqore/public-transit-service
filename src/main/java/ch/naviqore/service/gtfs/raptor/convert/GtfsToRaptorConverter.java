package ch.naviqore.service.gtfs.raptor.convert;

import ch.naviqore.gtfs.schedule.model.GtfsSchedule;
import ch.naviqore.gtfs.schedule.model.Stop;
import ch.naviqore.gtfs.schedule.model.StopTime;
import ch.naviqore.gtfs.schedule.model.Transfer;
import ch.naviqore.gtfs.schedule.type.TransferType;
import ch.naviqore.raptor.router.RaptorConfig;
import ch.naviqore.raptor.router.RaptorRouter;
import ch.naviqore.raptor.router.RaptorRouterBuilder;
import ch.naviqore.service.gtfs.raptor.transfer.TransferGenerator;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

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

    private final Set<String> addedStops = new HashSet<>();
    private final RaptorRouterBuilder builder;
    private final GtfsRoutePartitioner partitioner;
    private final List<TransferGenerator.Transfer> additionalTransfers;
    private final GtfsSchedule schedule;

    public GtfsToRaptorConverter(GtfsSchedule schedule, RaptorConfig config) {
        this(schedule, List.of(), config);
    }

    public GtfsToRaptorConverter(GtfsSchedule schedule, List<TransferGenerator.Transfer> additionalTransfers,
                                 RaptorConfig config) {
        this.partitioner = new GtfsRoutePartitioner(schedule);
        this.additionalTransfers = additionalTransfers;
        this.schedule = schedule;
        this.builder = RaptorRouter.builder(config);
    }

    public RaptorRouter convert() {
        log.info("Converting {} trips from GTFS schedule to Raptor data model", schedule.getTrips().size());

        for (var route : schedule.getRoutes().values()) {
            for (GtfsRoutePartitioner.SubRoute subRoute : partitioner.getSubRoutes(route)) {
                addRoute(subRoute);
            }
        }

        addTransfers();

        return builder.build();
    }

    // add raptor route for each sub route of the gtfs routes
    private void addRoute(GtfsRoutePartitioner.SubRoute subRoute) {

        // add stops of sub route that are not already added
        List<String> stopIds = subRoute.getStopsSequence().stream().map(Stop::getId).toList();
        for (String stopId : stopIds) {
            if (!addedStops.contains(stopId)) {
                builder.addStop(stopId);
                addedStops.add(stopId);
            }
        }

        // add sub route as raptor route
        builder.addRoute(subRoute.getId(), stopIds);

        // add trips of sub route
        for (var trip : subRoute.getTrips()) {
            builder.addTrip(trip.getId(), subRoute.getId());
            List<StopTime> stopTimes = trip.getStopTimes();
            for (int i = 0; i < stopTimes.size(); i++) {
                StopTime stopTime = stopTimes.get(i);
                builder.addStopTime(subRoute.getId(), trip.getId(), i, stopTime.stop().getId(),
                        stopTime.arrival().getTotalSeconds(), stopTime.departure().getTotalSeconds());
            }
        }
    }

    // add raptor transfer for each gtfs or additional transfer
    private void addTransfers() {

        // add all additional transfers
        for (TransferGenerator.Transfer transfer : additionalTransfers) {
            builder.addTransfer(transfer.from().getId(), transfer.to().getId(), transfer.duration());
        }

        // sometimes the parent stop might hold transfers for child stops, thus parent stops have to be checked as well,
        // however to ensure that the explicit transfers between child stops have precedence this is handled
        for (String stopId : addedStops) {
            Stop stop = schedule.getStops().get(stopId);
            Collection<TransferGenerator.Transfer> parentTransfers = stop.getParent()
                    .map(ps -> getParentTransfers(stop, ps))
                    .orElse(Collections.emptyList());
            for (TransferGenerator.Transfer transfer : parentTransfers) {
                builder.addTransfer(transfer.from().getId(), transfer.to().getId(), transfer.duration());
            }
            for (Stop childStop : stop.getChildren()) {
                for (TransferGenerator.Transfer childTransfer : getParentTransfers(stop, childStop)) {
                    builder.addTransfer(stop.getId(), childTransfer.to().getId(), childTransfer.duration());
                }
            }
            for (Transfer stopTransfer : stop.getTransfers()) {
                if (stopTransfer.getTransferType() == TransferType.MINIMUM_TIME && stopTransfer.getMinTransferTime()
                        .isPresent()) {
                    for (Stop toChildStop : stopTransfer.getToStop().getChildren()) {
                        if (addedStops.contains(toChildStop.getId())) {
                            builder.addTransfer(stop.getId(), toChildStop.getId(),
                                    stopTransfer.getMinTransferTime().get());
                        }
                    }
                }
            }
        }

        // transfers from gtfs have precedence; already added additional transfers with the same source and target stop
        // will be overwritten, avoids costly lookups.
        for (String stopId : addedStops) {
            Stop stop = schedule.getStops().get(stopId);
            for (Transfer transfer : stop.getTransfers()) {
                if (transfer.getTransferType() == TransferType.MINIMUM_TIME && transfer.getMinTransferTime()
                        .isPresent() && addedStops.contains(transfer.getToStop().getId())) {
                    builder.addTransfer(stop.getId(), transfer.getToStop().getId(),
                            transfer.getMinTransferTime().get());
                }
            }
        }

    }

    private Collection<TransferGenerator.Transfer> getParentTransfers(Stop fromStop, Stop parentStop) {
        Map<Stop, TransferGenerator.Transfer> parentTransfers = new HashMap<>();
        List<TransferGenerator.Transfer> otherTransfers = new ArrayList<>();

        for (Transfer transfer : parentStop.getTransfers()) {
            if (transfer.getTransferType() != TransferType.MINIMUM_TIME || transfer.getMinTransferTime().isEmpty()) {
                continue;
            }
            Stop toStop = transfer.getToStop();
            if (addedStops.contains(toStop.getId())) {
                otherTransfers.add(
                        new TransferGenerator.Transfer(fromStop, toStop, transfer.getMinTransferTime().get()));
            }
            for (Stop childToStop : toStop.getChildren()) {
                if (!addedStops.contains(childToStop.getId())) {
                    continue;
                }
                parentTransfers.put(childToStop,
                        new TransferGenerator.Transfer(fromStop, childToStop, transfer.getMinTransferTime().get()));
            }
        }

        // overwrite transfers derived from children when explicit transfer declaration exists
        for (TransferGenerator.Transfer transfer : otherTransfers) {
            parentTransfers.put(transfer.to(), transfer);
        }

        return parentTransfers.values();
    }

}
