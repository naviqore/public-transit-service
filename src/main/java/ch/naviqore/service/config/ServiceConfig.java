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
    private final WalkCalculatorType walkCalculatorType;
    private final String gtfsUrl;

    public ServiceConfig(String gtfsUrl, int minimumTransferTime, int maxWalkingDistance, int walkingSpeed,
                         WalkCalculatorType walkCalculatorType) {
        this.gtfsUrl = gtfsUrl;
        this.minimumTransferTime = minimumTransferTime;
        this.maxWalkingDistance = maxWalkingDistance;
        this.walkingSpeed = walkingSpeed;
        this.walkCalculatorType = walkCalculatorType;
    }

    /**
     * Constructor with defaults
     */
    public ServiceConfig(String gtfsUrl) {
        this(gtfsUrl, 120, 500, 3500, WalkCalculatorType.BEE_LINE_DISTANCE);
    }

    public enum WalkCalculatorType {
        BEE_LINE_DISTANCE
    }

}
