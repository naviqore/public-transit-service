package ch.naviqore.service.impl.transfer;

import ch.naviqore.gtfs.schedule.model.GtfsSchedule;
import ch.naviqore.gtfs.schedule.model.Stop;
import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Log4j2
public class SameStopTransferGenerator implements TransferGenerator {

    private final int minimumTransferTime;

    /**
     * Creates a new generator
     *
     * @param minimumTransferTime minimum transfer time between two trips at the same stop in seconds.
     */
    public SameStopTransferGenerator(int minimumTransferTime) {
        if (minimumTransferTime < 0) {
            throw new IllegalArgumentException("minimumTransferTime is negative");
        }
        this.minimumTransferTime = minimumTransferTime;
    }

    @Override
    public List<TransferGenerator.Transfer> generateTransfers(GtfsSchedule schedule) {
        List<TransferGenerator.Transfer> transfers = new ArrayList<>();
        Map<String, Stop> stops = schedule.getStops();

        log.info("Generating same stop transfers for {} stops", stops.size());
        for (Stop fromStop : stops.values()) {
            transfers.add(new TransferGenerator.Transfer(fromStop, fromStop, minimumTransferTime));
        }

        return transfers;
    }
}
