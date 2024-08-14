package ch.naviqore.service.gtfs.raptor;

import ch.naviqore.service.Route;
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
    private final String routeType;
    private final String Agency;

}