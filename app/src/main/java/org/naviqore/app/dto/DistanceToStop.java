package org.naviqore.app.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@EqualsAndHashCode
@ToString
@Getter
@Schema(description = "A stop within the search radius of a location.")
public class DistanceToStop {

    @Schema(description = "The stop, or its station if it has one.")
    private final Stop stop;

    @Schema(description = "Beeline distance from the location to the stop, in meters.")
    private final double distance;

}

