package ch.naviqore.gtfs.schedule;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Standard GTFS schedule file types and their corresponding file names.
 */
@RequiredArgsConstructor
@Getter
public enum GtfsScheduleFile {
    AGENCY("agency.txt"),
    CALENDAR("calendar.txt"),
    CALENDAR_DATES("calendar_dates.txt"),
    // FARE_ATTRIBUTES("fare_attributes.txt"),
    // FARE_RULES("fare_rules.txt"),
    // FREQUENCIES("frequencies.txt"),
    STOPS("stops.txt"),
    ROUTES("routes.txt"),
    SHAPES("shapes.txt"),
    TRIPS("trips.txt"),
    STOP_TIMES("stop_times.txt");

    private final String fileName;
}
