package org.naviqore.service.gtfs.raptor;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import org.naviqore.service.Stop;
import org.naviqore.utils.spatial.GeoCoordinate;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@Getter
@ToString
public class GtfsRaptorStop implements Stop {

    private final String id;
    private final String name;
    private final GeoCoordinate coordinate;

}
