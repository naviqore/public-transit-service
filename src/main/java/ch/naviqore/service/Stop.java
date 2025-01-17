package ch.naviqore.service;

import ch.naviqore.utils.spatial.GeoCoordinate;
import ch.naviqore.utils.spatial.Location;

public interface Stop extends Location<GeoCoordinate> {

    String getId();

    String getName();

    GeoCoordinate getLocation();

}
