package ch.naviqore.service;

import ch.naviqore.service.config.ServiceConfig;
import ch.naviqore.service.impl.PublicTransitServiceImpl;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class PublicTransitServiceFactory {

    private final ServiceConfig config;

    public PublicTransitService create() {
        return new PublicTransitServiceImpl(config);
    }

}