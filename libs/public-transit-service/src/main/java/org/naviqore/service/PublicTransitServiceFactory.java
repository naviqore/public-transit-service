package org.naviqore.service;

import lombok.RequiredArgsConstructor;
import org.naviqore.gtfs.schedule.model.GtfsSchedule;
import org.naviqore.service.config.ServiceConfig;
import org.naviqore.service.gtfs.raptor.GtfsRaptorServiceInitializer;
import org.naviqore.service.repo.GtfsScheduleRepository;

import java.io.IOException;

@RequiredArgsConstructor
public class PublicTransitServiceFactory {

    private final ServiceConfig config;
    private final GtfsScheduleRepository repo;

    public PublicTransitService create() {
        try {
            GtfsSchedule schedule = repo.get();
            return new GtfsRaptorServiceInitializer(config, schedule).get();
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

}