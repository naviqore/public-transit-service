package org.naviqore.app.dto;

import lombok.*;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.OffsetDateTime;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@EqualsAndHashCode
@ToString
@Getter
public class StopTime {

    private final Stop stop;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private final OffsetDateTime arrivalTime;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private final OffsetDateTime departureTime;

}

