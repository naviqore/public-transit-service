package ch.naviqore.service;

public enum LegType {

    /**
     * A public transit leg, which means traveling using a transit trip belonging to a transit route.
     */
    PUBLIC_TRANSIT,

    /**
     * A transfer, which means a walk between two transit stations.
     */
    TRANSFER,

    /**
     * A walk from or to a stop, which means the first or the last mile.
     */
    WALK

}
