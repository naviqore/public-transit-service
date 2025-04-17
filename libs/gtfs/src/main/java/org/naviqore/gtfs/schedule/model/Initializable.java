package org.naviqore.gtfs.schedule.model;

/**
 * Initializable class
 * <p>
 * This internal interface should be implemented by classes that require initialization steps to be executed before they
 * are considered fully ready and operational. This interface is designed to enforce a consistent initialization pattern
 * across different components of the GTFS (General Transit Feed Specification) schedule model.
 *
 * @author munterfi
 */
interface Initializable {

    /**
     * Initializes the implementing object.
     */
    void initialize();

}
