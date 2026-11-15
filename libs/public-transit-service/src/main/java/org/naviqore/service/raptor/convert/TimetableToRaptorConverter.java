package org.naviqore.service.raptor.convert;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.naviqore.raptor.router.RaptorConfig;
import org.naviqore.raptor.router.RaptorRouter;
import org.naviqore.raptor.router.RaptorRouterBuilder;
import org.naviqore.raptor.router.TripTransfer;
import org.naviqore.timetable.RoutePartitioner;
import org.naviqore.timetable.model.*;
import org.naviqore.timetable.type.TransferType;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Maps timetable to RAPTOR
 * <p>
 * For each sub-route in a timetable route a route in RAPTOR is created, whose trips are boarded and left only where
 * they pick up and set down passengers. The transfers of the timetable for all trips become the walks between their
 * stops, or the same stop transfer time of their stop: a minimum time transfer takes its time, a timed transfer none,
 * and a transfer that is not possible refuses them. The transfers for particular routes or trips become trip transfers
 * of RAPTOR between the sub-routes of their routes (see {@link TripTransfer}). A transfer for a station applies to its
 * stops, unless one for the stops themselves does.
 *
 * @author munterfi
 */
@Slf4j
public class TimetableToRaptorConverter {

    private final Set<Stop> addedStops = new HashSet<>();
    private final Timetable schedule;
    private final List<TransferGenerator> transferGenerators;

    private final RoutePartitioner partitioner;
    private final RaptorRouterBuilder builder;

    public TimetableToRaptorConverter(RaptorConfig config, Timetable schedule) {
        this(config, schedule, List.of());
    }

    public TimetableToRaptorConverter(RaptorConfig config, Timetable schedule,
                                      List<TransferGenerator> transferGenerators) {
        this.schedule = schedule;
        this.transferGenerators = transferGenerators;
        this.partitioner = new RoutePartitioner(schedule);
        this.builder = RaptorRouter.builder(config);
    }

    /**
     * Gets the time a transfer for all trips gives the walk between its stops, or the change at its stop: its minimum
     * time, or none for a timed transfer, whose departing trip waits for the arriving one. Empty for the other types.
     */
    private static Optional<Integer> getStopTransferTime(Transfer transfer) {
        if (!isForAllTrips(transfer)) {
            return Optional.empty();
        }
        return switch (transfer.getTransferType()) {
            case MINIMUM_TIME -> transfer.getMinTransferTime();
            case TIMED -> Optional.of(0);
            default -> Optional.empty();
        };
    }

    /**
     * Whether a transfer for all trips is defined between two stops themselves, not derived from their stations.
     */
    private static boolean hasStopTransfer(Stop fromStop, Stop toStop) {
        for (Transfer transfer : fromStop.getTransfers()) {
            if (transfer.getToStop().equals(toStop) && isForAllTrips(transfer)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a transfer applies to all trips between its stops, not to particular routes or trips.
     */
    private static boolean isForAllTrips(Transfer transfer) {
        return transfer.getFromRoute().isEmpty() && transfer.getToRoute().isEmpty() && transfer.getFromTrip()
                .isEmpty() && transfer.getToTrip().isEmpty();
    }

    public RaptorRouter run() {
        log.info("Converting {} trips from timetable to RAPTOR data model", schedule.getTrips().size());

        for (Route route : schedule.getRoutes().values()) {
            for (RoutePartitioner.SubRoute subRoute : partitioner.getSubRoutes(route)) {
                addRoute(subRoute);
            }
        }

        processAllTransfers();
        refuseTransfers();
        addTripTransfers();

        return builder.build();
    }

    // add raptor route for each sub route of the timetable routes
    private void addRoute(RoutePartitioner.SubRoute subRoute) {

        // add stops of sub route that are not already added
        for (Stop stop : subRoute.getStopsSequence()) {
            if (!addedStops.contains(stop)) {
                builder.addStop(stop.getId());
                addedStops.add(stop);
            }
        }

        // add sub route as raptor route
        builder.addRoute(subRoute.getId(), subRoute.getRoute().getAgency().timezone(),
                subRoute.getStopsSequence().stream().map(Stop::getId).toList());

        // add trips of sub route
        for (Trip trip : subRoute.getTrips()) {
            builder.addTrip(trip.getId(), subRoute.getId());
            List<? extends StopTime> stopTimes = trip.getStopTimes();
            for (int i = 0; i < stopTimes.size(); i++) {
                StopTime stopTime = stopTimes.get(i);
                builder.addStopTime(subRoute.getId(), trip.getId(), i, stopTime.stop().getId(),
                        stopTime.arrival().getTotalSeconds(), stopTime.departure().getTotalSeconds(),
                        stopTime.pickupType().isPossible(), stopTime.dropOffType().isPossible());
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
     *     <b>Timetable-defined transfers:</b> Transfers explicitly defined in the timetable
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
        addTimetableTransfersWithPrecedence();
    }

    /**
     * Create and add transfers from transfer generators.
     */
    private void createAndAddTransfersFromTransferGenerators() {
        // reverse order ensures that the lowest priority transfers are overwritten if a higher priority transfer
        // generator generated transfer or timetable based transfer exists.
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
            Optional<Integer> duration = getStopTransferTime(stopTransfer);
            if (duration.isPresent()) {
                for (Stop toChildStop : stopTransfer.getToStop().getChildren()) {
                    // only add new transfers if the to stop also has departures, else the raptor router does not care
                    // about this stop and the builder will throw an exception.
                    if (addedStops.contains(toChildStop)) {
                        builder.addTransfer(stop.getId(), toChildStop.getId(), duration.get());
                    }
                }
            }
        }
    }

    /**
     * Adds transfers explicitly defined in the timetable, ensuring precedence over additional transfers.
     */
    private void addTimetableTransfersWithPrecedence() {
        for (Stop stop : addedStops) {
            for (Transfer transfer : stop.getTransfers()) {
                Optional<Integer> duration = getStopTransferTime(transfer);
                // only add new transfers if the to stop also has departures, else the raptor router does not care about
                // this stop and the builder will throw an exception.
                if (duration.isPresent() && addedStops.contains(transfer.getToStop())) {
                    builder.addTransfer(stop.getId(), transfer.getToStop().getId(), duration.get());
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
            Optional<Integer> duration = getStopTransferTime(transfer);
            if (duration.isEmpty()) {
                continue;
            }
            Stop toStop = transfer.getToStop();
            // only add new transfers if the to stop also has departures, else the raptor router does not care about
            // this stop and the builder will throw an exception.
            if (addedStops.contains(toStop)) {
                otherTransfers.add(new TransferGenerator.Transfer(stop, toStop, duration.get()));
            }
            for (Stop childToStop : toStop.getChildren()) {
                if (addedStops.contains(childToStop)) {
                    parentTransfers.put(childToStop, new TransferGenerator.Transfer(stop, childToStop, duration.get()));
                }
            }
        }

        // Overwrite transfers derived from children when explicit transfer declaration exists
        for (TransferGenerator.Transfer transfer : otherTransfers) {
            parentTransfers.put(transfer.to(), transfer);
        }

        return parentTransfers.values();
    }

    /**
     * Gets the stops in RAPTOR a stop of a transfer stands for: the stop, and the stops of a station.
     */
    private List<Stop> getRaptorStops(Stop stop) {
        List<Stop> stops = new ArrayList<>();
        if (addedStops.contains(stop)) {
            stops.add(stop);
        }
        for (Stop child : stop.getChildren()) {
            if (addedStops.contains(child)) {
                stops.add(child);
            }
        }
        return stops;
    }

    /**
     * Refuses the transfers that a transfer for all trips does not allow: at its stop, or between its two stops. One
     * for a station refuses them between its stops, except where a transfer for the stops themselves is defined.
     */
    private void refuseTransfers() {
        for (Stop stop : schedule.getStops().values()) {
            for (Transfer transfer : stop.getTransfers()) {
                if (!isForAllTrips(transfer) || transfer.getTransferType() != TransferType.NOT_POSSIBLE) {
                    continue;
                }
                for (Stop fromStop : getRaptorStops(transfer.getFromStop())) {
                    for (Stop toStop : getRaptorStops(transfer.getToStop())) {
                        boolean derived = !fromStop.equals(transfer.getFromStop()) || !toStop.equals(
                                transfer.getToStop());
                        if (!derived || !hasStopTransfer(fromStop, toStop)) {
                            builder.refuseTransfers(fromStop.getId(), toStop.getId());
                        }
                    }
                }
            }
        }
    }

    /**
     * Adds the transfers for particular routes or trips as trip transfers of RAPTOR. Those derived from stations are
     * added first, so that a transfer for the stops themselves overrides them.
     */
    private void addTripTransfers() {
        int count = addTripTransfers(true) + addTripTransfers(false);
        if (count > 0) {
            log.info("Added {} trip transfers for particular routes or trips", count);
        }
    }

    /**
     * Adds the trip transfers derived from stations, or those between the stops of the transfers themselves.
     *
     * @return the number of trip transfers added.
     */
    private int addTripTransfers(boolean derivedFromStations) {
        int count = 0;
        for (Stop stop : schedule.getStops().values()) {
            for (Transfer transfer : stop.getTransfers()) {
                if (isForAllTrips(transfer)) {
                    continue;
                }
                TripTransfer.Type type = switch (transfer.getTransferType()) {
                    case MINIMUM_TIME, TIMED -> TripTransfer.Type.MINIMUM_TIME;
                    case NOT_POSSIBLE -> TripTransfer.Type.NOT_POSSIBLE;
                    case IN_SEAT -> TripTransfer.Type.IN_SEAT;
                    case RECOMMENDED, IN_SEAT_FORBIDDEN -> TripTransfer.Type.STOP_TIME;
                };
                // a timed transfer takes no time: the departing trip waits for the arriving one
                int duration = transfer.getTransferType() == TransferType.MINIMUM_TIME ? transfer.getMinTransferTime()
                        .orElseThrow() : 0;
                String fromTripId = transfer.getFromTrip().map(Trip::getId).orElse(null);
                String toTripId = transfer.getToTrip().map(Trip::getId).orElse(null);
                for (Stop fromStop : getRaptorStops(transfer.getFromStop())) {
                    for (Stop toStop : getRaptorStops(transfer.getToStop())) {
                        boolean derived = !fromStop.equals(transfer.getFromStop()) || !toStop.equals(
                                transfer.getToStop());
                        if (derived != derivedFromStations) {
                            continue;
                        }
                        for (String fromRouteId : getRaptorRouteIds(transfer.getFromRoute(), fromTripId, fromStop)) {
                            for (String toRouteId : getRaptorRouteIds(transfer.getToRoute(), toTripId, toStop)) {
                                builder.addTripTransfer(
                                        new TripTransfer(fromStop.getId(), fromRouteId, fromTripId, toStop.getId(),
                                                toRouteId, toTripId, type, duration));
                                count++;
                            }
                        }
                    }
                }
            }
        }
        return count;
    }

    /**
     * Gets the routes in RAPTOR a side of a transfer names at a stop: none for a trip, whose route RAPTOR knows, or for
     * no route; else the sub-routes of the route that call at the stop.
     */
    private List<@Nullable String> getRaptorRouteIds(Optional<? extends Route> route, @Nullable String tripId,
                                                     Stop stop) {
        if (tripId != null || route.isEmpty()) {
            return Collections.singletonList(null);
        }
        return partitioner.getSubRoutes(route.get())
                .stream()
                .filter(subRoute -> subRoute.getStopsSequence().contains(stop))
                .map(RoutePartitioner.SubRoute::getId)
                .toList();
    }

}
