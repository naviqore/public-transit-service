package org.naviqore.service;

import org.naviqore.gtfs.schedule.GtfsScheduleReader;
import org.naviqore.service.config.ConnectionQueryConfig;
import org.naviqore.service.config.ServiceConfig;
import org.naviqore.service.exception.ConnectionRoutingException;
import org.naviqore.service.exception.StopNotFoundException;
import org.naviqore.service.repo.GtfsScheduleRepository;
import org.naviqore.utils.network.FileDownloader;
import org.naviqore.utils.spatial.GeoCoordinate;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * A simple working example of how to use the public transit service in your Java application.
 */
public class PublicTransitServiceExample {
    public static final String GTFS_STATIC_URI = "https://github.com/google/transit/raw/refs/heads/master/gtfs/spec/en/examples/sample-feed-1.zip";
    public static final String ORIG_STOP_ID = "STAGECOACH";
    public static final GeoCoordinate DEST_LOCATION = new GeoCoordinate(36.9149, -116.7614);
    public static final OffsetDateTime DEPARTURE_TIME = LocalDateTime.of(2007, 1, 1, 0, 0)
            .atZone(ZoneId.of("America/Los_Angeles"))
            .toOffsetDateTime();

    static void main() throws IOException, InterruptedException, StopNotFoundException, ConnectionRoutingException {

        GtfsScheduleRepository repo = () -> {
            new FileDownloader(GTFS_STATIC_URI).downloadTo(Path.of("."), "gtfs.zip", true);
            return new GtfsScheduleReader().read("gtfs.zip");
        };

        ServiceConfig serviceConfig = ServiceConfig.builder().gtfsScheduleRepository(repo).build();
        PublicTransitService service = new PublicTransitServiceFactory(serviceConfig).create();

        Stop orig = service.getStopById(ORIG_STOP_ID);
        ConnectionQueryConfig queryConfig = ConnectionQueryConfig.builder().build();
        service.getConnections(orig, DEST_LOCATION, DEPARTURE_TIME, TimeType.DEPARTURE, queryConfig);
    }
}
