package org.naviqore.app.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.OffsetDateTime;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@EqualsAndHashCode
@ToString
@Getter
@Schema(description = "The times a trip calls at a stop.")
public class StopTime {

    @Schema(description = "The stop.")
    private final Stop stop;

    @Schema(description = "When the trip arrives at the stop, as an ISO-8601 date-time with offset.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private final OffsetDateTime arrivalTime;

    @Schema(description = "When the trip departs from the stop, as an ISO-8601 date-time with offset.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private final OffsetDateTime departureTime;

}

