package ch.naviqore.app.service;

import ch.naviqore.service.config.ServiceConfig;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Getter
public class ServiceConfigParser {

    private final ServiceConfig serviceConfig;

    public ServiceConfigParser(@Value("${gtfs.static.url}") String gtfsUrl,
                               @Value("${transfer.time.minimum:120}") int minimumTransferTime,
                               @Value("${walking.distance.maximum:500}") int maxWalkingDistance,
                               @Value("${walking.speed:3500}") int walkingSpeed,
                               @Value("${walking.calculator.type:BEE_LINE_DISTANCE}") String walkCalculatorTypeStr) {
        ServiceConfig.WalkCalculatorType walkCalculatorType = ServiceConfig.WalkCalculatorType.valueOf(
                walkCalculatorTypeStr.toUpperCase());
        this.serviceConfig = new ServiceConfig(gtfsUrl, minimumTransferTime, maxWalkingDistance, walkingSpeed,
                walkCalculatorType);
    }

}