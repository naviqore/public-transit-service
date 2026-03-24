package org.naviqore.raptor;

import lombok.Builder;
import lombok.Getter;

import java.util.EnumSet;
import java.util.OptionalInt;

@Getter
@Builder(toBuilder = true)
public class QueryConfig {

    @Builder.Default
    private final int maximumWalkDuration = Integer.MAX_VALUE;
    @Builder.Default
    private final int minimumTransferDuration = 0;
    @Builder.Default
    private final int maximumTransfers = Integer.MAX_VALUE;
    @Builder.Default
    private final int maximumTravelDuration = Integer.MAX_VALUE;
    @Builder.Default
    private final boolean wheelchairAccessible = false;
    @Builder.Default
    private final boolean bikeAccessible = false;
    @Builder.Default
    private final boolean allowSourceTransfer = true;
    @Builder.Default
    private final boolean allowTargetTransfer = true;

    private final Integer raptorRange;

    @Builder.Default
    private final EnumSet<TravelMode> allowedTravelModes = EnumSet.allOf(TravelMode.class);

    private QueryConfig(int maximumWalkDuration, int minimumTransferDuration, int maximumTransfers,
                        int maximumTravelDuration, boolean wheelchairAccessible, boolean bikeAccessible,
                        boolean allowSourceTransfer, boolean allowTargetTransfer, Integer raptorRange,
                        EnumSet<TravelMode> allowedTravelModes) {

        if (maximumWalkDuration < 0) {
            throw new IllegalArgumentException("Maximum walk duration must be greater than or equal to 0.");
        }
        if (minimumTransferDuration < 0) {
            throw new IllegalArgumentException("Minimum transfer duration must be greater than or equal to 0.");
        }
        if (maximumTransfers < 0) {
            throw new IllegalArgumentException("Maximum transfers must be greater than or equal to 0.");
        }
        if (maximumTravelDuration <= 0) {
            throw new IllegalArgumentException("Maximum travel duration must be greater than 0.");
        }

        this.maximumWalkDuration = maximumWalkDuration;
        this.minimumTransferDuration = minimumTransferDuration;
        this.maximumTransfers = maximumTransfers;
        this.maximumTravelDuration = maximumTravelDuration;
        this.wheelchairAccessible = wheelchairAccessible;
        this.bikeAccessible = bikeAccessible;
        this.allowSourceTransfer = allowSourceTransfer;
        this.allowTargetTransfer = allowTargetTransfer;
        this.raptorRange = raptorRange;
        this.allowedTravelModes = allowedTravelModes;
    }

    public OptionalInt getRaptorRange() {
        return raptorRange == null ? OptionalInt.empty() : OptionalInt.of(raptorRange);
    }

    public boolean needsTravelModeFiltering() {
        return allowedTravelModes.size() < TravelMode.values().length && !allowedTravelModes.isEmpty();
    }
}