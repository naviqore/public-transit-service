package org.naviqore.app.service;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.naviqore.app.infrastructure.GtfsScheduleFile;
import org.naviqore.app.infrastructure.GtfsScheduleS3;
import org.naviqore.app.infrastructure.GtfsScheduleUrl;
import org.naviqore.service.config.ServiceConfig;
import org.naviqore.service.repo.GtfsScheduleRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;

@Component
@Getter
@Slf4j
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

    private static GtfsScheduleRepository getRepository(String gtfsStaticUri) {
        if (isLocalFile(gtfsStaticUri)) {
            return new GtfsScheduleFile(gtfsStaticUri);
        }

        UriScheme scheme = getUriScheme(gtfsStaticUri);

        return switch (scheme) {
            case HTTP, HTTPS -> new GtfsScheduleUrl(gtfsStaticUri);
            case S3 -> new GtfsScheduleS3(gtfsStaticUri);
        };
    }

    private static boolean isLocalFile(String path) {
        File file = new File(path);
        return file.exists() && file.isFile();
    }

    private static UriScheme getUriScheme(String uri) {
        try {
            URI parsedUri = new URI(uri);
            String scheme = parsedUri.getScheme();
            return UriScheme.valueOf(scheme.toUpperCase());
        } catch (URISyntaxException e) {
            log.error("Error parsing URI: {}. Exception: {}", uri, e.getMessage(), e);
            throw new IllegalArgumentException("Invalid URI format: " + uri, e);
        } catch (IllegalArgumentException e) {
            log.error("Invalid URI scheme: {}. Exception: {}", uri, e.getMessage(), e);
            throw e;
        }
    }

    private enum UriScheme {
        HTTP,
        HTTPS,
        S3
    }
}
