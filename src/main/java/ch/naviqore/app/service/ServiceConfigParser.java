package ch.naviqore.app.service;

import ch.naviqore.service.config.ServiceConfig;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Getter
public class ServiceConfigParser {

    private final ServiceConfig serviceConfig;

    public ServiceConfigParser(@Value("${gtfs.static.uri}") String gtfsStaticUri,
                               @Value("${gtfs.static.update.cron}") String gtfsStaticUpdateCron,
                               @Value("${transfer.time.same.stop.default}") int transferTimeSameStopDefault,
                               @Value("${transfer.time.between.stops.minimum}") int transferTimeBetweenStopsMinimum,
                               @Value("${transfer.time.access.egress}") int transferTimeAccessEgress,
                               @Value("${walking.search.radius}") int walkingSearchRadius,
                               @Value("${walking.calculator.type}") String walkingCalculatorType,
                               @Value("${walking.speed}") double walkingSpeed,
                               @Value("${walking.duration.minimum}") int walkingDurationMinimum,
                               @Value("${raptor.days.to.scan}") int raptorDaysToScan,
                               @Value("${cache.service.day.size}") int cacheServiceDaySize,
                               @Value("${cache.eviction.strategy}") String cacheEvictionStrategy) {

        ServiceConfig.WalkCalculatorType walkCalculatorTypeEnum = ServiceConfig.WalkCalculatorType.valueOf(
                walkingCalculatorType.toUpperCase());
        ServiceConfig.CacheEvictionStrategy cacheEvictionStrategyEnum = ServiceConfig.CacheEvictionStrategy.valueOf(
                cacheEvictionStrategy.toUpperCase());

        this.serviceConfig = new ServiceConfig(gtfsStaticUri, gtfsStaticUpdateCron, transferTimeSameStopDefault,
                transferTimeBetweenStopsMinimum, transferTimeAccessEgress, walkingSearchRadius, walkCalculatorTypeEnum,
                walkingSpeed, walkingDurationMinimum, raptorDaysToScan, cacheServiceDaySize, cacheEvictionStrategyEnum);
    }

}
