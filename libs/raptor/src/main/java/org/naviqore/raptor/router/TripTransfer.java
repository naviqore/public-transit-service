package org.naviqore.raptor.router;

import org.jspecify.annotations.Nullable;

/**
 * A transfer between particular routes or trips: from the trips arriving at one stop to the trips departing from
 * another stop, or from the same one.
 * <p>
 * On either side, a trip transfer names a trip, a route, or neither, which stands for every route at its stop; a trip
 * may come with its route, which it must belong to. Of the trip transfers that apply to a pair of an arriving and a
 * departing trip, the most specific one applies, as GTFS ranks them: the one naming both trips, then one naming a trip
 * and a route, one naming a trip, both routes, and one naming a route. Where none applies, the walks and the same stop
 * transfer times of the stops do (see {@link RaptorRouterBuilder#addTransfer(String, String, int)} and
 * {@link RaptorRouterBuilder#refuseTransfers(String, String)}).
 *
 * @param fromStopId  the stop the arriving trip is left at.
 * @param fromRouteId the route of the arriving trip, or null for every route.
 * @param fromTripId  the arriving trip, or null for every trip of the route.
 * @param toStopId    the stop the departing trip is boarded at.
 * @param toRouteId   the route of the departing trip, or null for every route.
 * @param toTripId    the departing trip, or null for every trip of the route.
 * @param type        whether and how the transfer is possible.
 * @param duration    the time the transfer takes, in seconds, for the type {@link Type#MINIMUM_TIME}.
 */
public record TripTransfer(String fromStopId, @Nullable String fromRouteId, @Nullable String fromTripId,
                           String toStopId, @Nullable String toRouteId, @Nullable String toTripId, Type type,
                           int duration) {

    public TripTransfer {
        if (duration < 0) {
            throw new IllegalArgumentException("Duration must not be negative.");
        }
    }

    /**
     * Whether and how a trip transfer is possible.
     */
    public enum Type {

        /**
         * Possible once its duration has passed after the arrival, as is a walk or a change at a stop: a walk takes the
         * duration of the transfer instead of its own. The minimum transfer duration of a query applies.
         */
        MINIMUM_TIME,
        /**
         * Possible in the time of the stops: the same stop transfer time, or the walk between them, as if the stops did
         * not refuse transfers. Not possible between two stops without a walk.
         */
        STOP_TIME,
        /**
         * The rider stays in the vehicle, which continues as the departing trip: possible in no time, without the
         * minimum transfer duration of a query.
         */
        IN_SEAT,
        /**
         * Not possible.
         */
        NOT_POSSIBLE

    }

}
