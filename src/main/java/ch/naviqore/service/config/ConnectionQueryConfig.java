package ch.naviqore.service.config;

import ch.naviqore.service.TravelMode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

import java.util.EnumSet;

@RequiredArgsConstructor
@Getter
@ToString
public class ConnectionQueryConfig {

    private final int maximumWalkingDuration;
    private final int minimumTransferDuration;
    private final int maximumTransferNumber;
    private final int maximumTravelTime;
    private final boolean wheelchairAccessible;
    private final boolean bikeAllowed;
    private final EnumSet<TravelMode> travelModes;

}
