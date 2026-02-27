package org.naviqore.raptor.router;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.naviqore.raptor.TimeType;

import java.time.*;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Centralized converter for date and time calculations within the RAPTOR router. Handles the mapping between external
 * OffsetDateTime and internal integer UTC seconds.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
class DateTimeConverter {

    /**
     * Determines the reference datetime to be used as a query anchor.
     */
    static OffsetDateTime getReference(Map<?, OffsetDateTime> sourceStops, TimeType timeType) {
        if (timeType == TimeType.DEPARTURE) {
            // get minimum departure time
            return sourceStops.values().stream().min(Comparator.naturalOrder()).orElseThrow();
        } else {
            // get maximum arrival time
            return sourceStops.values().stream().max(Comparator.naturalOrder()).orElseThrow();
        }
    }

    /**
     * Maps a collection of stop datetime objects to internal seconds relative to the reference date.
     */
    static Map<String, Integer> mapToUtcSeconds(Map<String, OffsetDateTime> sourceStops, LocalDate referenceDate) {
        return sourceStops.entrySet()
                .stream()
                .map(e -> Map.entry(e.getKey(), toUtcSeconds(e.getValue(), referenceDate)))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * Converts an OffsetDateTime to absolute seconds from UTC Midnight of the reference date. The referenceDate defines
     * the "zero" point for the internal integer timeline.
     */
    static int toUtcSeconds(OffsetDateTime dateTime, LocalDate referenceDate) {
        long referenceDayStartEpoch = referenceDate.atStartOfDay(ZoneOffset.UTC).toEpochSecond();
        return (int) (dateTime.toEpochSecond() - referenceDayStartEpoch);
    }

    /**
     * Converts internal UTC seconds back to an OffsetDateTime using a specific ZoneId. This preserves the correct local
     * time and offset for a specific location or agency.
     *
     * @param utcSeconds    seconds relative to reference date UTC midnight
     * @param referenceDate the reference date used as the anchor for the query
     * @param zoneId        the target time zone for the resulting datetime
     * @return OffsetDateTime correctly adjusted to the target zone
     */
    static OffsetDateTime toOffsetDateTime(int utcSeconds, LocalDate referenceDate, ZoneId zoneId) {
        return referenceDate.atStartOfDay(ZoneOffset.UTC)
                .plusSeconds(utcSeconds)
                .toInstant()
                .atZone(zoneId)
                .toOffsetDateTime();
    }

    /**
     * Calculates the offset in seconds required to convert local wall-clock seconds to UTC seconds. Uses UTC Noon of
     * the given date to ensure a stable offset, avoiding DST transition ambiguity.
     * <p>
     * Example: Zurich (UTC+1) in Winter returns -3600.
     *
     * @param date   the service date
     * @param zoneId the local time zone
     * @return the offset in seconds (can be negative)
     */
    static int getLocalToUtcOffset(LocalDate date, ZoneId zoneId) {
        return -zoneId.getRules().getOffset(LocalDateTime.of(date, LocalTime.NOON)).getTotalSeconds();
    }

}