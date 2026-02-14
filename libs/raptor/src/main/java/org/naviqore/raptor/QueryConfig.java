package org.naviqore.raptor;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.EnumSet;

/**
 * Configuration to query connections or isolines. Default values are set to infinity or zero, which means that no
 * restrictions are set.
 */
@NoArgsConstructor
@Getter
public class QueryConfig {

    private static final int INFINITY = Integer.MAX_VALUE;

    private int maximumWalkDuration = INFINITY;
    private int minimumTransferDuration = 0;
    private int maximumTransfers = INFINITY;
    private int maximumTravelDuration = INFINITY;

    @Setter
    private boolean wheelchairAccessible = false;
    @Setter
    private boolean bikeAccessible = false;
    @Setter
    private EnumSet<TravelMode> allowedTravelModes = EnumSet.allOf(TravelMode.class);

    @Setter
    private boolean allowSourceTransfer = true;
    @Setter
    private boolean allowTargetTransfer = true;

    public QueryConfig(int maximumWalkDuration, int minimumTransferDuration, int maximumTransfers,
                       int maximumTravelDuration, boolean wheelchairAccessible, boolean bikeAccessible,
                       EnumSet<TravelMode> allowedTravelModes) {
        this.setMaximumWalkDuration(maximumWalkDuration);
        this.setMinimumTransferDuration(minimumTransferDuration);
        this.setMaximumTransfers(maximumTransfers);
        this.setMaximumTravelDuration(maximumTravelDuration);
        this.setWheelchairAccessible(wheelchairAccessible);
        this.setBikeAccessible(bikeAccessible);
        this.setAllowedTravelModes(allowedTravelModes);
    }

    public void setMaximumWalkDuration(int maximumWalkDuration) {
        if (maximumWalkDuration < 0) {
            throw new IllegalArgumentException("Maximum walk duration must be greater than or equal to 0.");
        }
        this.maximumWalkDuration = maximumWalkDuration;
    }

    public void setMinimumTransferDuration(int minimumTransferDuration) {
        if (minimumTransferDuration < 0) {
            throw new IllegalArgumentException("Minimum transfer duration must be greater than or equal to 0.");
        }
        this.minimumTransferDuration = minimumTransferDuration;
    }

    public void setMaximumTransfers(int maximumTransfers) {
        if (maximumTransfers < 0) {
            throw new IllegalArgumentException("Maximum transfers must be greater than or equal to 0.");
        }
        this.maximumTransfers = maximumTransfers;
    }

    public void setMaximumTravelDuration(int maximumTravelDuration) {
        if (maximumTravelDuration <= 0) {
            throw new IllegalArgumentException("Maximum travel duration must be greater than 0.");
        }
        this.maximumTravelDuration = maximumTravelDuration;
    }

    /**
     * Check if the query configuration needs filtering based on the allowed travel modes.
     *
     * <p>It is not necessary to filter if all travel modes are allowed. Also, if allowed travel modes are empty, it
     * is assumed that all travel modes are allowed.
     *
     * @return true if the query configuration needs filtering based on the allowed travel modes, false otherwise.
     */
    public boolean needsTravelModeFiltering() {
        return allowedTravelModes.size() < TravelMode.values().length && !allowedTravelModes.isEmpty();
    }

}
