package ch.naviqore.service;

/**
 * Public transit service with methods to retrieve stops, trips, routes, and connections.
 */
public interface PublicTransitService extends ScheduleInformationService, ConnectionRoutingService {

    /**
     * Updates the transit schedule from the URL provided.
     */
    void updateStaticSchedule();

}
