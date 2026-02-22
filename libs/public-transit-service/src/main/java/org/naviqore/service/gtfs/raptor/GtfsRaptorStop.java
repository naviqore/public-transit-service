package org.naviqore.service.gtfs.raptor;

import lombok.*;
import org.naviqore.service.Stop;
import org.naviqore.utils.spatial.GeoCoordinate;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@Getter
@ToString
@EqualsAndHashCode
public class GtfsRaptorStop implements Stop {

    private final String id;
    private final String name;
    private final GeoCoordinate coordinate;

}
