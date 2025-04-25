package org.naviqore.raptor.router;

record Stop(String id, int stopRouteIdx, int numberOfRoutes, int sameStopTransferTime, int transferIdx,
            int numberOfTransfers) {
}
