package org.naviqore.service.config;

import lombok.Builder;
import lombok.Value;
import org.naviqore.service.TravelMode;

import java.util.EnumSet;

@Builder
@Value
public class ConnectionQueryConfig {

    @Builder.Default
    int maximumWalkingDuration = Integer.MAX_VALUE;

    @Builder.Default
    int minimumTransferDuration = 0;

    @Builder.Default
    int maximumTransferNumber = Integer.MAX_VALUE;

    @Builder.Default
    int maximumTravelTime = Integer.MAX_VALUE;

    @Builder.Default
    boolean wheelchairAccessible = false;

    @Builder.Default
    boolean bikeAllowed = false;

    @Builder.Default
    EnumSet<TravelMode> travelModes = EnumSet.allOf(TravelMode.class);

}
