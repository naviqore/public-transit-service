package org.naviqore.app.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@EqualsAndHashCode(of = "id")
@ToString
@Getter
@Schema(description = "A public transit route, also called a line.")
public class Route {

    @Schema(description = "ID of the route.")
    private final String id;

    @Schema(description = "Long name of the route; may be empty.")
    private final String name;

    @Schema(description = "Short name of the route, such as its number; may be empty.")
    private final String shortName;

    @Schema(description = "Travel mode of the route.")
    private final TravelMode travelMode;

    @Schema(description = "Description of the route type in the schedule, which may be more specific than the travel mode, e.g. Suburban Railway.")
    private final String routeTypeDescription;

}

