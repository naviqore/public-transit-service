package org.naviqore.service.config;

import lombok.Builder;
import lombok.Value;
import org.naviqore.service.TravelMode;

import java.util.EnumSet;

@Builder(toBuilder = true)
@Value
public class ConnectionQueryConfig {

    /**
     * The time window in seconds after the time of a departure query (before the time of an arrival query), in which
     * connections leave (arrive), or 0 for none. See {@link org.naviqore.service.ConnectionRoutingService}.
     */
    @Builder.Default
    int timeWindowDuration = 0;

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

    public static ConnectionQueryConfig defaults() {
        return builder().build();
    }

}
