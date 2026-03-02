package org.naviqore.app.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import org.jspecify.annotations.Nullable;
import org.naviqore.utils.spatial.GeoCoordinate;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.OffsetDateTime;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@EqualsAndHashCode
@ToString
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Leg {

    private final LegType type;
    private final GeoCoordinate from;
    private final GeoCoordinate to;
    private final Stop fromStop;
    private final Stop toStop;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private final OffsetDateTime departureTime;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private final OffsetDateTime arrivalTime;
    @Nullable
    private final Trip trip;

}

