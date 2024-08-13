package ch.naviqore.service;

/**
 * Public transit service with methods for retrieving stops, trips, routes, and connections.
 */
public interface PublicTransitService extends ScheduleInformationService, ConnectionRoutingService {

    // future methods to update the service using GTFS real-time data should be included here

}
