package ch.naviqore.service.gtfs.raptor.convert;

import ch.naviqore.gtfs.schedule.model.*;
import ch.naviqore.gtfs.schedule.type.TransferType;
import ch.naviqore.raptor.router.RaptorConfig;
import ch.naviqore.raptor.router.RaptorRouter;
import ch.naviqore.raptor.router.RaptorRouterBuilder;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

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

    private final Set<Stop> addedStops = new HashSet<>();
    private final GtfsSchedule schedule;
    private final List<TransferGenerator> transferGenerators;

    private final GtfsRoutePartitioner partitioner;
    private final RaptorRouterBuilder builder;

    public GtfsToRaptorConverter(RaptorConfig config, GtfsSchedule schedule) {
        this(config, schedule, List.of());
    }

    public GtfsToRaptorConverter(RaptorConfig config, GtfsSchedule schedule,
                                 List<TransferGenerator> transferGenerators) {
        this.schedule = schedule;
        this.transferGenerators = transferGenerators;
        this.partitioner = new GtfsRoutePartitioner(schedule);
        this.builder = RaptorRouter.builder(config);
    }

    public RaptorRouter run() {
        log.info("Converting {} trips from GTFS schedule to Raptor data model", schedule.getTrips().size());

        for (Route route : schedule.getRoutes().values()) {
            for (GtfsRoutePartitioner.SubRoute subRoute : partitioner.getSubRoutes(route)) {
                addRoute(subRoute);
            }
        }

        processAllTransfers();

        return builder.build();
    }

    // add raptor route for each sub route of the gtfs routes
    private void addRoute(GtfsRoutePartitioner.SubRoute subRoute) {

        // add stops of sub route that are not already added
        for (Stop stop : subRoute.getStopsSequence()) {
            if (!addedStops.contains(stop)) {
                builder.addStop(stop.getId());
                addedStops.add(stop);
            }
        }

        // add sub route as raptor route
        builder.addRoute(subRoute.getId(), subRoute.getStopsSequence().stream().map(Stop::getId).toList());

        // add trips of sub route
        for (Trip trip : subRoute.getTrips()) {
            builder.addTrip(trip.getId(), subRoute.getId());
            List<StopTime> stopTimes = trip.getStopTimes();
            for (int i = 0; i < stopTimes.size(); i++) {
                StopTime stopTime = stopTimes.get(i);
                builder.addStopTime(subRoute.getId(), trip.getId(), i, stopTime.stop().getId(),
                        stopTime.arrival().getTotalSeconds(), stopTime.departure().getTotalSeconds());
            }
        }
    }

    /**
     * Processes all types of transfers, ensuring the correct order of precedence:
     * <p>
     * <ol>
     *   <li>
     *     <b>Additional transfers:</b> These transfers have the lowest priority and are processed first.
     *   </li>
     *   <li>
     *     <b>Parent-child derived transfers:</b> If a transfer is defined between two parent stops
     *     (e.g., A to B), this method derives corresponding transfers for their child stops
     *     (e.g., A1, A2, ... to B1, B2, ...).
     *   </li>
     *   <li>
     *     <b>GTFS schedule-defined transfers:</b> Transfers explicitly defined in the GTFS schedule
     *     (e.g., A1 to B2) take the highest priority. These transfers are applied last,
     *     overwriting any transfers previously derived from parent stops.
     *   </li>
     * </ol>
     * <p>
     * The method ensures that all transfers, whether additional, derived, or explicitly defined, are handled in the
     * correct priority order.
     */
    private void processAllTransfers() {
        createAndAddTransfersFromTransferGenerators();
        processStopAndParentChildTransfers();
        addGtfsTransfersWithPrecedence();
    }

    /**
     * Create and add transfers from transfer generators.
     */
    private void createAndAddTransfersFromTransferGenerators() {
        // reverse order ensures that the lowest priority transfers are overwritten if a higher priority transfer
        // generator generated transfer or gtfs based transfer exists.
        transferGenerators.stream()
                .collect(Collectors.collectingAndThen(Collectors.toList(), list -> {
                    Collections.reverse(list);
                    return list.stream();
                }))
                .flatMap(generator -> generator.generateTransfers(addedStops).stream())
                .forEach(transfer -> builder.addTransfer(transfer.from().getId(), transfer.to().getId(),
                        transfer.duration()));
    }

    /**
     * Processes transfers for each stop and handles parent-child relationships.
     */
    private void processStopAndParentChildTransfers() {
        for (Stop stop : addedStops) {
            processParentAndChildTransfersForStop(stop);
        }
    }

    /**
     * Processes parent and child transfers for a given stop, ensuring proper precedence.
     * <p>
     * Assuming transfers A-A, A-B are defined, and both stops are parent stops with 2 children (A1, A2, B1, B2) and
     * there are also active departures on the parent stop B. For stop A1 following transfers will be derived: (A-A):
     * A1-A1, A1-A2, (A-B): A1-B, A1-B1, A1-B2
     * <p>
     * Alternatively, this method can also handle the case when parent stops have departures but no transfers specified.
     * For example if transfers A1-A2, A1-B1 are defined, following transfers for stop A may be derived: (A1-A2): A-A,
     * A-A1, A-A2, (A1-B1): A-B, A-B1, A-B2.
     *
     * @param stop the stop for which parent and child transfers are being processed
     */
    private void processParentAndChildTransfersForStop(Stop stop) {
        // this checks if parent stop is present (i.e. checks if stop in question is child stop) and if true, will
        // derive apply all parent transfers to child stop.
        stop.getParent().ifPresent(parentStop -> applyTransfersFromOtherStop(stop, parentStop));

        // alternatively, if stop in question is parent stop, all transfers from child stops to other stops should also
        // be applied to parent stop.
        for (Stop childStop : stop.getChildren()) {
            applyTransfersFromOtherStop(stop, childStop);
        }

        // this is used to make sure that all to stop children/parents are also included for transfer defined on the
        // current stop.
        addToStopChildrenTransfers(stop);
    }

    /**
     * This method gets all possible transfers from the provider stop and adds copies for the consumer stop. E.g. if A1
     * is the consumer stop, A is the provider stop and A has a transfer to B (A-B), this method will create the
     * transfer A1-B and potentially A1-B1, A1-B2,... if B has child stops.
     *
     * @param consumerStop the stop of interest where transfers should be derived for
     * @param providerStop the stop from which transfers should be derived from for the consumerStop
     */
    private void applyTransfersFromOtherStop(Stop consumerStop, Stop providerStop) {
        Collection<TransferGenerator.Transfer> transfers = expandTransfersFromStop(providerStop);
        for (TransferGenerator.Transfer transfer : transfers) {
            builder.addTransfer(consumerStop.getId(), transfer.to().getId(), transfer.duration());
        }
    }

    /**
     * Adds transfers to all children stops of all transfer destinations. E.g. if stop B has stops B1 and B2 as children
     * and stop A has transfer A-B defined, this method will newly create transfers A-B1, A-B2.
     *
     * @param stop the stop to process all to stops for
     */
    private void addToStopChildrenTransfers(Stop stop) {
        for (Transfer stopTransfer : stop.getTransfers()) {
            if (stopTransfer.getTransferType() == TransferType.MINIMUM_TIME && stopTransfer.getMinTransferTime()
                    .isPresent()) {
                for (Stop toChildStop : stopTransfer.getToStop().getChildren()) {
                    // only add new transfers if the to stop also has departures, else the raptor router does not care
                    // about this stop and the builder will throw an exception.
                    if (addedStops.contains(toChildStop)) {
                        builder.addTransfer(stop.getId(), toChildStop.getId(), stopTransfer.getMinTransferTime().get());
                    }
                }
            }
        }
    }

    /**
     * Adds transfers explicitly defined in the GTFS schedule, ensuring precedence over additional transfers.
     */
    private void addGtfsTransfersWithPrecedence() {
        for (Stop stop : addedStops) {
            for (Transfer transfer : stop.getTransfers()) {
                // only add new transfers if the to stop also has departures, else the raptor router does not care about
                // this stop and the builder will throw an exception.
                if (transfer.getTransferType() == TransferType.MINIMUM_TIME && transfer.getMinTransferTime()
                        .isPresent() && addedStops.contains(transfer.getToStop())) {
                    builder.addTransfer(stop.getId(), transfer.getToStop().getId(),
                            transfer.getMinTransferTime().get());
                }
            }
        }
    }

    /**
     * Collects all possible transfers from a given stop. This method loops over all the stops transfers and checks if
     * the "to stops" have any children and parents and adds transfers to those if needed. For example if Stop A has the
     * following transfers: A-A1 (A1 is child of A), A-A (note A has also a child A2), A-B (B has B1, B2 as children)
     * and A-B1. This method will return all the previously defined transfers: A-A1, A-A, A-B and A-B1 and derive the
     * following: A-A2, A-B2. Note: even though A-A1 and A-B1 could be derived from A-A and A-B, those are not added
     * because these were already explicitly defined.
     *
     * @param stop the stop of interest
     * @return a collection of complete transfers from stop
     */
    private Collection<TransferGenerator.Transfer> expandTransfersFromStop(Stop stop) {
        Map<Stop, TransferGenerator.Transfer> parentTransfers = new HashMap<>();
        // to ensure explicitly defined transfers take precedence, those are collected separately and applied in the
        // end, potentially overwriting other lower priority transfers
        List<TransferGenerator.Transfer> otherTransfers = new ArrayList<>();

        for (Transfer transfer : stop.getTransfers()) {
            if (transfer.getTransferType() != TransferType.MINIMUM_TIME || transfer.getMinTransferTime().isEmpty()) {
                continue;
            }
            Stop toStop = transfer.getToStop();
            // only add new transfers if the to stop also has departures, else the raptor router does not care about
            // this stop and the builder will throw an exception.
            if (addedStops.contains(toStop)) {
                otherTransfers.add(new TransferGenerator.Transfer(stop, toStop, transfer.getMinTransferTime().get()));
            }
            for (Stop childToStop : toStop.getChildren()) {
                if (addedStops.contains(childToStop)) {
                    parentTransfers.put(childToStop,
                            new TransferGenerator.Transfer(stop, childToStop, transfer.getMinTransferTime().get()));
                }
            }
        }

        // Overwrite transfers derived from children when explicit transfer declaration exists
        for (TransferGenerator.Transfer transfer : otherTransfers) {
            parentTransfers.put(transfer.to(), transfer);
        }

        return parentTransfers.values();
    }

}