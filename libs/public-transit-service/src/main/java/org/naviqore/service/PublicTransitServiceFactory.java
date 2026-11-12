package org.naviqore.service;

import lombok.RequiredArgsConstructor;
import org.naviqore.service.config.ServiceConfig;
import org.naviqore.service.raptor.RaptorServiceInitializer;

import java.io.IOException;

@RequiredArgsConstructor
public class PublicTransitServiceFactory {

    private final ServiceConfig config;

    public PublicTransitService create() throws IOException, InterruptedException {
        return new RaptorServiceInitializer(config).get();
    }

}