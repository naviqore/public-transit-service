package ch.naviqore.service;

public enum WalkType {

    /**
     * A walk from a location to a stop, the first leg of a connection.
     */
    FIRST_MILE,

    /**
     * A walk from a stop to a location, the last leg of a connection.
     */
    LAST_MILE,

    /**
     * A walk between two locations, the only leg of a connection.
     */
    DIRECT
    
}
