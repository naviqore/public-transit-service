package ch.naviqore.service.gtfsraptor;

import ch.naviqore.gtfs.schedule.model.GtfsSchedule;
import ch.naviqore.gtfs.schedule.model.Stop;

/**
 * Represents a minimum time transfer between two stops. Is only intended to be used in the
 * {@link GtfsToRaptorConverter}, as source to provide additional generated transfers not present in the
 * {@link GtfsSchedule} schedule.
 */
public record MinimumTimeTransfer(Stop from, Stop to, int duration) {
}
