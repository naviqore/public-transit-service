package ch.naviqore.service.gtfsraptor;

import ch.naviqore.gtfs.schedule.model.GtfsSchedule;
import ch.naviqore.gtfs.schedule.model.Stop;
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

    /**
     * Creates a new WalkTransferGenerator with the given WalkCalculator, minimum transfer time and maximum walking
     * distance.
     *
     * @param walkCalculator WalkCalculator to use for calculating walking times.
     * @param minimumTransferTime Minimum transfer time between stops in seconds.
     * @param maxWalkDistance Maximum walking distance between stops in meters.
     */
    public WalkTransferGenerator(WalkCalculator walkCalculator, int minimumTransferTime, int maxWalkDistance) {
        if( walkCalculator == null ) throw new IllegalArgumentException("walkCalculator is null");
        if( minimumTransferTime < 0 ) throw new IllegalArgumentException("minimumTransferTime is negative");
        if( maxWalkDistance <= 0 ) throw new IllegalArgumentException("maxWalkDistance is negative or zero");
        this.walkCalculator = walkCalculator;
        this.minimumTransferTime = minimumTransferTime;
        this.maxWalkDistance = maxWalkDistance;
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
        ConcurrentLinkedQueue<MinimumTimeTransfer> transfers = new ConcurrentLinkedQueue<>();

        log.info("Generating transfers between {} stops", stops.size());

        stops.values().parallelStream().forEach(fromStop -> {
            List<Stop> nearbyStops = schedule.getNearestStops(
                    fromStop.getCoordinate().latitude(),
                    fromStop.getCoordinate().longitude(),
                    maxWalkDistance
            );
            nearbyStops.forEach(toStop -> createTransfer(fromStop, toStop, transfers));
        });

        log.info("Generated {} transfers between {} stops", transfers.size(), stops.size());

        return new ArrayList<>(transfers);
    }

    private void createTransfer(Stop fromStop, Stop toStop, ConcurrentLinkedQueue<MinimumTimeTransfer> transfers) {
        if (!fromStop.equals(toStop)) {
            // calculate the walking time between the stops
            Walk walk = walkCalculator.calculateWalk(fromStop.getCoordinate(), toStop.getCoordinate());
            int transferDuration = Math.max(walk.duration(), minimumTransferTime);
            transfers.add(new MinimumTimeTransfer(fromStop, toStop, transferDuration));
        }
    }
}
