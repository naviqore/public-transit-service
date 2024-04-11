package ch.naviqore.gtfs.schedule.model;

import ch.naviqore.gtfs.schedule.type.ServiceDayTime;

public record StopTime(Stop stop, Trip trip, ServiceDayTime arrival, ServiceDayTime departure) {
}
