package ch.naviqore.raptor.simple;

/**
 * Internal interface to provide access to data structures required for the raptor routing.
 */
interface RaptorData {

    Lookup getLookup();

    StopContext getStopContext();

    RouteTraversal getRouteTraversal();

}
