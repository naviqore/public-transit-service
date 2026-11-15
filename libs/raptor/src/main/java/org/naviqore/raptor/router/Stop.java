package org.naviqore.raptor.router;

import org.naviqore.raptor.TimeType;

/**
 * A stop of the router: its routes, by the index of the first and their number in the stop routes, its same stop
 * transfer time, whether it refuses changing from one trip to another at it, and the walks between it and other stops,
 * by the index of the first and their number: the walks from it in the transfers, and the walks to it in the reverse
 * transfers (see {@link StopContext}).
 * <p>
 * A slot of the trip transfers is a stop too, with the id, routes, transfer times and walks of its stop (see
 * {@link TripTransfers}).
 */
record Stop(String id, int stopRouteIdx, int numberOfRoutes, int sameStopTransferTime, boolean refusesTransfers,
            int transferIdx, int numberOfTransfers, int reverseTransferIdx, int numberOfReverseTransfers) {

    /**
     * The index of the first walk a search follows from the stop, in the transfers of its direction (see
     * {@link StopContext#transfers(TimeType)}).
     */
    int transferIdx(TimeType timeType) {
        return timeType == TimeType.DEPARTURE ? transferIdx : reverseTransferIdx;
    }

    /**
     * The number of walks a search follows from the stop, in the transfers of its direction.
     */
    int numberOfTransfers(TimeType timeType) {
        return timeType == TimeType.DEPARTURE ? numberOfTransfers : numberOfReverseTransfers;
    }
}
