package ch.naviqore.service;

import java.util.List;

/**
 * A connection in a public transit schedule, consisting of multiple public transit legs and walks.
 */
public interface Connection {

    List<PublicTransitLeg> getPublicTransitLegs();

    List<Walk> getWalks();

    /**
     * The total number of legs and walks in this connection.
     */
    int getSize();

}
