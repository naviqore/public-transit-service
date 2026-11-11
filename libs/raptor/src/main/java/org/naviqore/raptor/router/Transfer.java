package org.naviqore.raptor.router;

/**
 * A walk between two stops, of the duration of walking from the one to the other, by which a search reaches the target
 * stop: in the transfers of a stop the walk from the stop to the target stop, and in its reverse transfers, which a
 * search arriving follows backward, the walk from the target stop to the stop.
 */
record Transfer(int targetStopIdx, int duration) {
}
