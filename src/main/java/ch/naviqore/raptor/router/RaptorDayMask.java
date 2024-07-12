package ch.naviqore.raptor.router;

import java.time.LocalDate;
import java.util.Map;

/**
 * This represents a service day mask for a given day.
 * <p>
 * The service day mask contains information about earliest and latest stop times for a given day. Holds a serviceId
 * which can be identical for multiple days if the service is the same. And a map of route ids to {@link TripMask}.
 *
 * @param serviceId        the service id for the day
 * @param date             the date of the day
 * @param earliestTripTime the earliest trip time for the day (in seconds relative to the mask date)
 * @param latestTripTime   the latest trip time for the day (in seconds relative to the mask date)
 * @param tripMask         a map of route ids to trip masks for the day
 */
public record RaptorDayMask(String serviceId, LocalDate date, int earliestTripTime, int latestTripTime,
                            Map<String, TripMask> tripMask) {
}