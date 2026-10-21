package org.naviqore.service.spi;

/**
 * A stop time of a {@link ScheduledTrip}, in seconds relative to the service day (see {@link TransitSchedule}).
 *
 * @param tripId    the ID of the trip
 * @param stopId    the ID of the stop
 * @param arrival   the arrival time at the stop
 * @param departure the departure time from the stop
 */
public record ScheduledStopTime(String tripId, String stopId, int arrival, int departure) {
}
