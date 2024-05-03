package ch.naviqore.gtfs.schedule;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Standard GTFS schedule file types and their corresponding file names.
 */
@RequiredArgsConstructor
@Getter
enum GtfsScheduleFile {
    // FEED_INFO("feed_info.txt", Presence.OPTIONAL),
    // ATTRIBUTIONS("attributions.txt", Presence.OPTIONAL),
    AGENCY("agency.txt", Presence.REQUIRED),
    CALENDAR("calendar.txt", Presence.CONDITIONALLY_REQUIRED),
    CALENDAR_DATES("calendar_dates.txt", Presence.CONDITIONALLY_REQUIRED),
    // FARE_ATTRIBUTES("fare_attributes.txt", Presence.OPTIONAL),
    // FARE_RULES("fare_rules.txt", Presence.OPTIONAL),
    // FREQUENCIES("frequencies.txt", Presence.OPTIONAL),
    STOPS("stops.txt", Presence.REQUIRED),
    ROUTES("routes.txt", Presence.REQUIRED),
    // SHAPES("shapes.txt", Presence.OPTIONAL),
    TRIPS("trips.txt", Presence.REQUIRED),
    STOP_TIMES("stop_times.txt", Presence.REQUIRED);
    // TRANSFERS("transfers.txt", Presence.OPTIONAL);

    private final String fileName;
    private final Presence presence;

    public enum Presence {
        REQUIRED, OPTIONAL, CONDITIONALLY_REQUIRED, CONDITIONALLY_FORBIDDEN, RECOMMENDED
    }
}
