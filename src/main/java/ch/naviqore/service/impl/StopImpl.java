package ch.naviqore.service.impl;

import ch.naviqore.service.Stop;
import ch.naviqore.utils.spatial.GeoCoordinate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@Getter
@ToString
public class StopImpl implements Stop {

    private final String id;
    private final String name;
    private final GeoCoordinate location;

}
