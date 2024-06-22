package ch.naviqore.raptor;

import ch.naviqore.raptor.impl.RaptorBuilder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface RaptorAlgorithm {

    static RaptorBuilder builder(int sameStopTransferTime) {
        return new RaptorBuilder(sameStopTransferTime);
    }

    /**
     * Routing the earliest arrival from departure stops to arrival. Given a set departure time.
     *
     * @param departureStops Map of stop ids and departure times
     * @param arrivalStops   Map of stop ids and walking times to final destination
     * @param config         Query configuration
     * @return a list of pareto-optimal earliest arrival connections
     */
    List<Connection> routeEarliestArrival(Map<String, LocalDateTime> departureStops, Map<String, Integer> arrivalStops,
                                          QueryConfig config);

    /**
     * Routing the latest departure from departure stops to arrival. Given a set arrival time.
     *
     * @param departureStops Map of stop ids and walking times from origin
     * @param arrivalStops   Map of stop ids and arrival times
     * @param config         Query configuration
     * @return a list of pareto-optimal latest departure connections
     */
    List<Connection> routeLatestDeparture(Map<String, Integer> departureStops, Map<String, LocalDateTime> arrivalStops,
                                          QueryConfig config);

    /**
     * Route isolines from source stops. Given a set of departure or arrival times, the method will return the earliest
     * arrival or latest departure connections for each stop.
     *
     * @param sourceStops is a map of stop ids and departure/arrival times
     * @param timeType    is the type of time to route for (arrival or departure)
     * @param config      is the query configuration
     * @return a pareto-optimal connection for each stop
     */
    Map<String, Connection> routeIsolines(Map<String, LocalDateTime> sourceStops, TimeType timeType,
                                          QueryConfig config);

}
