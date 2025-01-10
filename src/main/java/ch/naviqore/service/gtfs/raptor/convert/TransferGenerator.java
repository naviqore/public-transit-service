package ch.naviqore.service.gtfs.raptor.convert;

import ch.naviqore.gtfs.schedule.model.GtfsSchedule;
import ch.naviqore.gtfs.schedule.model.Stop;

import java.util.Collection;
import java.util.List;

/**
 * Abstracts the generation of transfers between stops from a GTFS schedule.
 * <p>
 * Is only intended to be used in the {@link GtfsToRaptorConverter}, as source to provide additional generated transfers
 * not present in the {@link GtfsSchedule} schedule.
 */
public interface TransferGenerator {

    /**
     * Generates minimum time transfers between stops in the GTFS schedule.
     *
     * @param stops Stops of the GTFS schedule to generate transfers for.
     * @return List of minimum time transfers.
     */
    List<Transfer> generateTransfers(Collection<Stop> stops);

    /**
     * Represents a minimum time transfer between two stops.
     */
    record Transfer(Stop from, Stop to, int duration) {
    }
}
