package ch.naviqore.service.gtfs.raptor;

import ch.naviqore.service.Route;
import ch.naviqore.service.StopTime;
import ch.naviqore.service.Trip;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

import java.util.List;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@Getter
@ToString
public class GtfsRaptorTrip implements Trip {

    private final String id;
    private final String headSign;
    private final Route route;
    private final List<StopTime> stopTimes;

}
