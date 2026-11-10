package org.naviqore.raptor;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * The queries of RAPTOR, named as in the RAPTOR paper (Delling, Pajor and Werneck, Round-Based Public Transit Routing,
 * 2015): a query at a time, and a range query over a time window (rRAPTOR); each for connections between stops, and for
 * isolines, from stops to all others. See {@code docs/routing.md} for the definitions and the options.
 * <p>
 * The router routes from stop to stop. The way to and from the stops, the first and the last mile, is the caller's: it
 * gives the time at each stop the search starts from, and the walk from each stop the search ends at to the target.
 * <p>
 * The time type sets the direction of the search. A departure query searches forward in time from the stops where
 * connections depart. An arrival query searches backward in time from the stops where connections arrive: its source
 * stops are those, at the latest time to arrive at each, and its target stops are where connections depart, with the
 * walk from the origin to each.
 */
public interface RaptorAlgorithm {

    /**
     * RAPTOR: the connections from the source stops at their times to the target stops that no other connection beats,
     * by its time at the target and its number of trips. For a departure query this is the earliest arrival for each
     * number of trips, for an arrival query the latest departure; a connection with more trips is only included if it
     * is better in time. A connection takes the first trip it can catch, so of equally early arrivals it may leave
     * earlier than necessary: an arrival query from its arrival, with as many trips at most, finds the latest departure
     * for it.
     *
     * @param sourceStops the stops the search starts from, and the time at each: the departure for a departure query,
     *                    the latest arrival for an arrival query
     * @param targetStops the stops the search ends at, and the walk duration in seconds from each to the target, or for
     *                    an arrival query from the origin to each
     * @param timeType    the direction of the search: forward from departures, or backward from arrivals
     * @param config      the query configuration
     * @return the connections, by their number of trips, the fewest first
     * @throws InvalidStopException     if source or target stops are invalid
     * @throws InvalidTimeException     if the source times are invalid
     * @throws IllegalArgumentException for other argument related errors
     */
    List<Connection> connectionQuery(Map<String, OffsetDateTime> sourceStops, Map<String, Integer> targetStops,
                                     TimeType timeType, QueryConfig config);

    /**
     * rRAPTOR: the connections that leave the source stops in a time window starting at their times (that reach them in
     * a window ending at their times, for an arrival query) and that no other connection beats: no other leaves no
     * earlier, reaches the target no later and has no more trips, and is better in one of them. This is the range query
     * of the RAPTOR paper, the window its range. The window applies to all source stops alike, so it is a window at the
     * origin or destination they were reached from.
     * <p>
     * A connection beaten by one leaving after the window (arriving before it) is not included, as at the time it
     * leaves a query finds the other: so the windows that follow one another give the connections of a longer one, and
     * a window shorter than the headway may hold no connection at all. A connection on foot alone can leave at any
     * time; it is included once, leaving at the start of the window (arriving at its end).
     *
     * @param sourceStops the stops the search starts from, and the time at each the window starts at, or for an arrival
     *                    query ends at
     * @param targetStops the stops the search ends at, and the walk duration in seconds from each to the target, or for
     *                    an arrival query from the origin to each
     * @param timeType    the direction of the search: forward from departures, or backward from arrivals
     * @param timeWindow  the duration of the time window, at least a second
     * @param config      the query configuration
     * @return the connections, by their departure, the earliest first, and then by their arrival
     * @throws InvalidStopException     if source or target stops are invalid
     * @throws InvalidTimeException     if the source times are invalid
     * @throws IllegalArgumentException if the time window is shorter than a second, and for other argument related
     *                                  errors
     */
    List<Connection> connectionRangeQuery(Map<String, OffsetDateTime> sourceStops, Map<String, Integer> targetStops,
                                          TimeType timeType, Duration timeWindow, QueryConfig config);

    /**
     * RAPTOR from the source stops to all stops: the connection of the earliest arrival at each stop (latest departure,
     * for an arrival query). Of equally early arrivals, the one with the fewest trips is taken.
     *
     * @param sourceStops the stops the search starts from, and the time at each: the departure for a departure query,
     *                    the latest arrival for an arrival query
     * @param timeType    the direction of the search: forward from departures, or backward from arrivals
     * @param config      the query configuration
     * @return the connection to each stop reached, by stop id; the source stops are not included unless reached from
     * another source stop
     * @throws InvalidStopException     if the source stops are invalid
     * @throws InvalidTimeException     if the source times are invalid
     * @throws IllegalArgumentException for other argument related errors
     */
    Map<String, Connection> isolineQuery(Map<String, OffsetDateTime> sourceStops, TimeType timeType,
                                         QueryConfig config);

    /**
     * rRAPTOR from the source stops to all stops: for each stop, the connection with the shortest travel time among
     * those leaving the source stops in a time window starting at their times (reaching them in a window ending at
     * their times, for an arrival query). Of equally short travel times, the connection leaving earliest (arriving
     * latest) is taken. The travel time is measured from leaving the source stops as late as the connection allows, so
     * that waiting before its first trip does not count.
     *
     * @param sourceStops the stops the search starts from, and the time at each the window starts at, or for an arrival
     *                    query ends at
     * @param timeType    the direction of the search: forward from departures, or backward from arrivals
     * @param timeWindow  the duration of the time window, at least a second
     * @param config      the query configuration
     * @return the connection to each stop reached, by stop id; the source stops are not included unless reached from
     * another source stop
     * @throws InvalidStopException     if the source stops are invalid
     * @throws InvalidTimeException     if the source times are invalid
     * @throws IllegalArgumentException if the time window is shorter than a second, and for other argument related
     *                                  errors
     */
    Map<String, Connection> isolineRangeQuery(Map<String, OffsetDateTime> sourceStops, TimeType timeType,
                                              Duration timeWindow, QueryConfig config);

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
