package org.naviqore.raptor.router;

/**
 * The stop times of the trips that run on a service day, in seconds relative to the day in UTC.
 * <p>
 * Only the trips that run on the day are in it: the running trips of a route lie one after the other in the order of
 * the route, each as its arrival and departure at every stop of the route, and the routes follow one another in the
 * order of their indices. So a search for the trip to take at a stop reads only trips that run, and a day takes the
 * size of its running trips, not that of the whole timetable.
 * <p>
 * The trips of a route are numbered on the day from {@code 0} to {@link #numberOfTrips(int)}, in the order of the
 * route, which is the order of their times at every stop of the route.
 *
 * @param stopTimes      the arrival and departure of each running trip at each stop of its route.
 * @param routeStopTimes for each route, where the stop times of its first running trip start in {@code stopTimes}.
 * @param routeTrips     for each route, the number of running trips of the routes before it, and at the end the number
 *                       of all: the running trips of route {@code r} are {@code routeTrips[r]} to
 *                       {@code routeTrips[r + 1]} of the day.
 * @param tripPositions  for each running trip of the day, its position among all trips of its route.
 * @param routeEarliest  for each route, its earliest stop time on the day, or {@code NO_TRIP} if none of its trips
 *                       runs.
 * @param routeLatest    for each route, its latest stop time on the day, or {@code NO_TRIP} if none of its trips runs.
 * @param earliest       the earliest stop time of the day, or {@code NO_TRIP} if no trip runs.
 * @param latest         the latest stop time of the day, or {@code NO_TRIP} if no trip runs.
 */
record DayStopTimes(int[] stopTimes, int[] routeStopTimes, int[] routeTrips, int[] tripPositions, int[] routeEarliest,
                    int[] routeLatest, int earliest, int latest) {

    /**
     * Gets the number of trips of a route that run on the day.
     */
    int numberOfTrips(int routeIdx) {
        return routeTrips[routeIdx + 1] - routeTrips[routeIdx];
    }

    /**
     * Gets the position among all trips of its route of a trip of the route running on the day, which gives its id.
     *
     * @param routeIdx the index of the route.
     * @param trip     the number of the trip among the trips of the route running on the day.
     */
    int tripPosition(int routeIdx, int trip) {
        return tripPositions[routeTrips[routeIdx] + trip];
    }
}
