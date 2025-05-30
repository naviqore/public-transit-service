package org.naviqore.service.gtfs.raptor;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import org.naviqore.service.Route;
import org.naviqore.service.TravelMode;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@Getter
@ToString
public class GtfsRaptorRoute implements Route {

    private final String id;
    private final String name;
    private final String shortName;
    private final TravelMode routeType;
    private final String routeTypeDescription;
    private final String Agency;

}
