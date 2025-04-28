package org.naviqore.service;

import lombok.RequiredArgsConstructor;
import org.naviqore.service.config.ServiceConfig;
import org.naviqore.service.gtfs.raptor.GtfsRaptorServiceInitializer;

import java.io.IOException;

@RequiredArgsConstructor
public class PublicTransitServiceFactory {

    private final ServiceConfig config;

    public PublicTransitService create() throws IOException, InterruptedException {
        return new GtfsRaptorServiceInitializer(config).get();
    }

}