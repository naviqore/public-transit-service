package ch.naviqore.service;

import ch.naviqore.service.config.ConnectionQueryConfig;
import ch.naviqore.service.exception.ConnectionRoutingException;
import ch.naviqore.utils.spatial.GeoCoordinate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface ConnectionRoutingService {

    /**
     * Retrieves the supported routing features of the service.
     *
     * @return the supported routing features
     */
    RoutingFeatures getRoutingFeatures();

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
    List<Connection> getConnections(GeoCoordinate source, GeoCoordinate target, LocalDateTime time, TimeType timeType,
                                    ConnectionQueryConfig config) throws ConnectionRoutingException;

    /**
     * Retrieves possible connections between two stops at a specified time.
     *
     * @param source   the starting stop
     * @param target   the destination stop
     * @param time     the time of departure or arrival
     * @param timeType the type of time specified (departure or arrival)
     * @param config   additional configuration for the query
     * @return a list of possible connections between the source and target stop
     */
    List<Connection> getConnections(Stop source, Stop target, LocalDateTime time, TimeType timeType,
                                    ConnectionQueryConfig config) throws ConnectionRoutingException;

    /**
     * Retrieves possible connections between a coordinate and a stop target at a specified time.
     *
     * @param source   the starting coordinate
     * @param target   the destination stop
     * @param time     the time of departure or arrival
     * @param timeType the type of time specified (departure or arrival)
     * @param config   additional configuration for the query
     * @return a list of possible connections between the source and target stop
     */
    List<Connection> getConnections(GeoCoordinate source, Stop target, LocalDateTime time, TimeType timeType,
                                    ConnectionQueryConfig config) throws ConnectionRoutingException;

    /**
     * Retrieves possible connections between a stop and a coordinate target at a specified time.
     *
     * @param source   the starting stop
     * @param target   the destination coordinate
     * @param time     the time of departure or arrival
     * @param timeType the type of time specified (departure or arrival)
     * @param config   additional configuration for the query
     * @return a list of possible connections between the source and target stop
     */
    List<Connection> getConnections(Stop source, GeoCoordinate target, LocalDateTime time, TimeType timeType,
                                    ConnectionQueryConfig config) throws ConnectionRoutingException;

    /**
     * Retrieves the shortest possible connection to each stop from a given departure location and time  within a given
     * time budget or a maximum number of transfers.
     *
     * @param source   the location to start the journey from
     * @param time     the time of departure or arrival
     * @param timeType the type of time specified (departure or arrival)
     * @param config   additional configuration for the query
     * @return a map of stops to the shortest possible connection to each stop from the departure location
     */
    Map<Stop, Connection> getIsoLines(GeoCoordinate source, LocalDateTime time, TimeType timeType,
                                      ConnectionQueryConfig config) throws ConnectionRoutingException;

    /**
     * Retrieves the shortest possible connection to each stop from a given departure stop and time  within a given time
     * budget or a maximum number of transfers.
     *
     * @param source   the location to start the journey from
     * @param time     the time of departure or arrival
     * @param timeType the type of time specified (departure or arrival)
     * @param config   additional configuration for the query
     * @return a map of stops to the shortest possible connection to each stop from the departure location
     */
    Map<Stop, Connection> getIsoLines(Stop source, LocalDateTime time, TimeType timeType,
                                      ConnectionQueryConfig config) throws ConnectionRoutingException;
}
