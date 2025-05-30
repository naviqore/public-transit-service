package org.naviqore.raptor.router;

import org.naviqore.raptor.TimeType;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;

class DateTimeUtils {

    static LocalDateTime getReferenceDate(Map<?, LocalDateTime> sourceStops, TimeType timeType) {
        if (timeType == TimeType.DEPARTURE) {
            // get minimum departure time
            return sourceStops.values().stream().min(Comparator.naturalOrder()).orElseThrow();
        } else {
            // get maximum arrival time
            return sourceStops.values().stream().max(Comparator.naturalOrder()).orElseThrow();
        }
    }

    static Map<String, Integer> mapLocalDateTimeToTimestamp(Map<String, LocalDateTime> sourceStops,
                                                            LocalDate referenceDate) {
        return sourceStops.entrySet()
                .stream()
                .map(e -> Map.entry(e.getKey(), convertToTimestamp(e.getValue(), referenceDate)))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    static LocalDateTime convertToLocalDateTime(int timestamp, LocalDate referenceDate) {
        return referenceDate.atStartOfDay().plusSeconds(timestamp);
    }

    static int convertToTimestamp(LocalDateTime localDateTime, LocalDate referenceDate) {
        return (int) Duration.between(referenceDate.atStartOfDay(), localDateTime).getSeconds();
    }

}
