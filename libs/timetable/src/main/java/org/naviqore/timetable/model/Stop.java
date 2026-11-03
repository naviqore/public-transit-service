package org.naviqore.timetable.model;

import org.naviqore.utils.spatial.GeoCoordinate;
import org.naviqore.utils.spatial.Location;

import java.util.List;
import java.util.Optional;

/**
 * A stop of the timetable. Stops form a hierarchy: a stop may have a parent, e.g. the station of a platform, and child
 * stops.
 */
public interface Stop extends Location<GeoCoordinate> {

    String getId();

    String getName();

    Optional<? extends Stop> getParent();

    List<? extends Stop> getChildren();

    /**
     * Retrieves the transfers from this stop to other stops, or within this stop.
     *
     * @return the transfers departing from this stop
     */
    List<? extends Transfer> getTransfers();

}
