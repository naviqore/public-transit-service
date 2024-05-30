package ch.naviqore.service;

import ch.naviqore.service.config.ConnectionQueryConfig;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface ConnectionRoutingService {

    /**
     * Retrieves possible connections between two locations at a specified time.
     *
     * @param source   the starting location
     * @param target   the destination location
     * @param time     the time of departure or arrival
     * @param timeType the type of time specified (departure or arrival)
     * @param config   additional configuration for the query
     * @return a list of possible connections between the source and target locations
     */
    List<Connection> getConnections(Location source, Location target, LocalDateTime time, TimeType timeType,
                                    ConnectionQueryConfig config);

    /**
     * Retrieves the shortest possible connection to each stop from a given departure location and time  within a given
     * time budget or a maximum number of transfers.
     *
     * @param source        the location to start the journey from
     * @param departureTime the time of departure
     * @param config        additional configuration for the query
     * @return a map of stops to the shortest possible connection to each stop from the departure location
     */
    Map<Stop, Connection> getIsolines(Location source, LocalDateTime departureTime, ConnectionQueryConfig config);

}
