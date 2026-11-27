package org.naviqore.app.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * A connection: its legs, and the time it leaves the origin and reaches the destination, which at a location include
 * the access and egress time and a walk too short to be a leg of its own.
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@EqualsAndHashCode
@ToString
@Getter
@Schema(description = "A connection from an origin to a destination.")
public class Connection {

    @Schema(description = "The legs in the order they are traveled: rides and walks.")
    private final List<Leg> legs;

    @Schema(description = "When the connection leaves its origin, as an ISO-8601 date-time with offset: from a location, before the walk to the first stop and the access time, also if the walk is too short to be a leg.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private final OffsetDateTime departureTime;

    @Schema(description = "When the connection reaches its destination, as an ISO-8601 date-time with offset: at a location, after the egress time and the walk from the last stop, also if the walk is too short to be a leg.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private final OffsetDateTime arrivalTime;

}
