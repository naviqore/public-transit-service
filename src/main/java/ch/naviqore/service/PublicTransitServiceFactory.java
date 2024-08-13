package ch.naviqore.service;

import ch.naviqore.gtfs.schedule.model.GtfsSchedule;
import ch.naviqore.service.config.ServiceConfig;
import ch.naviqore.service.gtfs.raptor.GtfsRaptorServiceInitializer;
import ch.naviqore.service.repo.GtfsScheduleRepository;
import lombok.RequiredArgsConstructor;

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