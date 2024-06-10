package ch.naviqore.raptor;

import lombok.Getter;
import lombok.NoArgsConstructor;

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

    public QueryConfig(int maximumWalkingDuration, int minimumTransferDuration, int maximumTransferNumber,
                       int maximumTravelTime) {
        this.setMaximumWalkingDuration(maximumWalkingDuration);
        this.setMinimumTransferDuration(minimumTransferDuration);
        this.setMaximumTransferNumber(maximumTransferNumber);
        this.setMaximumTravelTime(maximumTravelTime);
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

}
