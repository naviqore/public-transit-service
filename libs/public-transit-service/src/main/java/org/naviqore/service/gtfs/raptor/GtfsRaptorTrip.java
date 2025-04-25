package org.naviqore.service.gtfs.raptor;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import org.naviqore.service.Route;
import org.naviqore.service.StopTime;
import org.naviqore.service.Trip;

import java.util.List;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@Getter
@ToString
public class GtfsRaptorTrip implements Trip {

    private final String id;
    private final String headSign;
    private final Route route;
    private final List<StopTime> stopTimes;
    private final boolean bikesAllowed;
    private final boolean wheelchairAccessible;

}
