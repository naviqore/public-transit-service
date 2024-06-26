package ch.naviqore.service.impl.transfer;

import ch.naviqore.gtfs.schedule.model.GtfsSchedule;
import ch.naviqore.gtfs.schedule.model.Stop;
import ch.naviqore.service.walk.WalkCalculator;
import ch.naviqore.utils.spatial.index.KDTree;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Implements a transfer generator that creates minimum time transfers between stops where the {@link GtfsSchedule} does
 * not provide a transfer for and the distance is within walking distance.
 */
@Slf4j
public class WalkTransferGenerator implements TransferGenerator {

    private final WalkCalculator walkCalculator;
    private final int minimumTransferTime;
    private final int accessEgressTime;
    private final int searchRadius;
    private final KDTree<Stop> spatialStopIndex;

    /**
     * Creates a new WalkTransferGenerator with the given WalkCalculator, minimum transfer time and maximum beeline
     * walking distance (= search radius).
     *
     * @param walkCalculator      WalkCalculator to use for calculating walking times.
     * @param minimumTransferTime Minimum transfer time between stops in seconds, even if plain walking duration would
     *                            be shorter. Accounts for access and egress of vehicle, building, stairways, etc.
     * @param accessEgressTime    Time needed to access or egress a public transit trip.
     * @param searchRadius        Search radius in meters, the maximum beeline walking distance between stops.
     */
    public WalkTransferGenerator(WalkCalculator walkCalculator, int minimumTransferTime, int accessEgressTime,
                                 int searchRadius, KDTree<Stop> spatialStopIndex) {
        if (walkCalculator == null) throw new IllegalArgumentException("walkCalculator is null");
        if (minimumTransferTime < 0) throw new IllegalArgumentException("minimumTransferTime is negative");
        if (accessEgressTime < 0) throw new IllegalArgumentException("accessEgressTime is negative");
        if (searchRadius <= 0) throw new IllegalArgumentException("searchRadius is negative or zero");
        if (spatialStopIndex == null) throw new IllegalArgumentException("spatialStopIndex is null");
        this.walkCalculator = walkCalculator;
        this.minimumTransferTime = minimumTransferTime;
        this.accessEgressTime = accessEgressTime;
        this.searchRadius = searchRadius;
        this.spatialStopIndex = spatialStopIndex;
    }

    /**
     * Generates minimum time transfers between stops in the GTFS schedule, when no transfer is provided by the
     * {@link GtfsSchedule} and the stops are within walking distance. Uses the {@link WalkCalculator} to calculate
     * walking times between stops.
     *
     * @param schedule GTFS schedule to generate transfers for.
     * @return List of minimum time transfers.
     */
    @Override
    public List<TransferGenerator.Transfer> generateTransfers(GtfsSchedule schedule) {
        Map<String, Stop> stops = schedule.getStops();

        log.info("Generating transfers between {} stops", stops.size());
        List<TransferGenerator.Transfer> transfers = stops.values().parallelStream().flatMap(fromStop -> {
            List<Stop> nearbyStops = spatialStopIndex.rangeSearch(fromStop, searchRadius);
            return nearbyStops.stream()
                    .filter(toStop -> !toStop.equals(fromStop))
                    .map(toStop -> createTransfer(fromStop, toStop));
        }).toList();
        log.info("Generated {} transfers between {} stops", transfers.size(), stops.size());

        return new ArrayList<>(transfers);
    }

    private TransferGenerator.Transfer createTransfer(Stop fromStop, Stop toStop) {
        // calculate the walking time between the stops
        WalkCalculator.Walk walk = walkCalculator.calculateWalk(fromStop.getCoordinate(), toStop.getCoordinate());
        // get total transfer duration by adding access and egress time (twice) to the walk duration
        // and taking the maximum value between this total and the minimum transfer time.
        int transferDuration = Math.max(walk.duration() + 2 * accessEgressTime, minimumTransferTime);
        return new TransferGenerator.Transfer(fromStop, toStop, transferDuration);
    }
}
