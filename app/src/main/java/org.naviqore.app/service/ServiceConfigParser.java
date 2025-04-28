package org.naviqore.app.service;

import lombok.Getter;
import org.naviqore.service.config.ServiceConfig;
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
                               @Value("${raptor.range}") int raptorRange,
                               @Value("${cache.service.day.size}") int cacheServiceDaySize,
                               @Value("${cache.eviction.strategy}") String cacheEvictionStrategy) {

        this.serviceConfig = ServiceConfig.builder()
                .gtfsStaticUri(gtfsStaticUri)
                .gtfsStaticUpdateCron(gtfsStaticUpdateCron)
                .transferTimeSameStopDefault(transferTimeSameStopDefault)
                .transferTimeBetweenStopsMinimum(transferTimeBetweenStopsMinimum)
                .transferTimeAccessEgress(transferTimeAccessEgress)
                .walkingSearchRadius(walkingSearchRadius)
                .walkingCalculatorType(ServiceConfig.WalkCalculatorType.valueOf(walkingCalculatorType.toUpperCase()))
                .walkingSpeed(walkingSpeed)
                .walkingDurationMinimum(walkingDurationMinimum)
                .raptorDaysToScan(raptorDaysToScan)
                .raptorRange(raptorRange)
                .cacheServiceDaySize(cacheServiceDaySize)
                .cacheEvictionStrategy(ServiceConfig.CacheEvictionStrategy.valueOf(cacheEvictionStrategy.toUpperCase()))
                .build();
    }

}
