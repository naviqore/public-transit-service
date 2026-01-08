package org.naviqore.raptor.router;

import org.naviqore.raptor.TimeType;

import java.time.*;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;

class DateTimeUtils {

    static OffsetDateTime getReferenceDateTime(Map<?, OffsetDateTime> sourceStops, TimeType timeType) {
        if (timeType == TimeType.DEPARTURE) {
            // get minimum departure time
            return sourceStops.values().stream().min(Comparator.naturalOrder()).orElseThrow();
        } else {
            // get maximum arrival time
            return sourceStops.values().stream().max(Comparator.naturalOrder()).orElseThrow();
        }
    }

    static Map<String, Integer> mapOffsetDateTimeToTimestamp(Map<String, OffsetDateTime> sourceStops,
                                                             LocalDate referenceDate) {
        return sourceStops.entrySet()
                .stream()
                .map(e -> Map.entry(e.getKey(), convertToTimestamp(e.getValue(), referenceDate)))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * Converts internal integer back to OffsetDateTime (Defaults to UTC).
     */
    static OffsetDateTime convertToOffsetDateTime(int timestamp, LocalDate referenceDate) {
        return referenceDate.atStartOfDay(ZoneOffset.UTC).plusSeconds(timestamp).toOffsetDateTime();
    }

    /**
     * Converts OffsetDateTime to absolute seconds from UTC Midnight of the reference date.
     */
    static int convertToTimestamp(OffsetDateTime dateTime, LocalDate referenceDate) {
        return (int) Duration.between(referenceDate.atStartOfDay(ZoneOffset.UTC), dateTime).getSeconds();
    }

    /**
     * Calculates the offset in seconds to convert "GTFS Local Seconds" to "UTC Seconds" relative to the Reference
     * Date's UTC Noon (after potential DST change).
     * <p>
     * Example (Zurich Winter UTC+1): Returns -3600.
     *
     * @param date   The service date.
     * @param zoneId The time zone of the route.
     * @return The offset in seconds (can be negative).
     */
    static int calculateUtcOffset(LocalDate date, ZoneId zoneId) {
        return -zoneId.getRules().getOffset(LocalDateTime.of(date, LocalTime.NOON)).getTotalSeconds();
    }

}