package ch.naviqore.raptor.router;

import java.time.LocalDate;
import java.util.Map;

/**
 * This represents a service day mask for a given day.
 * <p>
 * The service day mask holds the date it's valid for, a serviceId which can be identical for multiple days if the
 * service is the same. And a map of route ids to {@link TripMask}.
 *
 * @param serviceId the service id for the day
 * @param date      the date of the day
 * @param tripMask  a map of route ids to trip masks for the day
 */
public record RaptorDayMask(String serviceId, LocalDate date, Map<String, TripMask> tripMask) {
}