package org.naviqore.service.config;

import lombok.Builder;
import lombok.Value;
import org.naviqore.service.TravelMode;

import java.util.EnumSet;

@Builder
@Value
public class ConnectionQueryConfig {

    @Builder.Default
    int maximumWalkDuration = Integer.MAX_VALUE;

    @Builder.Default
    int minimumTransferDuration = 0;

    @Builder.Default
    int maximumTransfers = Integer.MAX_VALUE;

    @Builder.Default
    int maximumTravelDuration = Integer.MAX_VALUE;

    @Builder.Default
    boolean wheelchairAccessible = false;

    @Builder.Default
    boolean bikeAllowed = false;

    @Builder.Default
    EnumSet<TravelMode> travelModes = EnumSet.allOf(TravelMode.class);

}
