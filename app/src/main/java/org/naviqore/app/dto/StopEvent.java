package org.naviqore.app.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@EqualsAndHashCode
@ToString
@Getter
@Schema(description = "A departure or an arrival of a trip at a stop.")
public class StopEvent {

    @Schema(description = "The times of the trip at the stop.")
    private final StopTime stopTime;

    @Schema(description = "The trip, with all its stop times.")
    private final Trip trip;

}

