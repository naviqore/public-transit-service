package org.naviqore.service;

import org.naviqore.service.config.ConnectionQueryConfig;
import org.naviqore.service.exception.ConnectionRoutingException;
import org.naviqore.utils.spatial.GeoCoordinate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Routes connections and isolines between stops and locations, departing at or arriving by a time.
 * <ul>
 *     <li>Connections: without a time window, those no other connection beats in arrival (departure, for an arrival
 *     query) and number of transfers, each leaving as late as its arrival allows (arriving as early as its departure
 *     allows) unless the service is configured not to optimize travel times; with a time window, those leaving
 *     (arriving) in the window that no other connection beats in departure, arrival and number of transfers.</li>
 *     <li>Isolines: without a time window, the earliest arrival (latest departure) at each stop; with a time window,
 *     the shortest travel time to each stop among the connections leaving (arriving) in the window.</li>
 * </ul>
 * See {@code docs/routing.md} for the definitions and how they are routed.
 */
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
    List<Connection> getConnections(GeoCoordinate source, GeoCoordinate target, OffsetDateTime time, TimeType timeType,
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
    List<Connection> getConnections(Stop source, Stop target, OffsetDateTime time, TimeType timeType,
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
    List<Connection> getConnections(GeoCoordinate source, Stop target, OffsetDateTime time, TimeType timeType,
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
    List<Connection> getConnections(Stop source, GeoCoordinate target, OffsetDateTime time, TimeType timeType,
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
    Map<Stop, Connection> getIsolines(GeoCoordinate source, OffsetDateTime time, TimeType timeType,
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
    Map<Stop, Connection> getIsolines(Stop source, OffsetDateTime time, TimeType timeType,
                                      ConnectionQueryConfig config) throws ConnectionRoutingException;
}
