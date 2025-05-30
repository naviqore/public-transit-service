package org.naviqore.raptor.router;

/**
 * Internal interface to provide access to data structures required for the RAPTOR routing.
 */
interface RaptorData {

    Lookup getLookup();

    StopContext getStopContext();

    RouteTraversal getRouteTraversal();

    StopTimeProvider getStopTimeProvider();

}
