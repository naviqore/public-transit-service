package org.naviqore.service.spi;

import org.jspecify.annotations.Nullable;

import java.time.ZoneId;
import java.util.List;

/**
 * A trip of a {@link TransitSchedule}, independent of the service day it runs on.
 *
 * @param id                   the ID of the trip
 * @param headSign             the head sign of the trip, if any
 * @param routeId              the ID of the route the trip belongs to
 * @param zoneId               the time zone the stop times of the trip are given in
 * @param stopTimes            the stop times of the trip, in stop sequence order
 * @param bikesAllowed         whether bikes are allowed on the trip
 * @param wheelchairAccessible whether the trip is wheelchair accessible
 */
public record ScheduledTrip(String id, @Nullable String headSign, String routeId, ZoneId zoneId,
                            List<ScheduledStopTime> stopTimes, boolean bikesAllowed, boolean wheelchairAccessible) {
}
