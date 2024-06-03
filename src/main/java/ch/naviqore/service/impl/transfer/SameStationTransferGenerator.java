package ch.naviqore.service.impl.transfer;

import ch.naviqore.gtfs.schedule.model.GtfsSchedule;
import ch.naviqore.gtfs.schedule.model.Stop;
import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Log4j2
public class SameStationTransferGenerator implements TransferGenerator {

    /**
     * Minimum transfer time between stops at the same station (no walking required) in seconds.
     */
    private final int sameStationTransferTime;

    /**
     * Creates a new SameStationTransferGenerator with the given minimum transfer time between stops at the same station.
     *
     * @param sameStationTransferTime Minimum transfer time between stops at the same station in seconds.
     */
    public SameStationTransferGenerator(int sameStationTransferTime) {
        if (sameStationTransferTime < 0) {
            throw new IllegalArgumentException("sameStationTransferTime is negative");
        }
        this.sameStationTransferTime = sameStationTransferTime;
    }

    @Override
    public List<MinimumTimeTransfer> generateTransfers(GtfsSchedule schedule) {
        List<MinimumTimeTransfer> transfers = new ArrayList<>();
        Map<String, Stop> stops = schedule.getStops();
        log.info("Generating same station transfers for {} stops", stops.size());
        for (Stop fromStop : stops.values()) {
            transfers.add(new MinimumTimeTransfer(fromStop, fromStop, sameStationTransferTime));
        }
        return transfers;
    }
}
