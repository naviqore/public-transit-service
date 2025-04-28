package org.naviqore.app.service;

import lombok.Getter;
import org.naviqore.app.infrastructure.GtfsScheduleFile;
import org.naviqore.app.infrastructure.GtfsScheduleUrl;
import org.naviqore.service.config.ServiceConfig;
import org.naviqore.service.repo.GtfsScheduleRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;

@Component
@Getter
public class ServiceConfigParser {

    private static final List<String> URL_ALLOWED_SCHEMES = Arrays.asList("http", "https");

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
                .gtfsScheduleRepository(getRepository(gtfsStaticUri))
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

    private static GtfsScheduleRepository getRepository(String gtfsStaticUrl) {
        if (isLocalFile(gtfsStaticUrl)) {
            return new GtfsScheduleFile(gtfsStaticUrl);
        } else if (isValidUrl(gtfsStaticUrl)) {
            return new GtfsScheduleUrl(gtfsStaticUrl);
        } else {
            throw new IllegalArgumentException("Invalid GTFS static URI value: " + gtfsStaticUrl);
        }
    }

    private static boolean isLocalFile(String path) {
        File file = new File(path);
        return file.exists() && file.isFile();
    }

    private static boolean isValidUrl(String urlString) {
        try {
            URI uri = new URI(urlString);
            String scheme = uri.getScheme();
            return scheme != null && URL_ALLOWED_SCHEMES.contains(scheme);
        } catch (URISyntaxException e) {
            return false;
        }
    }
}
