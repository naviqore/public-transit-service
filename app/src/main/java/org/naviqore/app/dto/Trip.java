package org.naviqore.app.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import org.jspecify.annotations.Nullable;

import java.util.List;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@EqualsAndHashCode
@ToString
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "A trip: a run of a vehicle along a route.")
public class Trip {

    @Schema(description = "The destination shown on the vehicle; may be empty.")
    private final String headSign;

    @Schema(description = "The route of the trip.")
    private final Route route;

    @Schema(description = "The stop times of the trip, in the order of its stops; absent in an isoline that is not detailed.")
    @Nullable
    private final List<StopTime> stopTimes;

    @Schema(description = "Whether the trip allows bikes; false if the schedule does not tell, see GET /schedule.")
    private final boolean bikeAllowed;

    @Schema(description = "Whether the trip is accessible by wheelchair; false if the schedule does not tell, see GET /schedule.")
    private final boolean wheelchairAccessible;

}

