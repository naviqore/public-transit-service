package ch.naviqore.service.gtfsraptor;

import ch.naviqore.gtfs.schedule.model.GtfsSchedule;
import ch.naviqore.gtfs.schedule.model.Stop;
import ch.naviqore.gtfs.schedule.type.TransferType;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Implements a transfer generator that creates minimum time transfers between stops where the {@link GtfsSchedule} does
 * not provide a transfer for and the distance is within walking distance.
 */
@Log4j2
@RequiredArgsConstructor
public class SimpleTransferGenerator implements TransferGenerator {

    /**
     * WalkCalculator to use for calculating walking times.
     */
    private final WalkCalculator walkCalculator;

    /**
     * Minimum transfer time between stops at the same station (no walking required) in seconds.
     */
    private final int sameStationTransferTime;

    /**
     * Maximum walking distance between stops (search radius for stops) in meters.
     */
    private final int maxWalkDistance;

    /**
     * Generates minimum time transfers between stops in the GTFS schedule, when no transfer is provided by the
     * {@link GtfsSchedule} and the stops are within walking distance. Uses the {@link WalkCalculator} to calculate
     * walking times between stops.
     *
     * @param schedule GTFS schedule to generate transfers for.
     * @return List of minimum time transfers.
     */
    @Override
    public List<MinimumTimeTransfer> generateTransfers(GtfsSchedule schedule) {

        Map<String, Stop> stops = schedule.getStops();
        ConcurrentLinkedQueue<MinimumTimeTransfer> transfers = new ConcurrentLinkedQueue<>();

        log.info("Generating transfers for {} stops", stops.size());

        stops.values().parallelStream().forEach(fromStop -> {
            List<Stop> nearbyStops = schedule.getNearestStops(
                    fromStop.getCoordinate().latitude(),
                    fromStop.getCoordinate().longitude(),
                    maxWalkDistance
            );
            nearbyStops.forEach(toStop -> maybeCreateTransfer(fromStop, toStop, transfers));
        });

        log.info("Generated {} transfers between {} stops", transfers.size(), stops.size());

        return new ArrayList<>(transfers);
    }

    private void maybeCreateTransfer(Stop fromStop, Stop toStop, ConcurrentLinkedQueue<MinimumTimeTransfer> transfers) {
        // if there's already a minimum time transfer between these stops, don't create another one
        if (fromStop.getTransfers()
                .stream()
                .anyMatch(t -> t.getFromStop().equals(fromStop) && t.getToStop().equals(toStop) && t.getTransferType()
                        .equals(TransferType.MINIMUM_TIME))) {
            return;
        }

        // if the stops are the same, create a minimum time transfer
        if (fromStop.equals(toStop)) {
            transfers.add(new MinimumTimeTransfer(fromStop, toStop, sameStationTransferTime));
        } else {
            // calculate the walking time between the stops
            Walk walk = walkCalculator.calculateWalk(fromStop.getCoordinate(), toStop.getCoordinate());
            int transferDuration = Math.max(walk.duration(), sameStationTransferTime);
            transfers.add(new MinimumTimeTransfer(fromStop, toStop, transferDuration));
        }
    }
}
