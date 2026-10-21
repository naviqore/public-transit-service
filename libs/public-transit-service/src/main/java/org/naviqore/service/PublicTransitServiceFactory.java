package org.naviqore.service;

import lombok.RequiredArgsConstructor;
import org.naviqore.raptor.RaptorAlgorithm;
import org.naviqore.service.config.ServiceConfig;
import org.naviqore.service.gtfs.raptor.GtfsRaptorService;
import org.naviqore.service.gtfs.raptor.GtfsRaptorServiceInitializer;
import org.naviqore.service.spi.TransitSchedule;

import java.io.IOException;

@RequiredArgsConstructor
public class PublicTransitServiceFactory {

    private final ServiceConfig config;

    public PublicTransitService create() throws IOException, InterruptedException {
        return new GtfsRaptorServiceInitializer(config).get();
    }

    /**
     * Creates a public transit service on top of a schedule source other than the GTFS schedule repository of the
     * configuration, which is not used. The RAPTOR router must be built from the same schedule.
     *
     * @param schedule the schedule to answer schedule information queries and to compose routing results from
     * @param raptor   the RAPTOR router built from the schedule
     * @return the public transit service
     */
    public PublicTransitService create(TransitSchedule schedule, RaptorAlgorithm raptor) {
        return new GtfsRaptorService(config, schedule, raptor);
    }

}