package org.naviqore.service.raptor.convert;

import org.naviqore.timetable.model.Stop;
import org.naviqore.timetable.model.Timetable;

import java.util.Collection;
import java.util.List;

/**
 * Abstracts the generation of transfers between stops from a timetable.
 * <p>
 * Is only intended to be used in the {@link TimetableToRaptorConverter}, as source to provide additional generated
 * transfers not present in the {@link Timetable}.
 */
public interface TransferGenerator {

    /**
     * Generates minimum time transfers between stops in the timetable.
     *
     * @param stops Stops of the timetable to generate transfers for.
     * @return List of minimum time transfers.
     */
    List<Transfer> generateTransfers(Collection<Stop> stops);

    /**
     * Represents a minimum time transfer between two stops.
     */
    record Transfer(Stop from, Stop to, int duration) {
    }
}
