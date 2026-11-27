package org.naviqore.raptor.router;

import org.naviqore.raptor.TimeType;

/**
 * The stops of the router, their routes, and the walks between them: in the transfers the walks from each stop, and in
 * the reverse transfers the walks to each stop, each stop's in a range of their own (see {@link Stop}).
 */
record StopContext(Transfer[] transfers, Transfer[] reverseTransfers, Stop[] stops, int[] stopRoutes) {

    /**
     * The walks a search follows: departing, the walks from a stop to another, and arriving, routing backward, the
     * walks to a stop from another, so that each walk takes as long as walking it in its own direction.
     */
    Transfer[] transfers(TimeType timeType) {
        return timeType == TimeType.DEPARTURE ? transfers : reverseTransfers;
    }
}
