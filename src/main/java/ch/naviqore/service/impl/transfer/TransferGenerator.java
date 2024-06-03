package ch.naviqore.service.impl.transfer;

import ch.naviqore.gtfs.schedule.model.GtfsSchedule;
import ch.naviqore.gtfs.schedule.model.Stop;
import ch.naviqore.service.impl.convert.GtfsToRaptorConverter;

import java.util.List;

/**
 * Abstracts the generation of transfers between stops from a GTFS schedule.
 */
public interface TransferGenerator {

    /**
     * Generates minimum time transfers between stops in the GTFS schedule, which can be added to the
     * {@link GtfsToRaptorConverter}.
     *
     * @param schedule GTFS schedule to generate transfers for.
     * @return List of minimum time transfers.
     */
    List<Transfer> generateTransfers(GtfsSchedule schedule);

    /**
     * Represents a minimum time transfer between two stops. Is only intended to be used in the
     * {@link GtfsToRaptorConverter}, as source to provide additional generated transfers not present in the
     * {@link GtfsSchedule} schedule.
     */
    record Transfer(Stop from, Stop to, int duration) {
    }
}
