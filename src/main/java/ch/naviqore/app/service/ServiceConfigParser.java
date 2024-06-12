package ch.naviqore.app.service;

import ch.naviqore.service.config.ServiceConfig;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Getter
public class ServiceConfigParser {

    private final ServiceConfig serviceConfig;

    public ServiceConfigParser(@Value("${gtfs.static.url}") String gtfsStaticUrl,
                               @Value("${gtfs.static.update.cron}") String gtfsStaticUpdateCron,
                               @Value("${transfer.time.between.stops.minimum}") int transferTimeBetweenStopsMinimum,
                               @Value("${transfer.time.same.stop.default}") int transferTimeSameStopDefault,
                               @Value("${walking.calculator.type}") String walkingCalculatorType,
                               @Value("${walking.speed}") double walkingSpeed,
                               @Value("${walking.distance.maximum}") int walkingDistanceMaximum,
                               @Value("${walking.duration.minimum}") int walkingDurationMinimum,
                               @Value("${cache.size}") int cacheSize,
                               @Value("${cache.eviction.strategy}") String cacheEvictionStrategy) {

        ServiceConfig.WalkCalculatorType walkCalculatorTypeEnum = ServiceConfig.WalkCalculatorType.valueOf(
                walkingCalculatorType.toUpperCase());
        ServiceConfig.CacheEvictionStrategy cacheEvictionStrategyEnum = ServiceConfig.CacheEvictionStrategy.valueOf(
                cacheEvictionStrategy.toUpperCase());

        this.serviceConfig = new ServiceConfig(gtfsStaticUrl, gtfsStaticUpdateCron, transferTimeBetweenStopsMinimum,
                transferTimeSameStopDefault, walkCalculatorTypeEnum, walkingSpeed, walkingDistanceMaximum,
                walkingDurationMinimum, cacheSize, cacheEvictionStrategyEnum);
    }

}
