package ch.naviqore.service;

/**
 * Represents a leg of a connection, including its order, distance, and duration.
 */
public interface Leg {

    /**
     * The position of this leg in the overall connection.
     */
    int getOrder();

    int getDistance();

    int getDuration();

}
