package ch.naviqore.service.gtfs.raptor;

import ch.naviqore.service.Route;
import ch.naviqore.service.TravelMode;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

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
