package org.naviqore.gtfs.schedule.model;

import java.time.ZoneId;

public record Agency(String agency, String name, String url, ZoneId timezone) {
}
