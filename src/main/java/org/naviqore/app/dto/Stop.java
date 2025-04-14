package org.naviqore.app.dto;

import lombok.*;
import org.naviqore.utils.spatial.GeoCoordinate;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@EqualsAndHashCode(of = "id")
@ToString
@Getter
public class Stop {

    private final String id;
    private final String name;
    private final GeoCoordinate coordinates;

}

