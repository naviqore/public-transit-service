package org.naviqore.app.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import org.naviqore.utils.spatial.GeoCoordinate;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@EqualsAndHashCode(of = "id")
@ToString
@Getter
@Schema(description = "A stop or a station.")
public class Stop {

    @Schema(description = "ID of the stop.")
    private final String id;

    @Schema(description = "Name of the stop.")
    private final String name;

    @Schema(description = "Location of the stop, in WGS84 degrees.")
    private final GeoCoordinate coordinates;

}

