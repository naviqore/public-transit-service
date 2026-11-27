package org.naviqore.app.service;

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.naviqore.app.config.NaviqoreProperties;
import org.naviqore.app.infrastructure.GtfsScheduleFile;
import org.naviqore.app.infrastructure.GtfsScheduleS3;
import org.naviqore.app.infrastructure.GtfsScheduleUrl;
import org.naviqore.service.config.ServiceConfig;
import org.naviqore.service.repo.TimetableRepository;
import org.springframework.stereotype.Component;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/**
 * Builds the configuration of the public transit service from the properties of the app, which it validates.
 */
@Component
@Getter
public class ServiceConfigParser {

    private static final String URI_PROPERTY = "naviqore.gtfs.static.uri";

    private final ServiceConfig serviceConfig;

    public ServiceConfigParser(NaviqoreProperties properties) {
        String gtfsStaticUri = properties.getGtfs().getStaticFeed().getUri();
        if (gtfsStaticUri == null || gtfsStaticUri.isBlank()) {
            throw new IllegalArgumentException("No static GTFS feed is set: " + URI_PROPERTY + " is empty.");
        }
        NaviqoreProperties.Service service = properties.getService();
        NaviqoreProperties.Service.Transfer.Duration transferDuration = service.getTransfer().getDuration();
        NaviqoreProperties.Service.Walk walk = service.getWalk();
        NaviqoreProperties.Raptor raptor = properties.getRaptor();

        this.serviceConfig = ServiceConfig.builder()
                .timetableRepository(getRepository(gtfsStaticUri))
                .transferDurationBetweenStopsMinimum(transferDuration.getBetweenStopsMinimum())
                .transferDurationAccessEgress(transferDuration.getAccessEgress())
                .walkSearchRadius(walk.getSearchRadius())
                .walkCalculatorType(walk.getCalculatorType())
                .walkSpeed(walk.getSpeed())
                .walkDurationMinimum(walk.getDurationMinimum())
                .optimizeTravelTime(service.isOptimizeTravelTime())
                .raptorDaysToScan(raptor.getDaysToScan())
                .raptorSameStopTransferDurationDefault(raptor.getSameStopTransferDurationDefault())
                .raptorStopTimeCacheSize(raptor.getStopTimeCacheSize())
                .raptorStopTimeCacheStrategy(raptor.getStopTimeCacheStrategy())
                .build();
    }

    private static TimetableRepository getRepository(String gtfsStaticUri) {
        if (isLocalFile(gtfsStaticUri)) {
            return new GtfsScheduleFile(gtfsStaticUri);
        }

        String scheme;
        try {
            scheme = new URI(gtfsStaticUri).getScheme();
        } catch (URISyntaxException e) {
            throw invalidUri(gtfsStaticUri, e);
        }

        return switch (scheme == null ? "" : scheme.toLowerCase(Locale.ROOT)) {
            case "http", "https" -> new GtfsScheduleUrl(gtfsStaticUri);
            case "s3" -> new GtfsScheduleS3(gtfsStaticUri);
            default -> throw invalidUri(gtfsStaticUri, null);
        };
    }

    private static boolean isLocalFile(String path) {
        File file = new File(path);
        return file.exists() && file.isFile();
    }

    private static IllegalArgumentException invalidUri(String gtfsStaticUri, @Nullable URISyntaxException cause) {
        return new IllegalArgumentException(URI_PROPERTY + " is neither an existing file nor a URI with scheme " +
                "http, https or s3: " + gtfsStaticUri, cause);
    }
}
