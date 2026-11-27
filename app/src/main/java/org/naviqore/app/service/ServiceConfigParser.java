package org.naviqore.app.service;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.naviqore.app.config.AppProperties;
import org.naviqore.app.infrastructure.GtfsScheduleFile;
import org.naviqore.app.infrastructure.GtfsScheduleS3;
import org.naviqore.app.infrastructure.GtfsScheduleUrl;
import org.naviqore.service.config.ServiceConfig;
import org.naviqore.service.repo.TimetableRepository;
import org.springframework.stereotype.Component;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * Builds the configuration of the public transit service from the properties of the app, which it validates.
 */
@Component
@Getter
@Slf4j
public class ServiceConfigParser {

    private final ServiceConfig serviceConfig;

    public ServiceConfigParser(AppProperties properties) {
        AppProperties.Gtfs.Static staticFeed = properties.getGtfs().getStaticFeed();
        String gtfsStaticUri = staticFeed.getUri();
        if (gtfsStaticUri == null || gtfsStaticUri.isBlank()) {
            throw new IllegalArgumentException("No static GTFS feed is set: app.gtfs.static.uri is empty.");
        }
        AppProperties.Transfer.Duration transferDuration = properties.getTransfer().getDuration();
        AppProperties.Walk walk = properties.getWalk();
        AppProperties.Raptor raptor = properties.getRaptor();
        AppProperties.Cache cache = properties.getCache();

        this.serviceConfig = ServiceConfig.builder()
                .timetableRepository(getRepository(gtfsStaticUri))
                .gtfsStaticUpdateCron(staticFeed.getUpdateCron())
                .transferDurationSameStopDefault(transferDuration.getSameStopDefault())
                .transferDurationBetweenStopsMinimum(transferDuration.getBetweenStopsMinimum())
                .transferDurationAccessEgress(transferDuration.getAccessEgress())
                .walkSearchRadius(walk.getSearchRadius())
                .walkCalculatorType(walk.getCalculatorType())
                .walkSpeed(walk.getSpeed())
                .walkDurationMinimum(walk.getDurationMinimum())
                .raptorDaysToScan(raptor.getDaysToScan())
                .optimizeTravelTime(raptor.isOptimizeTravelTime())
                .cacheServiceDaySize(cache.getServiceDaySize())
                .cacheEvictionStrategy(cache.getEvictionStrategy())
                .build();
    }

    private static TimetableRepository getRepository(String gtfsStaticUri) {
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
