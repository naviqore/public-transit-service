package ch.naviqore.service.config;

import lombok.Getter;

/**
 * Configuration to create a public transit service with default values set.
 */
@Getter
public class ServiceConfig {

    private final int minimumTransferTime;
    private final int maxWalkingDistance;
    private final int walkingSpeed;
    private final int sameStationTransferTime;
    private final WalkCalculatorType walkCalculatorType;
    private final String gtfsUrl;

    public ServiceConfig(String gtfsUrl, int minimumTransferTime, int maxWalkingDistance, int walkingSpeed,
                         int sameStationTransferTime, WalkCalculatorType walkCalculatorType) {
        this.gtfsUrl = gtfsUrl;

        if (minimumTransferTime < 0) {
            throw new IllegalArgumentException("Minimum transfer time must be greater than or equal to 0.");
        }
        this.minimumTransferTime = minimumTransferTime;

        if (maxWalkingDistance < 0) {
            throw new IllegalArgumentException("Maximum walking distance must be greater than or equal to 0.");
        }
        this.maxWalkingDistance = maxWalkingDistance;

        if (walkingSpeed <= 0) {
            throw new IllegalArgumentException("Walking speed must be greater than to 0.");
        }
        this.walkingSpeed = walkingSpeed;

        if (sameStationTransferTime < 0) {
            throw new IllegalArgumentException("Same station transfer time must be greater than or equal to 0.");
        }
        this.sameStationTransferTime = sameStationTransferTime;

        this.walkCalculatorType = walkCalculatorType;
    }

    /**
     * Constructor with defaults
     */
    public ServiceConfig(String gtfsUrl) {
        this(gtfsUrl, 120, 500, 3500, 120, WalkCalculatorType.BEE_LINE_DISTANCE);
    }

    public enum WalkCalculatorType {
        BEE_LINE_DISTANCE
    }

}