package ch.naviqore.raptor;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.EnumSet;

/**
 * Configuration to query connections or iso-lines. Default values are set to infinity or zero, which means that no
 * restrictions are set.
 */
@NoArgsConstructor
@Getter
public class QueryConfig {

    private static final int INFINITY = Integer.MAX_VALUE;

    private int maximumWalkingDuration = INFINITY;
    private int minimumTransferDuration = 0;
    private int maximumTransferNumber = INFINITY;
    private int maximumTravelTime = INFINITY;

    @Setter
    private boolean wheelchairAccessible = false;
    @Setter
    private boolean bikeAccessible = false;
    @Setter
    private EnumSet<TravelMode> allowedTravelModes = EnumSet.allOf(TravelMode.class);

    @Setter
    private boolean doInitialTransferRelaxation = true;
    @Setter
    private boolean allowSourceTransfer = true;
    @Setter
    private boolean allowTargetTransfer = true;


    public QueryConfig(int maximumWalkingDuration, int minimumTransferDuration, int maximumTransferNumber,
                       int maximumTravelTime, boolean wheelchairAccessible, boolean bikeAccessible,
                       EnumSet<TravelMode> allowedTravelModes) {
        this.setMaximumWalkingDuration(maximumWalkingDuration);
        this.setMinimumTransferDuration(minimumTransferDuration);
        this.setMaximumTransferNumber(maximumTransferNumber);
        this.setMaximumTravelTime(maximumTravelTime);
        this.setWheelchairAccessible(wheelchairAccessible);
        this.setBikeAccessible(bikeAccessible);
        this.setAllowedTravelModes(allowedTravelModes);
    }

    public void setMaximumWalkingDuration(int maximumWalkingDuration) {
        if (maximumWalkingDuration < 0) {
            throw new IllegalArgumentException("Maximum walking duration must be greater than or equal to 0.");
        }
        this.maximumWalkingDuration = maximumWalkingDuration;
    }

    public void setMinimumTransferDuration(int minimumTransferDuration) {
        if (minimumTransferDuration < 0) {
            throw new IllegalArgumentException("Minimum transfer duration must be greater than or equal to 0.");
        }
        this.minimumTransferDuration = minimumTransferDuration;
    }

    public void setMaximumTransferNumber(int maximumTransferNumber) {
        if (maximumTransferNumber < 0) {
            throw new IllegalArgumentException("Maximum transfer number must be greater than or equal to 0.");
        }
        this.maximumTransferNumber = maximumTransferNumber;
    }

    public void setMaximumTravelTime(int maximumTravelTime) {
        if (maximumTravelTime <= 0) {
            throw new IllegalArgumentException("Maximum transfer number must be greater than 0.");
        }
        this.maximumTravelTime = maximumTravelTime;
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
