package ch.naviqore.service.impl.transfergenerator;

import ch.naviqore.gtfs.schedule.model.GtfsSchedule;
import ch.naviqore.service.impl.GtfsToRaptorConverter;

import java.util.List;

/**
 * Abstracts the generation of transfers between stops from a GTFS schedule.
 */
public interface TransferGenerator {

    /**
     * Generates minimum time transfers between stops in the GTFS schedule, which can be added to the
     * {@link GtfsToRaptorConverter} .
     *
     * @param schedule GTFS schedule to generate transfers for.
     * @return List of minimum time transfers.
     */
    List<MinimumTimeTransfer> generateTransfers(GtfsSchedule schedule);
}
