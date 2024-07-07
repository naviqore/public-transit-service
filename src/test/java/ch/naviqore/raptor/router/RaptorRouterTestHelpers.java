package ch.naviqore.raptor.router;

import ch.naviqore.raptor.Connection;
import ch.naviqore.raptor.QueryConfig;
import ch.naviqore.raptor.RaptorAlgorithm;
import ch.naviqore.raptor.TimeType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RaptorRouterTestHelpers {

    static Map<String, Connection> getIsoLines(RaptorAlgorithm raptor, Map<String, LocalDateTime> sourceStops) {
        return getIsoLines(raptor, sourceStops, new QueryConfig());
    }

    static Map<String, Connection> getIsoLines(RaptorAlgorithm raptor, Map<String, LocalDateTime> sourceStops,
                                               QueryConfig config) {
        return raptor.routeIsolines(sourceStops, TimeType.DEPARTURE, config);
    }

    static List<Connection> routeEarliestArrival(RaptorAlgorithm raptor, String sourceStopId, String targetStopId,
                                                 LocalDateTime departureTime) {
        return routeEarliestArrival(raptor, createStopMap(sourceStopId, departureTime),
                createStopMap(targetStopId, 0));
    }

    static List<Connection> routeEarliestArrival(RaptorAlgorithm raptor, String sourceStopId, String targetStopId,
                                                 LocalDateTime departureTime, QueryConfig config) {
        return routeEarliestArrival(raptor, createStopMap(sourceStopId, departureTime),
                createStopMap(targetStopId, 0), config);
    }

    static Map<String, LocalDateTime> createStopMap(String stopId, LocalDateTime value) {
        return Map.of(stopId, value);
    }

    static Map<String, Integer> createStopMap(String stopId, int value) {
        return Map.of(stopId, value);
    }

    static List<Connection> routeEarliestArrival(RaptorAlgorithm raptor, Map<String, LocalDateTime> sourceStops,
                                                 Map<String, Integer> targetStopIds) {
        return routeEarliestArrival(raptor, sourceStops, targetStopIds, new QueryConfig());
    }

    static List<Connection> routeEarliestArrival(RaptorAlgorithm raptor, Map<String, LocalDateTime> sourceStops,
                                                 Map<String, Integer> targetStopIds, QueryConfig config) {
        return raptor.routeEarliestArrival(sourceStops, targetStopIds, config);
    }

    static List<Connection> routeLatestDeparture(RaptorAlgorithm raptor, String sourceStopId, String targetStopId,
                                                 LocalDateTime arrivalTime) {
        return routeLatestDeparture(raptor, createStopMap(sourceStopId, 0),
                createStopMap(targetStopId, arrivalTime));
    }

    static List<Connection> routeLatestDeparture(RaptorAlgorithm raptor, Map<String, Integer> sourceStops,
                                                 Map<String, LocalDateTime> targetStops) {
        return routeLatestDeparture(raptor, sourceStops, targetStops, new QueryConfig());
    }

    static List<Connection> routeLatestDeparture(RaptorAlgorithm raptor, Map<String, Integer> sourceStops,
                                                 Map<String, LocalDateTime> targetStops, QueryConfig config) {
        return raptor.routeLatestDeparture(sourceStops, targetStops, config);
    }


    static void assertEarliestArrivalConnection(Connection connection, String sourceStop, String targetStop,
                                                LocalDateTime requestedDepartureTime, int numSameStopTransfers,
                                                int numWalkTransfers, int numTrips, RaptorAlgorithm raptor) {
        assertEquals(sourceStop, connection.getFromStopId());
        assertEquals(targetStop, connection.getToStopId());

        assertFalse(connection.getDepartureTime().isBefore(requestedDepartureTime),
                "Departure time should be greater equal than searched for departure time");
        assertNotNull(connection.getArrivalTime(), "Arrival time should not be null");

        assertEquals(numSameStopTransfers, connection.getNumberOfSameStopTransfers(),
                "Number of same stop transfers should match");
        assertEquals(numWalkTransfers, connection.getWalkTransfers().size(), "Number of walk transfers should match");
        assertEquals(numSameStopTransfers + numWalkTransfers, connection.getNumberOfTotalTransfers(),
                "Number of transfers should match");

        assertEquals(numTrips, connection.getRouteLegs().size(), "Number of trips should match");
        assertReverseDirectionConnection(connection, TimeType.ARRIVAL, raptor);
    }

    static void assertLatestDepartureConnection(Connection connection, String sourceStop, String targetStop,
                                                LocalDateTime requestedArrivalTime, int numSameStopTransfers,
                                                int numWalkTransfers, int numTrips, RaptorAlgorithm raptor) {
        assertEquals(sourceStop, connection.getFromStopId());
        assertEquals(targetStop, connection.getToStopId());

        assertNotNull(connection.getDepartureTime(), "Departure time should not be null");
        assertFalse(connection.getArrivalTime().isAfter(requestedArrivalTime),
                "Arrival time should be smaller equal than searched for arrival time");

        assertEquals(numSameStopTransfers, connection.getNumberOfSameStopTransfers(),
                "Number of same station transfers should match");
        assertEquals(numWalkTransfers, connection.getWalkTransfers().size(), "Number of walk transfers should match");
        assertEquals(numSameStopTransfers + numWalkTransfers, connection.getNumberOfTotalTransfers(),
                "Number of transfers should match");

        assertEquals(numTrips, connection.getRouteLegs().size(), "Number of trips should match");
        assertReverseDirectionConnection(connection, TimeType.DEPARTURE, raptor);
    }

    static void assertReverseDirectionConnection(Connection connection, TimeType timeType, RaptorAlgorithm raptor) {
        List<Connection> connections;
        if (timeType == TimeType.DEPARTURE) {
            connections = routeEarliestArrival(raptor, connection.getFromStopId(),
                    connection.getToStopId(), connection.getDepartureTime());
        } else {
            connections = routeLatestDeparture(raptor, connection.getFromStopId(),
                    connection.getToStopId(), connection.getArrivalTime());
        }

        // find the connections with the same amount of rounds (this one should match)
        Connection matchingConnection = connections.stream()
                .filter(c -> c.getRouteLegs().size() == connection.getRouteLegs().size())
                .findFirst()
                .orElse(null);

        assertNotNull(matchingConnection, "Matching connection should be found");
        assertEquals(connection.getFromStopId(), matchingConnection.getFromStopId(), "From stop should match");
        assertEquals(connection.getToStopId(), matchingConnection.getToStopId(), "To stop should match");
        if (timeType == TimeType.DEPARTURE) {
            assertEquals(connection.getDepartureTime(), matchingConnection.getDepartureTime(),
                    "Departure time should match");

            // there is no guarantee that the arrival time is the same, but it should not be later (worse) than
            // the arrival time of the matching connection
            if (connection.getArrivalTime().isBefore(matchingConnection.getArrivalTime())) {
                return;
            }
        } else {
            assertEquals(connection.getArrivalTime(), matchingConnection.getArrivalTime(), "Arrival time should match");
            // there is no guarantee that the departure time is the same, but it should not be earlier (worse) than
            // the departure time of the matching connection
            if (connection.getDepartureTime().isBefore(matchingConnection.getArrivalTime())) {
                return;
            }
        }

        assertEquals(connection.getDepartureTime(), matchingConnection.getDepartureTime(),
                "Departure time should match");
        assertEquals(connection.getArrivalTime(), matchingConnection.getArrivalTime(), "Arrival time should match");
        assertEquals(connection.getNumberOfSameStopTransfers(), matchingConnection.getNumberOfSameStopTransfers(),
                "Number of same stop transfers should match");
        assertEquals(connection.getWalkTransfers().size(), matchingConnection.getWalkTransfers().size(),
                "Number of walk transfers should match");
        assertEquals(connection.getNumberOfTotalTransfers(), matchingConnection.getNumberOfTotalTransfers(),
                "Number of transfers should match");
        assertEquals(connection.getRouteLegs().size(), matchingConnection.getRouteLegs().size(),
                "Number of trips should match");
    }

    static void checkIfConnectionsAreParetoOptimal(List<Connection> connections) {
        Connection previousConnection = connections.getFirst();
        for (int i = 1; i < connections.size(); i++) {
            Connection currentConnection = connections.get(i);
            assertTrue(previousConnection.getDurationInSeconds() > currentConnection.getDurationInSeconds(),
                    "Previous connection should be slower than current connection");
            assertTrue(previousConnection.getRouteLegs().size() < currentConnection.getRouteLegs().size(),
                    "Previous connection should have fewer route legs than current connection");
            previousConnection = currentConnection;
        }
    }

    static void assertIsoLines(Map<String, Connection> isoLines, String startStop, LocalDateTime departureTime,
                               int expectedIsoLines) {
        assertEquals(expectedIsoLines, isoLines.size());
        assertFalse(isoLines.containsKey(startStop), "Source stop should not be in iso lines");
        for (Map.Entry<String, Connection> entry : isoLines.entrySet()) {
            assertFalse(entry.getValue().getDepartureTime().isBefore(departureTime),
                    "Departure time should be greater than or equal to departure time");
            assertFalse(entry.getValue().getArrivalTime().isBefore(departureTime),
                    "Arrival time should be greater than or equal to departure time");
            assertNotNull(entry.getValue().getArrivalTime(), "Arrival time must be set.");
            assertEquals(startStop, entry.getValue().getFromStopId(), "From stop should be source stop");
            assertEquals(entry.getKey(), entry.getValue().getToStopId(), "To stop should be key of map entry");
        }
    }
}
