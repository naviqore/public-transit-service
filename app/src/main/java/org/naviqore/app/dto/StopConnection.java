package org.naviqore.app.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import org.jspecify.annotations.Nullable;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.OffsetDateTime;

/**
 * This class represents a connection between a stop and a spawn source (iso-line) in a transportation network. It
 * contains information about the stop, the leg closest to the target stop, and the connection itself.
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@Getter
@EqualsAndHashCode
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "The connection of an isoline to a stop, or from it for timeType ARRIVAL.")
public class StopConnection {

    @Schema(description = "The stop.")
    private final Stop stop;

    @Schema(description = "When the connection leaves the source, or the stop for timeType ARRIVAL, as an ISO-8601 date-time with offset.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private final OffsetDateTime departureTime;

    @Schema(description = "When the connection reaches the stop, or the source for timeType ARRIVAL, as an ISO-8601 date-time with offset.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private final OffsetDateTime arrivalTime;

    @Schema(description = "Number of transfers of the connection.")
    private final int transfers;

    @Schema(description = "The leg that reaches the stop, or leaves it for timeType ARRIVAL; of a ride, only the part from the stop before (to the stop after).")
    private final Leg connectingLeg;

    @Schema(description = "The whole connection; only if detailed.")
    @Nullable
    private final Connection connection;

}