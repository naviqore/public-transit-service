package org.naviqore.app.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "A ride on a trip, or a walk.")
public class Leg {

    @Schema(description = "ROUTE for a ride on a trip, WALK for a walk.")
    private final LegType type;

    @Schema(description = "Where the leg starts, in WGS84 degrees.")
    private final GeoCoordinate from;

    @Schema(description = "Where the leg ends, in WGS84 degrees.")
    private final GeoCoordinate to;

    @Schema(description = "The stop the leg starts at; absent for a walk from a location.")
    private final @Nullable Stop fromStop;

    @Schema(description = "The stop the leg ends at; absent for a walk to a location.")
    private final @Nullable Stop toStop;

    @Schema(description = "When the leg starts, as an ISO-8601 date-time with offset.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private final OffsetDateTime departureTime;

    @Schema(description = "When the leg ends, as an ISO-8601 date-time with offset.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private final OffsetDateTime arrivalTime;

    @Schema(description = "The trip ridden; absent for a walk.")
    @Nullable
    private final Trip trip;

}

