package ch.naviqore.service.impl.transfergenerator;

import ch.naviqore.gtfs.schedule.model.GtfsSchedule;
import ch.naviqore.gtfs.schedule.model.Stop;
import ch.naviqore.service.impl.walkcalculator.WalkCalculator;
import ch.naviqore.service.impl.walkcalculator.Walk;
import ch.naviqore.utils.spatial.index.KDTree;
import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Implements a transfer generator that creates minimum time transfers between stops where the {@link GtfsSchedule} does
 * not provide a transfer for and the distance is within walking distance.
 */
@Log4j2
public class WalkTransferGenerator implements TransferGenerator {
    /**
     * WalkCalculator to use for calculating walking times.
     */
    private final WalkCalculator walkCalculator;

    /**
     * Minimum transfer time between stops in seconds.
     */
    private final int minimumTransferTime;

    /**
     * Maximum walking distance between stops (search radius for stops) in meters.
     */
    private final int maxWalkDistance;

    private final KDTree<Stop> spatialStopIndex;

    /**
     * Creates a new WalkTransferGenerator with the given WalkCalculator, minimum transfer time and maximum walking
     * distance.
     *
     * @param walkCalculator      WalkCalculator to use for calculating walking times.
     * @param minimumTransferTime Minimum transfer time between stops in seconds.
     * @param maxWalkDistance     Maximum walking distance between stops in meters.
     */
    public WalkTransferGenerator(WalkCalculator walkCalculator, int minimumTransferTime, int maxWalkDistance, KDTree<Stop> spatialStopIndex) {
        if (walkCalculator == null) throw new IllegalArgumentException("walkCalculator is null");
        if (minimumTransferTime < 0) throw new IllegalArgumentException("minimumTransferTime is negative");
        if (maxWalkDistance <= 0) throw new IllegalArgumentException("maxWalkDistance is negative or zero");
        if (spatialStopIndex == null) throw new IllegalArgumentException("spatialStopIndex is null");
        this.walkCalculator = walkCalculator;
        this.minimumTransferTime = minimumTransferTime;
        this.maxWalkDistance = maxWalkDistance;
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
    public List<MinimumTimeTransfer> generateTransfers(GtfsSchedule schedule) {
        Map<String, Stop> stops = schedule.getStops();

        log.info("Generating transfers between {} stops", stops.size());
        List<MinimumTimeTransfer> transfers = stops.values().parallelStream().flatMap(fromStop -> {
            List<Stop> nearbyStops = spatialStopIndex.rangeSearch(fromStop, maxWalkDistance);
            return nearbyStops.stream()
                    .filter(toStop -> !toStop.equals(fromStop))
                    .map(toStop -> createTransfer(fromStop, toStop));
        }).toList();
        log.info("Generated {} transfers between {} stops", transfers.size(), stops.size());

        return new ArrayList<>(transfers);
    }

    private MinimumTimeTransfer createTransfer(Stop fromStop, Stop toStop) {
        // calculate the walking time between the stops
        Walk walk = walkCalculator.calculateWalk(fromStop.getCoordinate(), toStop.getCoordinate());
        int transferDuration = Math.max(walk.duration(), minimumTransferTime);
        return new MinimumTimeTransfer(fromStop, toStop, transferDuration);
    }
}