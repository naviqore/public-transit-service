package org.naviqore.raptor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public interface RaptorAlgorithm {

    /**
     * Routing the earliest arrival from departure stops to arrival. Given a set departure time.
     *
     * @param departureStops Map of stop ids and departure times
     * @param arrivalStops   Map of stop ids and walking times to final destination
     * @param config         Query configuration
     * @return a list of pareto-optimal earliest arrival connections sorted in ascending order by the number of route
     * legs (rounds)
     * @throws InvalidStopException     if departure or arrival stops are invalid
     * @throws InvalidTimeException     if departure or arrival times are invalid
     * @throws IllegalArgumentException for other argument related errors
     */
    List<Connection> routeEarliestArrival(Map<String, OffsetDateTime> departureStops, Map<String, Integer> arrivalStops,
                                          QueryConfig config);

    /**
     * Routing the latest departure from departure stops to arrival. Given a set arrival time.
     *
     * @param departureStops Map of stop ids and walking times from origin
     * @param arrivalStops   Map of stop ids and arrival times
     * @param config         Query configuration
     * @return a list of pareto-optimal latest departure connections sorted in ascending order by the number of route
     * legs (rounds)
     * @throws InvalidStopException     if departure or arrival stops are invalid
     * @throws InvalidTimeException     if departure or arrival times are invalid
     * @throws IllegalArgumentException for other argument related errors
     */
    List<Connection> routeLatestDeparture(Map<String, Integer> departureStops, Map<String, OffsetDateTime> arrivalStops,
                                          QueryConfig config);

    /**
     * Route isolines from source stops. Given a set of departure or arrival times, the method will return the earliest
     * arrival or latest departure connections for each stop.
     *
     * @param sourceStops is a map of stop ids and departure/arrival times
     * @param timeType    is the type of time to route for (arrival or departure)
     * @param config      is the query configuration
     * @return the earliest arrival (timeType=departure) or latest departure (timeType=arrival) connection for each stop
     * @throws InvalidStopException     if source stop is invalid
     * @throws InvalidTimeException     if source time is invalid
     * @throws IllegalArgumentException for other argument related errors
     */
    Map<String, Connection> routeIsolines(Map<String, OffsetDateTime> sourceStops, TimeType timeType,
                                          QueryConfig config);

    class InvalidStopException extends IllegalArgumentException {
        public InvalidStopException(String message) {
            super(message);
        }
    }

    class InvalidTimeException extends IllegalArgumentException {
        public InvalidTimeException(String message) {
            super(message);
        }
    }

}
