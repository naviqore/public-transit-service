package ch.naviqore.app.dto;

import ch.naviqore.utils.spatial.GeoCoordinate;
import lombok.*;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@EqualsAndHashCode(of = "id")
@ToString
@Getter
public class Stop {

    private final String id;
    private final String name;
    private final GeoCoordinate coordinates;

}

